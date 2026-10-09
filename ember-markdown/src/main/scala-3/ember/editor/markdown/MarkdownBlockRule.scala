package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** A rule for one kind of block. */
trait MarkdownBlockRule extends MarkdownRule:

  /** Syntax to node. `children` are the ids this block's children already produced.
    *
    * `None` means "not mine" -- the codec tries the next rule, exactly as §12's command chain does.
    * A rule that returns `None` must not have written to the sink.
    */
  def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId]

  /** Whether this rule is responsible for a node.
    *
    * Separate from [[encode]] and deliberately cheap: the codec has to know whether a node's
    * children are blocks or inline content '''before''' it descends, and asking by calling `encode`
    * would mean calling it speculatively -- with a sink that then collects diagnostics for a
    * conversion that never happened.
    */
  def handles(node: EditorNode): Boolean

  /** Node to syntax.
    *
    * `children` carries both shapes because a block has one or the other: a quote holds blocks, a
    * paragraph holds inline content, and which one a node has is [[handles]]'s answer one level
    * down.
    */
  def encode(
      node: EditorNode,
      children: MarkdownChildren,
      sink: SyntaxSink
  ): Option[MarkdownBlock]
