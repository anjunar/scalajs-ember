package ember.editor.markdown

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** A block type that exists only in this suite. */
private final case class Box(id: NodeId, children: Vector[NodeId]) extends ElementNode

private object Box extends ElementNodeType[Box]:
  val typeId: NodeTypeId = NodeTypeId("test.box/1")

  def project(node: EditorNode): Option[Box] = node match
    case box: Box => Some(box)
    case _        => None

  def rekey(node: Box, id: NodeId): Box                      = node.copy(id = id)
  def withChildren(node: Box, children: Vector[NodeId]): Box = node.copy(children = children)
