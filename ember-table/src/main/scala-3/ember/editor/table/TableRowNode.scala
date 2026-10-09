package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*

/** One row. Structure only -- it has no properties of its own. */
final case class TableRowNode(id: NodeId, children: Vector[NodeId])
    extends ElementNode
    with StructuralElementNode

object TableRowNode extends ElementNodeType[TableRowNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.table.row/1")

  def project(node: EditorNode): Option[TableRowNode] = node match
    case value: TableRowNode => Some(value)
    case _                   => None

  def rekey(node: TableRowNode, id: NodeId): TableRowNode = node.copy(id = id)

  def withChildren(node: TableRowNode, children: Vector[NodeId]): TableRowNode =
    node.copy(children = children)
