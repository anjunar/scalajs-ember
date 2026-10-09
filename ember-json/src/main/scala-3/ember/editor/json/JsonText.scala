package ember.editor.json

import ember.editor.core.*

import scala.scalajs.js

/** Die Plattformgrenze: Text zu [[JsonValue]] und zurueck.
  *
  * Hier und nur hier steht `js.JSON`. Alles darueber arbeitet auf dem ADT.
  */
object JsonText:

  /** Liest Text und konvertiert ihn vollstaendig ins ADT.
    *
    * ==Was hier nicht geprueft werden kann==
    *
    * `js.JSON.parse` fasst doppelte Objektschluessel zusammen, bevor dieses Modul den Wert zu sehen
    * bekommt: aus `{"a":1,"a":2}` wird `{"a":2}`, und die erste Belegung ist dann spurlos
    * verschwunden. Das ist keine Nachlaessigkeit, sondern die Semantik der Plattformfunktion -- ein
    * eigener Parser koennte es melden, waere aber ein zweiter, schlechter getesteter JSON-Parser
    * fuer eine Diagnose, die kein Datenverlust ist: der letzte Wert gewinnt, deterministisch und
    * dokumentiert.
    *
    * '''Doppelte Node-IDs bleiben davon unberuehrt.''' Sie sind der Fall, der wirklich zaehlt, und
    * deshalb steht die Knotenliste im Format als Array und nicht als Objekt, das nach ID
    * schluesselt -- so bleibt eine doppelte ID sichtbar und wird zum Fehler
    * ([[DecodeError.DuplicateNodeId]]).
    */
  def parse(text: String, limits: DecodeLimits): Either[DecodeError, JsonValue] =
    if text.length > limits.maxSourceChars then
      Left(
        DecodeError
          .LimitExceeded("maxSourceChars", limits.maxSourceChars, text.length, DiagnosticPath.Root)
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
    val built                        = Vector.newBuilder[B]
    val iterator                     = items.iterator
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
    *      grundsaetzlich, und U+2028/U+2029 dazu, die in JavaScript-Quelltext Zeilentrenner sind.
    *      Das Ergebnis bleibt gewoehnliches JSON; jeder Parser liest dieselben Zeichen.
    */
  def render(value: JsonValue): String =
    val out = new StringBuilder
    write(value, out)
    out.result()

  /** Serialisiert eingerueckt, fuer Menschen.
    *
    * '''Nicht die Wire-Form.''' [[render]] bleibt die: kompakt, weil jedes eingefuegte Leerzeichen
    * den byteweisen Vergleich zweier Staende erschwert. Diese Fassung ist fuer Diagnoseausgaben und
    * Demos -- gleiche Zeichenmaskierung, gleiche Feldreihenfolge, nur lesbar umbrochen.
    */
  def renderPretty(value: JsonValue, indent: String = "  "): String =
    val out = new StringBuilder
    writePretty(value, out, indent, 0)
    out.result()

  private def writePretty(
      value: JsonValue,
      out: StringBuilder,
      indent: String,
      level: Int
  ): Unit =
    def newline(at: Int): Unit =
      out += '\n'
      var step = 0
      while step < at do
        out ++= indent
        step += 1

    value match
      case JsonValue.Arr(items) if items.nonEmpty =>
        out += '['
        items.zipWithIndex.foreach { (item, index) =>
          if index > 0 then out += ','
          newline(level + 1)
          writePretty(item, out, indent, level + 1)
        }
        newline(level)
        out += ']'
      case JsonValue.Obj(fields) if fields.nonEmpty =>
        out += '{'
        fields.zipWithIndex.foreach { case ((key, item), index) =>
          if index > 0 then out += ','
          newline(level + 1)
          quote(key, out)
          out ++= ": "
          writePretty(item, out, indent, level + 1)
        }
        newline(level)
        out += '}'
      case other => write(other, out)

  private def write(value: JsonValue, out: StringBuilder): Unit = value match
    case JsonValue.Null       => out ++= "null"
    case JsonValue.Bool(flag) => out ++= (if flag then "true" else "false")
    case JsonValue.Num(value) => out ++= renderNumber(value)
    case JsonValue.Str(text)  => quote(text, out)
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
      case '"'                          => out ++= "\\\""
      case '\\'                         => out ++= "\\\\"
      case '\n'                         => out ++= "\\n"
      case '\r'                         => out ++= "\\r"
      case '\t'                         => out ++= "\\t"
      case '<'                          => out ++= "\\u003c"
      case '>'                          => out ++= "\\u003e"
      case '&'                          => out ++= "\\u0026"
      case ' '                          => out ++= "\\u2028"
      case ' '                          => out ++= "\\u2029"
      case character if character < ' ' =>
        out ++= "\\u%04x".format(character.toInt)
      case character => out += character
    }
    out += '"'
