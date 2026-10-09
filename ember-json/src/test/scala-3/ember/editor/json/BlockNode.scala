package ember.editor.json

import ember.editor.core.*

/** Lokale typisierte Testknoten.
  *
  * Bewusst hier und nicht aus `ember-rich-text`: §6 stellt `json` neben die Node-Module, nicht
  * ueber sie. Ein Test, der `ParagraphNode` benutzte, wuerde eine Abhaengigkeit belegen, die es
  * nicht gibt -- und die Codecs der Feature-Module entstehen ohnehin erst in P16/P18.
  */
final case class BlockNode(id: NodeId, children: Vector[NodeId], label: String) extends ElementNode

object BlockNode extends ElementNodeType[BlockNode]:

  val typeId: NodeTypeId = NodeTypeId("test.block/1")

  def project(node: EditorNode): Option[BlockNode] = node match
    case block: BlockNode => Some(block)
    case _                => None

  def rekey(node: BlockNode, id: NodeId): BlockNode = node.copy(id = id)

  def withChildren(node: BlockNode, children: Vector[NodeId]): BlockNode =
    node.copy(children = children)

  def apply(id: NodeId, children: Vector[NodeId]): BlockNode = BlockNode(id, children, "")
