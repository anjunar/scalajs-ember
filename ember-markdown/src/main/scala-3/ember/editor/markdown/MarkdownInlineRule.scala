package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** A rule for one kind of inline that becomes nodes. */
trait MarkdownInlineRule extends MarkdownRule:

  /** Inline to nodes, under the marks accumulated on the way down.
    *
    * Returns a '''vector''' because one inline can be several nodes -- and because a rule may
    * legitimately produce none, which is how a construct gets dropped with a diagnostic rather than
    * with an exception.
    */
  def decode(
      inline: MarkdownInline,
      marks: MarkSet,
      children: Vector[NodeId],
      sink: NodeSink
  ): Option[Vector[NodeId]]

  /** Whether this rule is responsible for a node. See [[MarkdownBlockRule.handles]]. */
  def handles(node: EditorNode): Boolean

  /** Node to inlines. */
  def encode(
      node: EditorNode,
      children: Vector[MarkdownInline],
      sink: SyntaxSink
  ): Option[Vector[MarkdownInline]]
