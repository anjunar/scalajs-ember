package ember.editor.markdown

/** The syntax tree a Markdown source parses into.
  *
  * ==What this is not==
  *
  * It is not a document. §18.1: "Die Syntax-AST ist immutable und nur ein Import-/Exportwert,
  * kein zweiter dauerhaft synchron gehaltener Editorzustand." Nothing here knows what a
  * `ParagraphNode` is, and nothing here can be edited -- turning this into a document is a
  * separate step, and the rules that do it live in the integration module (§6).
  *
  * That separation is the reason this module depends on the core alone. A parser that produced
  * `ParagraphNode` directly would have to know the rich-text profile, and an application with
  * its own block types could not reuse it.
  *
  * ==Inline content==
  *
  * A paragraph and a heading hold [[MarkdownInline]] values, not a string. Under a profile
  * whose [[Conformance]] is [[Conformance.BlocksOnly]] that vector is a single
  * [[MarkdownInline.Text]] holding the raw source -- which is what "not parsed" means, stated
  * as a value rather than as an empty field a consumer has to know about.
  */
sealed trait MarkdownBlock:

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
    * preserve the input needs to know what the input was. Keeping it costs a field; recovering
    * it later is impossible.
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
    * `fence` is `None` for an indented block. That is the only difference the model keeps --
    * an indented block has no info string and no fence character, and pretending otherwise
    * would mean inventing values the source never had.
    */
  final case class CodeBlock(
      id: SyntaxId,
      span: SourceSpan,
      literal: String,
      fence: Option[Fence]
  ) extends Leaf

  /** A raw HTML block, kept as literal source text.
    *
    * §18.1: "Raw HTML wird als sichtbarer Text erhalten statt ausgefuehrt." The parser
    * recognises HTML blocks because CommonMark does -- their block boundaries differ from a
    * paragraph's, and ignoring them would change the structure around them. What happens to
    * the literal afterwards is the profile's decision, not the parser's, and
    * [[MarkdownProfile.rawHtml]] states it.
    */
  final case class HtmlBlock(id: SyntaxId, span: SourceSpan, literal: String) extends Leaf

  /** `---`, `***`, `___`. Its own block type, not a run of characters (§18.2). */
  final case class ThematicBreak(id: SyntaxId, span: SourceSpan) extends Leaf

  final case class BlockQuote(id: SyntaxId, span: SourceSpan, children: Vector[MarkdownBlock])
      extends Container

  /** A list, with the tight/loose distinction §18.2 asks to preserve.
    *
    * Tightness is a property of the '''list''', not of an item: CommonMark decides it once,
    * from blank lines between items and inside them, and then renders every item the same way.
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

/** Inline syntax: what lives inside a paragraph or a heading.
  *
  * ==Why the destinations are plain strings==
  *
  * A [[MarkdownInline.Link]] carries `destination: String` and not a `LinkUrl`. That looks like
  * a missed opportunity for the "type is the door" pattern the rest of this editor uses -- and
  * it is deliberate: `LinkUrl` lives in `ember-link` and `MediaUrl` in `ember-image`, and §6
  * gives this module the core alone.
  *
  * The door is still there, one step later. `ember-standard` runs the application's policy when
  * it turns syntax into a document, exactly as §19.1 asks: "URLs werden nach
  * Entities-/Whitespace-Normalisierung durch die jeweilige Link-/Media-Policy geprueft." The
  * parser normalises; the adapter decides. A parser that decided would need to know which
  * policy, and there is no such thing as the one policy.
  */
sealed trait MarkdownInline:

  def id: SyntaxId

  /** Where this came from, in UTF-16 units of the original source. */
  def span: SourceSpan

  def children: Vector[MarkdownInline]

