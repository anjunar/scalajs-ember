package ember.editor.image

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** A block type standing in for a paragraph.
  *
  * `ember-image` depends on the core alone (§6), so the rich-text profile is not on its classpath.
  * Using a local type is not a workaround -- it is the check that an image needs nothing from that
  * profile.
  */
private final case class BlockNode(id: NodeId, children: Vector[NodeId]) extends ElementNode

private object BlockNode extends ElementNodeType[BlockNode]:
  val typeId: NodeTypeId = NodeTypeId("test.block/1")

  def project(node: EditorNode): Option[BlockNode] = node match
    case block: BlockNode => Some(block)
    case _                => None

  def rekey(node: BlockNode, id: NodeId): BlockNode                      = node.copy(id = id)
  def withChildren(node: BlockNode, children: Vector[NodeId]): BlockNode =
    node.copy(children = children)
