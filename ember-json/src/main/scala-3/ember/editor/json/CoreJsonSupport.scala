package ember.editor.json

import ember.editor.core.*

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
      else encodeMarks(node, context).map(marks => base :+ ("marks" -> JsonValue.Arr(marks)))

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
          DecodeError.LimitExceeded(
            "maxTextChars",
            context.limits.maxTextChars,
            value.length,
            context.path.field("text")
          )
        )

    private def decodeMarks(
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, MarkSet] =
      val at = context.path.field("marks")
      payload.get("marks") match
        case None | Some(JsonValue.Null) => Right(MarkSet.empty)
        case Some(value)                 =>
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
