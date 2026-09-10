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
  * prueft nichts, was er nicht ausdruecklich prueft -- und was er vergisst, faellt erst auf,
  * wenn ein fremder Payload es ausnutzt. Hier ist die Pruefung nicht Disziplin, sondern
  * Typsystem: wer einen [[JsonValue]] in der Hand hat, hat einen konvertierten Wert, dessen
  * Tiefe und Groesse bereits gegen die [[DecodeLimits]] gehalten wurden.
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
    case Null   => "null"
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
      * JSON kennt nur `number`, und `js.JSON.parse` liefert `Double`. Eine Zahl mit
      * Nachkommaanteil ist deshalb keine ganze Zahl mit Rundungsspielraum, sondern ein
      * Formatfehler -- §19.2 verlangt die Pruefung von "Zahlenbereichen" ausdruecklich.
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

/** Die Plattformgrenze: Text zu [[JsonValue]] und zurueck.
  *
  * Hier und nur hier steht `js.JSON`. Alles darueber arbeitet auf dem ADT.
  */
object JsonText:

  /** Liest Text und konvertiert ihn vollstaendig ins ADT.
    *
    * ==Was hier nicht geprueft werden kann==
    *
    * `js.JSON.parse` fasst doppelte Objektschluessel zusammen, bevor dieses Modul den Wert zu
    * sehen bekommt: aus `{"a":1,"a":2}` wird `{"a":2}`, und die erste Belegung ist dann
    * spurlos verschwunden. Das ist keine Nachlaessigkeit, sondern die Semantik der
    * Plattformfunktion -- ein eigener Parser koennte es melden, waere aber ein zweiter,
    * schlechter getesteter JSON-Parser fuer eine Diagnose, die kein Datenverlust ist: der
    * letzte Wert gewinnt, deterministisch und dokumentiert.
    *
    * '''Doppelte Node-IDs bleiben davon unberuehrt.''' Sie sind der Fall, der wirklich zaehlt,
    * und deshalb steht die Knotenliste im Format als Array und nicht als Objekt, das nach ID
    * schluesselt -- so bleibt eine doppelte ID sichtbar und wird zum Fehler
    * ([[DecodeError.DuplicateNodeId]]).
    */
  def parse(text: String, limits: DecodeLimits): Either[DecodeError, JsonValue] =
    if text.length > limits.maxSourceChars then
      Left(
        DecodeError.LimitExceeded("maxSourceChars", limits.maxSourceChars, text.length,
          DiagnosticPath.Root)
      )
    else
      val raw =
        try Right(js.JSON.parse(text))
        catch case error: Throwable => Left(DecodeError.MalformedJson(describe(error)))
      raw.flatMap(convert(_, 0, limits, DiagnosticPath.Root))

  private def describe(error: Throwable): String =
    Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)

  private def convert(
      raw: js.Any,
      depth: Int,
      limits: DecodeLimits,
      at: DiagnosticPath
  ): Either[DecodeError, JsonValue] =
    if depth > limits.maxJsonDepth then
      Left(DecodeError.LimitExceeded("maxJsonDepth", limits.maxJsonDepth, depth, at))
    else if raw == null then Right(JsonValue.Null)
    else
      js.typeOf(raw) match
        case "boolean" => Right(JsonValue.Bool(raw.asInstanceOf[Boolean]))
        case "string"  => Right(JsonValue.Str(raw.asInstanceOf[String]))
        case "number"  =>
          val number = raw.asInstanceOf[Double]
          // JSON kennt weder NaN noch Unendlich. `js.JSON.parse` erzeugt sie nicht -- die
          // Pruefung steht hier, weil dieselbe Funktion spaeter auch fremde js.Any-Werte
          // konvertieren koennte und ein NaN im Dokument nicht mehr serialisierbar waere.
          if number.isNaN || number.isInfinite then
            Left(DecodeError.InvalidValue(s"`$number` ist in JSON nicht darstellbar.", at))
          else Right(JsonValue.Num(number))
        case "object" =>
          if js.Array.isArray(raw) then
            convertArray(raw.asInstanceOf[js.Array[js.Any]], depth, limits, at)
          else convertObject(raw.asInstanceOf[js.Object], depth, limits, at)
        case other =>
          Left(DecodeError.InvalidValue(s"Unerwarteter JavaScript-Typ `$other`.", at))

  private def convertArray(
      raw: js.Array[js.Any],
      depth: Int,
      limits: DecodeLimits,
      at: DiagnosticPath
  ): Either[DecodeError, JsonValue] =
    if raw.length > limits.maxArrayLength then
      Left(DecodeError.LimitExceeded("maxArrayLength", limits.maxArrayLength, raw.length, at))
    else
      collect(raw.indices) { index =>
        convert(raw(index), depth + 1, limits, at.index(index))
      }.map(JsonValue.Arr(_))

  private def convertObject(
      raw: js.Object,
      depth: Int,
      limits: DecodeLimits,
      at: DiagnosticPath
  ): Either[DecodeError, JsonValue] =
    val keys = js.Object.keys(raw)
    if keys.length > limits.maxObjectFields then
      Left(DecodeError.LimitExceeded("maxObjectFields", limits.maxObjectFields, keys.length, at))
    else
      val dynamic = raw.asInstanceOf[js.Dynamic]
      collect(keys.toVector) { key =>
        convert(dynamic.selectDynamic(key), depth + 1, limits, at.field(key))
          .map(key -> _)
      }.map(JsonValue.Obj(_))

  /** Der erste Fehler bricht ab. §19.2: "bevor eine Session oder View entsteht" -- eine
    * Teilkonvertierung waere schon der halbgueltige Zustand, den die Abnahme ausschliesst.
    */
  private def collect[A, B](items: Iterable[A])(
      step: A => Either[DecodeError, B]
  ): Either[DecodeError, Vector[B]] =
    val built = Vector.newBuilder[B]
    val iterator = items.iterator
    var failure: Option[DecodeError] = None
    while iterator.hasNext && failure.isEmpty do
      step(iterator.next()) match
        case Right(value) => built += value
        case Left(error)  => failure = Some(error)
    failure.toLeft(built.result())

  /** Serialisiert.
    *
    * '''Eigener Serialisierer, nicht `js.JSON.stringify`.''' Zwei Gruende, beide praktisch:
    *
    *   1. '''Feldreihenfolge.''' Die Ausgabe muss bei gleichem Dokument byteweise gleich sein,
    *      sonst sind Roundtrip-Fixtures wertlos. Hier bestimmt sie der Aufbau des
    *      [[JsonValue.Obj]], nicht die Einfuegereihenfolge eines JavaScript-Objekts.
    *   1. '''Einbettbarkeit.''' Der Payload landet spaeter in einem `<script>`-Tag (§16). Ein
    *      `</script` im Text wuerde ihn dort beenden -- deshalb entkommen `<`, `>` und `&`
    *      grundsaetzlich, und U+2028/U+2029 dazu, die in JavaScript-Quelltext Zeilentrenner
    *      sind. Das Ergebnis bleibt gewoehnliches JSON; jeder Parser liest dieselben Zeichen.
    */
  def render(value: JsonValue): String =
    val out = new StringBuilder
    write(value, out)
    out.result()

  private def write(value: JsonValue, out: StringBuilder): Unit = value match
    case JsonValue.Null       => out ++= "null"
    case JsonValue.Bool(flag) => out ++= (if flag then "true" else "false")
    case JsonValue.Num(value)  => out ++= renderNumber(value)
    case JsonValue.Str(text)   => quote(text, out)
    case JsonValue.Arr(items) =>
      out += '['
      var first = true
      items.foreach { item =>
        if !first then out += ','
        first = false
        write(item, out)
      }
      out += ']'
    case JsonValue.Obj(fields) =>
      out += '{'
      var first = true
      fields.foreach { (key, item) =>
        if !first then out += ','
        first = false
        quote(key, out)
        out += ':'
        write(item, out)
      }
      out += '}'

  /** Ganze Zahlen ohne `.0`. Ein `1.0` waere gueltiges JSON, aber ein Roundtrip-Unterschied. */
  private def renderNumber(value: Double): String =
    if value.isWhole && value.abs <= 9007199254740991d then value.toLong.toString
    else value.toString

  private def quote(text: String, out: StringBuilder): Unit =
    out += '"'
    text.foreach {
      case '"'  => out ++= "\\\""
      case '\\' => out ++= "\\\\"
      case '\n' => out ++= "\\n"
      case '\r' => out ++= "\\r"
      case '\t' => out ++= "\\t"
      case '<'  => out ++= "\\u003c"
      case '>'  => out ++= "\\u003e"
      case '&'  => out ++= "\\u0026"
      case ' ' => out ++= "\\u2028"
      case ' ' => out ++= "\\u2029"
      case character if character < ' ' =>
        out ++= "\\u%04x".format(character.toInt)
      case character => out += character
    }
    out += '"'
