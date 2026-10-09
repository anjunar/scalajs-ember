package ember.editor.history

import ember.editor.core.*

/** Ein Block mit Kindern. Lokal, weil §6 `history` neben `rich-text` stellt und nicht darueber:
  * eine Anwendung, die eigene Blockarten mitbringt, bekommt dieselbe History.
  */
final case class BlockNode(id: NodeId, children: Vector[NodeId]) extends ElementNode

object BlockNode extends ElementNodeType[BlockNode]:
  val typeId: NodeTypeId = NodeTypeId("test.block/1")

  def project(node: EditorNode): Option[BlockNode] = node match
    case block: BlockNode => Some(block)
    case _                => None

  def rekey(node: BlockNode, id: NodeId): BlockNode = node.copy(id = id)

  def withChildren(node: BlockNode, children: Vector[NodeId]): BlockNode =
    node.copy(children = children)
