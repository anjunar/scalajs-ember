package ember.editor.markdown

/** The syntax tree a Markdown source parses into.
  *
  * ==What this is not==
  *
  * It is not a document. §18.1: "Die Syntax-AST ist immutable und nur ein Import-/Exportwert, kein
  * zweiter dauerhaft synchron gehaltener Editorzustand." Nothing here knows what a `ParagraphNode`
  * is, and nothing here can be edited -- turning this into a document is a separate step, and the
  * rules that do it live in the integration module (§6).
  *
  * That separation is the reason this module depends on the core alone. A parser that produced
  * `ParagraphNode` directly would have to know the rich-text profile, and an application with its
  * own block types could not reuse it.
  *
  * ==Inline content==
  *
  * A paragraph and a heading hold [[MarkdownInline]] values, not a string. Under a profile whose
  * [[Conformance]] is [[Conformance.BlocksOnly]] that vector is a single [[MarkdownInline.Text]]
  * holding the raw source -- which is what "not parsed" means, stated as a value rather than as an
  * empty field a consumer has to know about.
  */
trait MarkdownBlock:

  /** Identity within one parse. Stable for the lifetime of the result and nothing beyond it. */
  def id: SyntaxId

  /** Where this block came from, in UTF-16 units of the original source (§18.2). */
  def span: SourceSpan

  /** Child blocks, in document order. Empty for a leaf. */
  def children: Vector[MarkdownBlock]

object MarkdownBlock:

  /** A container: block quote, list, list item. Everything that holds other blocks. */
  sealed trait Container extends MarkdownBlock

  /** A leaf. Holds inline source, literal text, or nothing at all. */
  sealed trait Leaf extends MarkdownBlock:
    final def children: Vector[MarkdownBlock] = Vector.empty

  /** A paragraph, as inline content. */
  final case class Paragraph(
      id: SyntaxId,
      span: SourceSpan,
      inlines: Vector[MarkdownInline]
  ) extends Leaf

  /** ATX (`# foo`) or Setext (`foo` over `===`).
    *
    * `style` is kept although §18.2 lets the writer pick a canonical syntax: a round trip that
    * turns every Setext heading into an ATX one is allowed, but a writer that '''wants''' to
    * preserve the input needs to know what the input was. Keeping it costs a field; recovering it
    * later is impossible.
    */
  final case class Heading(
      id: SyntaxId,
      span: SourceSpan,
      level: Int,
      style: HeadingStyle,
      inlines: Vector[MarkdownInline]
  ) extends Leaf

  /** Fenced or indented code. `literal` is verbatim, including inner blank lines.
    *
    * `fence` is `None` for an indented block. That is the only difference the model keeps -- an
    * indented block has no info string and no fence character, and pretending otherwise would mean
    * inventing values the source never had.
    */
  final case class CodeBlock(
      id: SyntaxId,
      span: SourceSpan,
      literal: String,
      fence: Option[Fence]
  ) extends Leaf

  /** A raw HTML block, kept as literal source text.
    *
    * §18.1: "Raw HTML wird als sichtbarer Text erhalten statt ausgefuehrt." The parser recognises
    * HTML blocks because CommonMark does -- their block boundaries differ from a paragraph's, and
    * ignoring them would change the structure around them. What happens to the literal afterwards
    * is the profile's decision, not the parser's, and [[MarkdownProfile.rawHtml]] states it.
    */
  final case class HtmlBlock(id: SyntaxId, span: SourceSpan, literal: String) extends Leaf

  /** `---`, `***`, `___`. Its own block type, not a run of characters (§18.2). */
  final case class ThematicBreak(id: SyntaxId, span: SourceSpan) extends Leaf

  final case class BlockQuote(id: SyntaxId, span: SourceSpan, children: Vector[MarkdownBlock])
      extends Container

  /** A list, with the tight/loose distinction §18.2 asks to preserve.
    *
    * Tightness is a property of the '''list''', not of an item: CommonMark decides it once, from
    * blank lines between items and inside them, and then renders every item the same way.
    */
  final case class MarkdownList(
      id: SyntaxId,
      span: SourceSpan,
      kind: ListKind,
      tight: Boolean,
      children: Vector[MarkdownBlock]
  ) extends Container

  /** One item. Holds blocks, plural -- §18.2 asks for "mehrteilige ListItems". */
  final case class ListItem(id: SyntaxId, span: SourceSpan, children: Vector[MarkdownBlock])
      extends Container

  /** A GFM pipe table, only under a profile with [[MarkdownProfile.tables]].
    *
    * §18.2 names GFM tables as '''not''' part of the CommonMark guarantee, and they are not: a
    * CommonMark reader sees the same source as a paragraph with pipes in it. The profile flag is
    * where an application says it wants the extension, and the conformance suite runs without it.
    *
    * Rows hold cells, and the header row is the first one. `alignments` has one entry per column of
    * the delimiter row -- which is also the number of cells every row has.
    */
  final case class Table(
      id: SyntaxId,
      span: SourceSpan,
      alignments: Vector[TableAlignment],
      children: Vector[MarkdownBlock]
  ) extends Container

  final case class TableRow(
      id: SyntaxId,
      span: SourceSpan,
      header: Boolean,
      children: Vector[MarkdownBlock]
  ) extends Container

  /** One cell: a single line of inline content, with its row's header flag and its column's
    * alignment carried along, so that a rule can build a cell without looking at its parents.
    */
  final case class TableCell(
      id: SyntaxId,
      span: SourceSpan,
      header: Boolean,
      alignment: TableAlignment,
      inlines: Vector[MarkdownInline]
  ) extends Leaf
