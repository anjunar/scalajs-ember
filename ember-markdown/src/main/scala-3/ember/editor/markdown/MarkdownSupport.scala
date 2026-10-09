package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** The rules an application registers, in priority order.
  *
  * Like [[ember.editor.core.CommandRegistry]] and `JsonSupport`: a value that is combined with
  * `++`, not a global registry. §6 forbids "eager Sammelregistrierungen" -- an application that
  * wants paragraphs and nothing else registers paragraphs and nothing else.
  */
final case class MarkdownSupport(
    blocks: Vector[MarkdownBlockRule] = Vector.empty,
    inlines: Vector[MarkdownInlineRule] = Vector.empty,
    marks: Vector[MarkdownMarkRule] = Vector.empty
):

  def ++(other: MarkdownSupport): MarkdownSupport =
    MarkdownSupport(blocks ++ other.blocks, inlines ++ other.inlines, marks ++ other.marks)

  /** The rule ids that appear more than once. A support with duplicates is a configuration mistake,
    * and the codec refuses it rather than letting the earlier one silently win.
    */
  def duplicates: Vector[String] =
    val all = blocks.map(_.id) ++ inlines.map(_.id) ++ marks.map(_.id)
    all.groupBy(identity).collect { case (id, seen) if seen.length > 1 => id }.toVector.sorted

object MarkdownSupport:

  val empty: MarkdownSupport = MarkdownSupport()

  def of(rules: MarkdownRule*): MarkdownSupport =
    rules.foldLeft(empty) { (support, rule) =>
      rule match
        case block: MarkdownBlockRule   => support.copy(blocks = support.blocks :+ block)
        case inline: MarkdownInlineRule => support.copy(inlines = support.inlines :+ inline)
        case mark: MarkdownMarkRule     => support.copy(marks = support.marks :+ mark)
    }
