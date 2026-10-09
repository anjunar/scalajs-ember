package ember.editor.richtext

import ember.editor.core.*

/** A heading. A block containing inline content, like a paragraph, plus a level.
  *
  * ==Why a level and not six node types==
  *
  * `H1` through `H6` differ in exactly one number and in nothing else -- same children, same
  * editing behaviour, same normalisation. Six descriptors would mean six of everything, and
  * `SetHeading` would become a replacement of one type by another rather than a change of one
  * field. §8.3 reserves node type replacement for something else entirely.
  */
final case class HeadingNode(id: NodeId, children: Vector[NodeId], level: HeadingLevel)
    extends ElementNode

object HeadingNode extends ElementNodeType[HeadingNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.rich-text.heading/1")

  def project(node: EditorNode): Option[HeadingNode] = node match
    case heading: HeadingNode => Some(heading)
    case _                    => None

  def rekey(node: HeadingNode, id: NodeId): HeadingNode = node.copy(id = id)

  def withChildren(node: HeadingNode, children: Vector[NodeId]): HeadingNode =
    node.copy(children = children)

  def empty(id: NodeId, level: HeadingLevel): HeadingNode = HeadingNode(id, Vector.empty, level)

  override def validate(node: HeadingNode, document: DocumentRead): Vector[Violation] =
    InlineContent.validate(node, document)
