package ember.editor.json

import ember.editor.core.*

/** Ein Knoten, dessen Art das Schema nicht kennt, unter [[UnknownNodePolicy.Preserve]] erhalten.
  *
  * §19.2: "Ein expliziter Preservation-Modus kann den begrenzten JSON-Wert als `UnsupportedNode`
  * mit sicherem Textfallback erhalten; er fuehrt keinen Code oder HTML aus. Roundtrip erhaelt dann
  * den Payload, Editing der unbekannten Struktur bleibt gesperrt."
  *
  * ==Warum ein Container==
  *
  * Weil seine Kinder sonst verschwaenden. Ein unbekannter Knoten kann durchaus bekannte Knoten
  * enthalten -- eine Tabelle mit ganz gewoehnlichen Absaetzen darin. Wuerde dieser Typ die
  * Kindliste nicht tragen, waeren die Absaetze nach dem Dekodieren unerreichbar, und der
  * `DocumentValidator` lehnte das Dokument mit `UnreachableNode` ab. Der Preservation-Modus haette
  * dann genau das zerstoert, wozu es ihn gibt.
  *
  * ==Was hier nicht passiert==
  *
  * [[payload]] wird nicht interpretiert. Er ist ein [[JsonValue.Obj]] -- ein bereits konvertierter,
  * gegen die Limits gehaltener Wert, kein `js.Any`, keine deserialisierte Klasse, kein HTML.
  * [[fallbackText]] ist reiner Text und wird von einem Renderer als Text dargestellt, nie als
  * Markup.
  *
  * @param typeId
  *   die '''urspruengliche''' Wire-ID. Sie wird beim Kodieren wieder geschrieben -- ein
  *   Roundtrip durch einen Editor ohne das betreffende Feature-Modul aendert am Payload nichts.
  */
final case class UnsupportedNode(
    id: NodeId,
    typeId: NodeTypeId,
    codecVersion: Int,
    payload: JsonValue.Obj,
    children: Vector[NodeId],
    fallbackText: String
) extends ElementNode

object UnsupportedNode extends ElementNodeType[UnsupportedNode]:

  /** Der Deskriptor traegt eine eigene Wire-ID, geschrieben wird aber die des Knotens.
    *
    * Diese hier steht nie in einem Dokument. Sie existiert, weil ein [[NodeType]] einen Namen
    * braucht und weil eine Diagnose ihn nennen koennen soll.
    */
  val typeId: NodeTypeId = NodeTypeId("ember.json.unsupported/1")

  def project(node: EditorNode): Option[UnsupportedNode] = node match
    case unsupported: UnsupportedNode => Some(unsupported)
    case _                            => None

  def rekey(node: UnsupportedNode, id: NodeId): UnsupportedNode = node.copy(id = id)

  def withChildren(node: UnsupportedNode, children: Vector[NodeId]): UnsupportedNode =
    node.copy(children = children)

  /** Der Textfallback aus dem Payload.
    *
    * Ein `text`-Feld, wenn es eines gibt, sonst leer. Bewusst keine Rekursion durch den ganzen
    * Payload: was ein fremdes Feature als Text meint, weiss dieses Modul nicht, und geratener
    * Text ist schlechter als keiner.
    */
  private[json] def fallbackOf(payload: JsonValue.Obj): String =
    payload.get("text") match
      case Some(JsonValue.Str(text)) => text
      case _                         => ""

/** Die Codecs der Knotenarten, die der Kern selbst mitbringt.
  *
  * Genau zwei, wie [[Schema.core]]: Wurzel und Textlauf. Paragraph, Heading, Liste, Link und Bild
  * gehoeren in ihre Feature-Module (§6) und bringen dort ihren eigenen Codec mit.
  */
object CoreJsonSupport:

  /** Die Wurzel hat keine eigenen Felder -- nur Kinder, und die schreibt das Envelope. */
  val root: NodeJsonCodec[RootNode] = new NodeJsonCodec[RootNode]:
    val nodeType: NodeType[RootNode] = RootNode

    def encode(
        node: RootNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] = Right(Vector.empty)

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, RootNode] = Right(RootNode.empty(id))

  /** Ein Textlauf: sein Text und seine normalisierten Markierungen.
    *
    * `marks` fehlt in der Ausgabe, wenn die Menge leer ist. Das ist die haeufigste Belegung, und
    * ein `"marks":[]` in jedem Textlauf waere Rauschen ohne Aussage. Beim Lesen bedeutet ein
    * fehlendes Feld dasselbe wie ein leeres Array.
    */
  val text: NodeJsonCodec[TextNode] = new NodeJsonCodec[TextNode]:
    val nodeType: NodeType[TextNode] = TextNode

    def encode(
        node: TextNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] =
      val base = Vector("text" -> JsonValue.Str(node.text))
      if node.marks.isEmpty then Right(base)
      else
        encodeMarks(node, context).map(marks => base :+ ("marks" -> JsonValue.Arr(marks)))

    private def encodeMarks(
        node: TextNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[JsonValue]] =
      node.marks.marks.foldLeft[Either[EncodeError, Vector[JsonValue]]](Right(Vector.empty)) {
        (accumulated, mark) =>
          for
            written <- accumulated
            encoded <- context.marks.encode(mark, context.nodeId, context.path.field("marks"))
          yield written :+ encoded
      }

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, TextNode] =
      for
        value <- payload.string("text", context.path)
        _     <- checkLength(value, context)
        marks <- decodeMarks(payload, context)
      yield TextNode(id, value, marks)

    private def checkLength(value: String, context: DecodeContext): Either[DecodeError, Unit] =
      if value.length <= context.limits.maxTextChars then Right(())
      else
        Left(
          DecodeError.LimitExceeded("maxTextChars", context.limits.maxTextChars, value.length,
            context.path.field("text"))
        )

    private def decodeMarks(
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, MarkSet] =
      val at = context.path.field("marks")
      payload.get("marks") match
        case None | Some(JsonValue.Null) => Right(MarkSet.empty)
        case Some(value) =>
          value
            .asArray(at)
            .flatMap { entries =>
              entries.zipWithIndex.foldLeft[Either[DecodeError, MarkSet]](Right(MarkSet.empty)) {
                case (accumulated, (entry, index)) =>
                  for
                    built <- accumulated
                    mark  <- context.marks.decode(entry, at.index(index))
                  yield built + mark
              }
            }

  /** Beide Codecs des Kerns. */
  val all: JsonSupport = JsonSupport.of(root, text)
