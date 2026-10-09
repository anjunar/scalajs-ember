package ember.editor.json

import ember.editor.core.*

import scala.scalajs.js

/** Ein JSON-Wert als geschlossenes ADT.
  *
  * §19.2 verlangt genau das: "`NodeJsonCodec[N]` verarbeitet ein geschlossenes JSON-Value-ADT;
  * `js.JSON.parse/stringify` darf ausschliesslich an der Plattformgrenze stehen, gefolgt von
  * vollstaendiger Validierung."
  *
  * Der Unterschied zu `js.Any` ist nicht kosmetisch. Ein Codec, der auf `js.Dynamic` arbeitet,
  * prueft nichts, was er nicht ausdruecklich prueft -- und was er vergisst, faellt erst auf, wenn
  * ein fremder Payload es ausnutzt. Hier ist die Pruefung nicht Disziplin, sondern Typsystem: wer
  * einen [[JsonValue]] in der Hand hat, hat einen konvertierten Wert, dessen Tiefe und Groesse
  * bereits gegen die [[DecodeLimits]] gehalten wurden.
  *
  * Geschlossen ist es aus demselben Grund wie [[PathSegment]] im Kern: die Struktur eines
  * Wire-Werts ist eine Eigenschaft des Formats, keine Erweiterung.
  */
enum JsonValue:
  case Null
  case Bool(value: Boolean)
  case Num(value: Double)
  case Str(value: String)
  case Arr(items: Vector[JsonValue])
  case Obj(fields: Vector[(String, JsonValue)])

object JsonValue:

  def num(value: Int): JsonValue = Num(value.toDouble)

  def obj(fields: (String, JsonValue)*): Obj = Obj(fields.toVector)

  def arr(items: IterableOnce[JsonValue]): Arr = Arr(items.iterator.toVector)

  /** Der Name der Art, fuer Typdiagnosen. */
  def kindOf(value: JsonValue): String = value match
    case Null    => "null"
    case _: Bool => "boolean"
    case _: Num  => "number"
    case _: Str  => "string"
    case _: Arr  => "array"
    case _: Obj  => "object"

  extension (value: JsonValue)

    def asObject(at: DiagnosticPath): Either[DecodeError, Obj] = value match
      case obj: Obj => Right(obj)
      case other    => Left(DecodeError.TypeMismatch("object", kindOf(other), at))

    def asArray(at: DiagnosticPath): Either[DecodeError, Vector[JsonValue]] = value match
      case Arr(items) => Right(items)
      case other      => Left(DecodeError.TypeMismatch("array", kindOf(other), at))

    def asString(at: DiagnosticPath): Either[DecodeError, String] = value match
      case Str(text) => Right(text)
      case other     => Left(DecodeError.TypeMismatch("string", kindOf(other), at))

    /** Eine ganze Zahl im `Int`-Bereich.
      *
      * JSON kennt nur `number`, und `js.JSON.parse` liefert `Double`. Eine Zahl mit Nachkommaanteil
      * ist deshalb keine ganze Zahl mit Rundungsspielraum, sondern ein Formatfehler -- §19.2
      * verlangt die Pruefung von "Zahlenbereichen" ausdruecklich.
      */
    def asInt(at: DiagnosticPath): Either[DecodeError, Int] = value match
      case Num(number) if number.isWhole && number >= Int.MinValue && number <= Int.MaxValue =>
        Right(number.toInt)
      case Num(number) =>
        Left(DecodeError.InvalidValue(s"`$number` ist keine ganze Zahl im Int-Bereich.", at))
      case other => Left(DecodeError.TypeMismatch("number", kindOf(other), at))

  extension (obj: JsonValue.Obj)

    /** Der Wert zu einem Feldnamen, oder `None`.
      *
      * Bei mehrfach vorkommendem Namen gewinnt der '''letzte''' Eintrag -- dieselbe Regel, die
      * `js.JSON.parse` beim Zusammenfassen anwendet. Siehe [[JsonText.parse]] zu der Frage, was
      * dieses Modul deshalb ueberhaupt pruefen kann.
      */
    def get(name: String): Option[JsonValue] =
      obj.fields.reverseIterator.collectFirst { case (key, value) if key == name => value }

    def required(name: String, at: DiagnosticPath): Either[DecodeError, JsonValue] =
      get(name).toRight(DecodeError.MissingField(name, at))

    def string(name: String, at: DiagnosticPath): Either[DecodeError, String] =
      required(name, at).flatMap(_.asString(at.field(name)))

    def int(name: String, at: DiagnosticPath): Either[DecodeError, Int] =
      required(name, at).flatMap(_.asInt(at.field(name)))

    def array(name: String, at: DiagnosticPath): Either[DecodeError, Vector[JsonValue]] =
      required(name, at).flatMap(_.asArray(at.field(name)))

    def objectAt(name: String, at: DiagnosticPath): Either[DecodeError, JsonValue.Obj] =
      required(name, at).flatMap(_.asObject(at.field(name)))

    /** Ein optionales Textfeld. `null` gilt als nicht gesetzt. */
    def optionalString(name: String, at: DiagnosticPath): Either[DecodeError, Option[String]] =
      get(name) match
        case None | Some(JsonValue.Null) => Right(None)
        case Some(value)                 => value.asString(at.field(name)).map(Some(_))
