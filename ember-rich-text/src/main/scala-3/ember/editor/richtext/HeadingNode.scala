package ember.editor.richtext

import ember.editor.core.*

/** A heading level. Typed, not an `Int`.
  *
  * The plan asks for "typisierte Level" and the reason shows up at the first call site: an `Int`
  * makes `SetHeading(0)` and `SetHeading(9)` compile, and both are documents that no renderer can
  * produce. HTML has six levels; so has this.
  */
enum HeadingLevel(val level: Int):
  case H1 extends HeadingLevel(1)
  case H2 extends HeadingLevel(2)
  case H3 extends HeadingLevel(3)
  case H4 extends HeadingLevel(4)
  case H5 extends HeadingLevel(5)
  case H6 extends HeadingLevel(6)

object HeadingLevel:

  /** For wire formats, where the level arrives as a number. `None` if it is not one of the six. */
  def fromInt(level: Int): Option[HeadingLevel] = values.find(_.level == level)

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
