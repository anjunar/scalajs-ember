package ember.editor.markdown

/** Inline syntax: what lives inside a paragraph or a heading.
  *
  * ==Why the destinations are plain strings==
  *
  * A [[MarkdownInline.Link]] carries `destination: String` and not a `LinkUrl`. That looks like a
  * missed opportunity for the "type is the door" pattern the rest of this editor uses -- and it is
  * deliberate: `LinkUrl` lives in `ember-link` and `MediaUrl` in `ember-image`, and §6 gives this
  * module the core alone.
  *
  * The door is still there, one step later. `ember-standard` runs the application's policy when it
  * turns syntax into a document, exactly as §19.1 asks: "URLs werden nach
  * Entities-/Whitespace-Normalisierung durch die jeweilige Link-/Media-Policy geprueft." The parser
  * normalises; the adapter decides. A parser that decided would need to know which policy, and
  * there is no such thing as the one policy.
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

  /** An image. Its children are the alt text -- CommonMark models it that way because the alt text
    * may itself contain markup, and flattening it too early would lose that.
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
    val out                                = new StringBuilder
    def walk(inline: MarkdownInline): Unit = inline match
      case Text(_, _, value)   => out.append(value)
      case Code(_, _, literal) => out.append(literal)
      case SoftBreak(_, _)     => out.append('\n')
      case HardBreak(_, _)     => out.append('\n')
      case HtmlInline(_, _, _) => ()
      case other               => other.children.foreach(walk)
    inlines.foreach(walk)
    out.toString
