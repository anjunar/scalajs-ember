package ember.editor.list

import ember.editor.core.*

/** One item of a list.
  *
  * ==Why it holds blocks and not inline content==
  *
  * §8.2 again: "ein ListItem Blockinhalte." That is not pedantry -- it is what makes a list item
  * able to hold two paragraphs, a nested list, or a quote, all of which real documents do. An item
  * that held inline content directly would need a second, parallel structure for every one of those
  * cases.
  *
  * The price is one more level of nesting than a naive model, and it is paid in exactly one place:
  * [[ListNormalization]] gives an empty item a paragraph, so that there is always a caret position
  * inside it. Same reason as `BlockNeedsText` in the rich-text profile.
  */
final case class ListItemNode(id: NodeId, children: Vector[NodeId]) extends ElementNode

object ListItemNode extends ElementNodeType[ListItemNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.list.item/1")

  def project(node: EditorNode): Option[ListItemNode] = node match
    case value: ListItemNode => Some(value)
    case _                   => None

  def rekey(node: ListItemNode, id: NodeId): ListItemNode = node.copy(id = id)

  def withChildren(node: ListItemNode, children: Vector[NodeId]): ListItemNode =
    node.copy(children = children)

  def empty(id: NodeId): ListItemNode = ListItemNode(id, Vector.empty)
