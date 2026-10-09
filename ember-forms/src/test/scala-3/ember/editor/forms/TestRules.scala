package ember.editor.forms

import ember.editor.core.*
import ember.editor.json.*
import ember.editor.markdown.*

/** The Markdown and JSON adapters for that profile. */
object TestRules:

  val block: MarkdownBlockRule = new MarkdownBlockRule:
    val id = "test.block"

    def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId] =
      block match
        case MarkdownBlock.Paragraph(_, span, _)     => Some(sink.add(span)(Box(_, children)))
        case MarkdownBlock.Heading(_, span, _, _, _) => Some(sink.add(span)(Box(_, children)))
        case _                                       => None

    def handles(node: EditorNode): Boolean = node.isInstanceOf[Box]

    def encode(
        node: EditorNode,
        children: MarkdownChildren,
        sink: SyntaxSink
    ): Option[MarkdownBlock] = node match
      case _: Box => Some(MarkdownBlock.Paragraph(sink.fresh(), sink.nowhere, children.inlines))
      case _      => None

  val text: MarkdownInlineRule = new MarkdownInlineRule:
    val id = "test.text"

    def decode(
        inline: MarkdownInline,
        marks: MarkSet,
        children: Vector[NodeId],
        sink: NodeSink
    ): Option[Vector[NodeId]] = inline match
      case MarkdownInline.Text(_, span, value) =>
        Some(Vector(sink.add(span)(TextNode(_, value, marks))))
      case MarkdownInline.SoftBreak(_, span) =>
        Some(Vector(sink.add(span)(TextNode(_, " ", marks))))
      case _ => None

    def handles(node: EditorNode): Boolean = node.isInstanceOf[TextNode]

    def encode(
        node: EditorNode,
        children: Vector[MarkdownInline],
        sink: SyntaxSink
    ): Option[Vector[MarkdownInline]] = node match
      case run: TextNode => Some(Vector(MarkdownInline.Text(sink.fresh(), sink.nowhere, run.text)))
      case _             => None

  /** [[Loud]] can be written, [[Quiet]] cannot -- and the second is the interesting one. */
  val loud: MarkdownMarkRule = new MarkdownMarkRule:
    val id      = "test.marks"
    val nesting = 1

    def markFor(inline: MarkdownInline): Option[TextMark] = inline match
      case MarkdownInline.Emphasis(_, _, _) => Some(Loud)
      case _                                => None

    def owns(mark: TextMark): Boolean = mark == Loud || mark == Quiet

    def inlineFor(
        mark: TextMark,
        children: Vector[MarkdownInline],
        sink: SyntaxSink
    ): Option[MarkdownInline] =
      if mark == Loud then Some(MarkdownInline.Emphasis(sink.fresh(), sink.nowhere, children))
      else None

  private val boxCodec: NodeJsonCodec[Box] = new NodeJsonCodec[Box]:
    val nodeType: NodeType[Box] = Box

    def encode(
        node: Box,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] =
      Right(Vector.empty)

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, Box] = Right(Box(id, Vector.empty))

  private def markCodec(mark: TextMark): MarkJsonCodec[TextMark] = new MarkJsonCodec[TextMark]:
    val markId: MarkId = mark.markId

    def project(candidate: TextMark): Option[TextMark] =
      Option.when(candidate.markId == mark.markId)(mark)

    def encode(value: TextMark): Vector[(String, JsonValue)] = Vector.empty

    def decode(payload: JsonValue.Obj, at: DiagnosticPath): Either[DecodeError, TextMark] =
      Right(mark)

  val jsonSupport: JsonSupport =
    (CoreJsonSupport.all ++ JsonSupport.of(boxCodec))
      .withMarks(MarkSupport.of(markCodec(Loud), markCodec(Quiet)))
