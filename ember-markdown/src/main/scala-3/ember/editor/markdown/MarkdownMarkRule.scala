package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** A rule for an inline that is a mark on the text inside it, not a node around it. */
trait MarkdownMarkRule extends MarkdownRule:

  /** The mark this inline adds, or `None` if this rule does not know it. */
  def markFor(inline: MarkdownInline): Option[TextMark]

  /** Whether this rule is responsible for a mark.
    *
    * The reverse of [[markFor]], and it has to be asked separately: encoding starts from a
    * [[MarkSet]] and has no inline to offer. An earlier version handed the rule a synthetic
    * [[MarkdownInline.Text]] to identify the mark from -- which no rule ever recognised, so every
    * mark silently vanished on export. A round trip found it; nothing else would have.
    */
  def owns(mark: TextMark): Boolean

  /** Wraps already-written inlines in the syntax for this mark.
    *
    * `None` means the mark has no Markdown spelling -- underline, for instance. §18.2 lists it
    * explicitly as '''not''' a CommonMark guarantee, and the encoder turns a `None` here into a
    * loss diagnostic rather than into silently dropped formatting.
    */
  def inlineFor(
      mark: TextMark,
      children: Vector[MarkdownInline],
      sink: SyntaxSink
  ): Option[MarkdownInline]

  /** The order marks nest in when several apply to one run.
    *
    * Deterministic output needs a total order: `**a**` and `*a*` around the same run can be written
    * either way round, and a writer that picked by hash would produce a different document on every
    * run. Lower sorts outermost.
    */
  def nesting: Int