object MarkdownInline:

  sealed trait Leaf extends MarkdownInline:
    final def children: Vector[MarkdownInline] = Vector.empty

  /** Literal text. Escapes and entities are already resolved. */
  final case class Text(id: SyntaxId, span: SourceSpan, value: String) extends Leaf

  /** A code span. `literal` is verbatim, with the specification's whitespace rule applied. */
  final case class Code(id: SyntaxId, span: SourceSpan, literal: String) extends Leaf

  /** A newline inside a paragraph. §18.2 asks to keep it apart from a hard break. */
  final case class SoftBreak(id: SyntaxId, span: SourceSpan) extends Leaf

  /** Two trailing spaces, or a trailing backslash. A `<br>`. */
  final case class HardBreak(id: SyntaxId, span: SourceSpan) extends Leaf

  /** A raw inline tag, kept as text under the safe profile (§18.1). */
  final case class HtmlInline(id: SyntaxId, span: SourceSpan, literal: String) extends Leaf

  final case class Emphasis(id: SyntaxId, span: SourceSpan, children: Vector[MarkdownInline])
      extends MarkdownInline

  final case class Strong(id: SyntaxId, span: SourceSpan, children: Vector[MarkdownInline])
      extends MarkdownInline

  final case class Link(
      id: SyntaxId,
      span: SourceSpan,
      destination: String,
      title: Option[String],
      children: Vector[MarkdownInline]
  ) extends MarkdownInline

  /** An image. Its children are the alt text -- CommonMark models it that way because the alt
    * text may itself contain markup, and flattening it too early would lose that.
    */
  final case class Image(
      id: SyntaxId,
      span: SourceSpan,
      destination: String,
      title: Option[String],
      children: Vector[MarkdownInline]
  ) extends MarkdownInline

  /** The plain text of an inline tree -- what an image's alt attribute becomes. */
  def plainText(inlines: Vector[MarkdownInline]): String =
    val out = new StringBuilder
    def walk(inline: MarkdownInline): Unit = inline match
      case Text(_, _, value)     => out.append(value)
      case Code(_, _, literal)   => out.append(literal)
      case SoftBreak(_, _)       => out.append('\n')
      case HardBreak(_, _)       => out.append('\n')
      case HtmlInline(_, _, _)   => ()
      case other                 => other.children.foreach(walk)
    inlines.foreach(walk)
    out.toString

/** Which syntax produced a heading. */
enum HeadingStyle:
  case Atx, Setext

/** The fence of a fenced code block.
  *
  * @param char
  *   `` ` `` or `~`. Needed for the export side: a block whose content holds backticks has to
  *   be fenced with something else, or with a longer run.
  * @param length
  *   how many fence characters opened it, at least three.
  * @param info
  *   the info string, unescaped and trimmed. Empty when there was none. §18.2 wants the
  *   language typed -- that typing happens in the adapter, because [[CodeLanguage]] lives in
  *   `ember-code` and this module does not know it.
  */
final case class Fence(char: Char, length: Int, info: String)

/** Bullet or ordered, with what the source actually used. */
enum ListKind:

  /** `-`, `+` or `*`. The character matters: changing it starts a new list (§18.2). */
  case Bullet(marker: Char)

  /** `1.` or `1)`. `start` is the first item's number, `delimiter` is `.` or `)`. */
  case Ordered(start: Int, delimiter: Char)

/** A parsed source, and everything that came with it. */
final case class MarkdownDocument(
    id: SyntaxId,
    span: SourceSpan,
    children: Vector[MarkdownBlock]
) extends MarkdownBlock

/** A range of the original source, in UTF-16 units (§18.2).
  *
  * ==Why UTF-16 and not lines and columns==
  *
  * Because everything else in this editor counts in UTF-16 units -- §11 fixes it for text
  * positions, and `spliceText` in ui-core takes them. A source map that spoke in lines and
  * columns would need a conversion at every use, and the conversion is where an off-by-one
  * hides. CommonMark's own reference implementation reports line/column; this is the one place
  * the port deliberately does something else.
  *
  * ==Why no constructor check==
  *
  * `start <= end` and both non-negative is an invariant of the '''parser''', not of a caller
  * -- nobody outside builds one. A guard in the constructor would check the wrong party.
  * `MarkdownBlockSpec` checks the right one: every span of every parse of all
  * [[SpecFixtures]] examples is well formed and contained in its parent's.
  */
final case class SourceSpan(start: Int, end: Int):

  def length: Int = end - start

  def isEmpty: Boolean = end == start

  /** Half-open: the end offset is '''not''' inside. A caret sitting there belongs to whatever
    * follows, which is what a source map is asked at a block boundary.
    */
  def contains(offset: Int): Boolean = offset >= start && offset < end

  def containsSpan(other: SourceSpan): Boolean = other.start >= start && other.end <= end

  def render: String = s"[$start,$end)"

/** Identity of a block inside one parse result.
  *
  * Opaque and mintable only by the parser: an id that did not come from a parse cannot address
  * anything, and letting one be fabricated would make [[SourceMap]] lookups silently miss.
  * There is nothing to validate here -- the door exists to keep the numbering the parser's
  * business, not to check a value.
  */
opaque type SyntaxId = Int

object SyntaxId:

  private[markdown] def apply(value: Int): SyntaxId = value

  extension (id: SyntaxId)
    def value: Int    = id
    def render: String = s"#$id"
