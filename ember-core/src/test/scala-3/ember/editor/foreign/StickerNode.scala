package ember.editor.foreign

import ember.editor.core.*

/** Ein atomarer Fremdknoten. Sein Inneres ist kein Textbereich. */
final case class StickerNode(id: NodeId, emoji: String) extends AtomNode

object StickerNode extends NodeType[StickerNode]:

  val typeId: NodeTypeId = NodeTypeId("foreign.sticker/1")

  def project(node: EditorNode): Option[StickerNode] = node match
    case sticker: StickerNode => Some(sticker)
    case _                    => None

  def rekey(node: StickerNode, id: NodeId): StickerNode = node.copy(id = id)
