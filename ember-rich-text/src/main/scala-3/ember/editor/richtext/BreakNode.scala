package ember.editor.richtext

import ember.editor.core.*

/** A line break inside a block.
  *
  * An [[AtomNode]]: it has no children and no inside to edit. §8.1 keeps atoms deliberately
  * narrower than Lexical's `DecoratorNode` -- "keine Slots, keine editierbaren Teilbereiche" -- and
  * a break is the smallest possible case of that.
  *
  * ==Why it is not a `\n` in a text run==
  *
  * Because a text run's text is addressed in UTF-16 offsets (§11), and a caret can stand on either
  * side of a break. As a character, the two sides are the same offset with different affinity; as a
  * node, they are two distinct child positions. Selection, mapping and normalisation all become
  * simpler for it -- and the merge rule below gets a boundary it can actually see.
  */
final case class BreakNode(id: NodeId, kind: BreakKind) extends AtomNode

object BreakNode extends NodeType[BreakNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.rich-text.break/1")

  def project(node: EditorNode): Option[BreakNode] = node match
    case value: BreakNode => Some(value)
    case _                => None

  def rekey(node: BreakNode, id: NodeId): BreakNode = node.copy(id = id)

  def soft(id: NodeId): BreakNode = BreakNode(id, BreakKind.Soft)
  def hard(id: NodeId): BreakNode = BreakNode(id, BreakKind.Hard)
