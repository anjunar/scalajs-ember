package ember.editor.richtext

import ember.editor.core.*

/** A thematic break -- the horizontal rule. A block-level atom.
  *
  * Block-level, unlike [[BreakNode]]: it stands between blocks, not inside one. That is why it is a
  * separate type rather than a third [[BreakKind]] -- the two live at different levels of the tree,
  * and a schema rule that allowed both in both places would allow documents that neither Markdown
  * nor HTML can express.
  */
final case class ThematicBreakNode(id: NodeId) extends AtomNode

object ThematicBreakNode extends NodeType[ThematicBreakNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.rich-text.thematic-break/1")

  def project(node: EditorNode): Option[ThematicBreakNode] = node match
    case value: ThematicBreakNode => Some(value)
    case _                        => None

  def rekey(node: ThematicBreakNode, id: NodeId): ThematicBreakNode = node.copy(id = id)
