package ember.editor.richtext

import ember.editor.core.*

/** Shared by inline-content blocks and link containers, without naming feature modules. */
object InlineContent:
  def validate(node: ElementNode, document: DocumentRead): Vector[Violation] =
    node.children.flatMap(document.node).collect {
      case child: ElementNode if !child.isInstanceOf[InlineElementNode] =>
        Violation.NodeRejected(
          node.id,
          "Inline content cannot contain a block",
          document.pathTo(child.id)
        )
      case child: ThematicBreakNode =>
        Violation.NodeRejected(
          node.id,
          "A thematic break belongs between blocks",
          document.pathTo(child.id)
        )
    }
