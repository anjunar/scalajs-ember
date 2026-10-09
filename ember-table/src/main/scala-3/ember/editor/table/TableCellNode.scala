package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*

/** One cell: an isolated region of blocks.
  *
  * [[IsolatingElementNode]] is what keeps Backspace at its start from pulling the text into the
  * cell before it, and a range from one cell into another from joining them.
  */
final case class TableCellNode(
    id: NodeId,
    children: Vector[NodeId],
    header: Boolean = false,
    alignment: ColumnAlignment = ColumnAlignment.Default
) extends ElementNode
    with IsolatingElementNode

object TableCellNode extends ElementNodeType[TableCellNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.table.cell/1")

  def project(node: EditorNode): Option[TableCellNode] = node match
    case value: TableCellNode => Some(value)
    case _                    => None

  def rekey(node: TableCellNode, id: NodeId): TableCellNode = node.copy(id = id)

  def withChildren(node: TableCellNode, children: Vector[NodeId]): TableCellNode =
    node.copy(children = children)
