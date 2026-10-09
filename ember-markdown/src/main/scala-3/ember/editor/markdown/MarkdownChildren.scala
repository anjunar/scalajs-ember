package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** What a block rule gets as the content of its node.
  *
  * One of the two is always empty. Which one is not a question a rule has to ask -- a quote rule
  * reads `blocks`, a paragraph rule reads `inlines`, and neither can be surprised.
  */
final case class MarkdownChildren(
    blocks: Vector[MarkdownBlock] = Vector.empty,
    inlines: Vector[MarkdownInline] = Vector.empty
)
