package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*

/** A table.
  *
  * ==The shape==
  *
  * {{{
  * TableNode
  *   TableRowNode
  *     TableCellNode
  *       ParagraphNode ...
  * }}}
  *
  * A cell holds '''blocks''', not inline content. HTML allows a list in a cell, and every editor
  * that lets Enter make a new line inside a cell needs a second block to put it in. Markdown can
  * only write a single line per cell, and the Markdown adapter reports anything more as a loss
  * rather than restricting the document to what one format can spell.
  *
  * ==Where the column facts live==
  *
  * Whether the first row is a header and how each column is aligned are facts about the '''table'''
  * -- GFM writes them once, in the delimiter row. The cells carry a copy, because a cell has to be
  * rendered as `th` or `td` and with its alignment without knowing its row or column, and a
  * semantic description sees one node at a time (§15.1). [[TableNormalization]] keeps the copies in
  * step, so the table stays the only place anyone has to change.
  *
  * ==Why validation is lenient==
  *
  * An import builds a document without running transforms, and pasted HTML is full of tables that
  * are not rectangular. Refusing those would refuse the paste; accepting them and letting the
  * normalisation square them up the moment they enter a session keeps the content. A loose row, a
  * ragged row and a missing header flag are all repaired, not rejected.
  */
final case class TableNode(
    id: NodeId,
    children: Vector[NodeId],
    header: Boolean = true,
    alignments: Vector[ColumnAlignment] = Vector.empty
) extends ElementNode
    with StructuralElementNode:

  def alignmentOf(column: Int): ColumnAlignment =
    alignments.lift(column).getOrElse(ColumnAlignment.Default)

object TableNode extends ElementNodeType[TableNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.table.table/1")

  def project(node: EditorNode): Option[TableNode] = node match
    case value: TableNode => Some(value)
    case _                => None

  def rekey(node: TableNode, id: NodeId): TableNode = node.copy(id = id)

  def withChildren(node: TableNode, children: Vector[NodeId]): TableNode =
    node.copy(children = children)
