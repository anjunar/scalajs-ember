package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*

/** A table read as a grid.
  *
  * Built fresh from the document every time it is needed and never stored: a stored grid would be a
  * second description of the table, and the first edit would make the two disagree.
  */
final case class TableGrid(
    table: TableNode,
    rows: Vector[TableRowNode],
    cells: Vector[Vector[TableCellNode]]
):

  def columns: Int = cells.map(_.length).maxOption.getOrElse(0)

  def rowCount: Int = rows.length

  /** Row and column of a cell of this table. */
  def positionOf(cell: NodeId): Option[(Int, Int)] =
    cells.zipWithIndex.collectFirst {
      case (row, rowIndex) if row.exists(_.id == cell) => (rowIndex, row.indexWhere(_.id == cell))
    }

  def cellAt(row: Int, column: Int): Option[TableCellNode] =
    cells.lift(row).flatMap(_.lift(column))

  /** Every cell, row by row. */
  def ordered: Vector[TableCellNode] = cells.flatten

  /** The rows and columns spanned by two cells, as inclusive ranges. */
  def span(anchor: NodeId, focus: NodeId): Option[(Range, Range)] =
    for
      (anchorRow, anchorColumn) <- positionOf(anchor)
      (focusRow, focusColumn)   <- positionOf(focus)
    yield (
      math.min(anchorRow, focusRow) to math.max(anchorRow, focusRow),
      math.min(anchorColumn, focusColumn) to math.max(anchorColumn, focusColumn)
    )

  /** The cells of the rectangle two cells span, row by row. */
  def rectangle(anchor: NodeId, focus: NodeId): Vector[TableCellNode] =
    span(anchor, focus) match
      case Some((rowRange, columnRange)) =>
        rowRange.toVector.flatMap(row =>
          columnRange.toVector.flatMap(column => cellAt(row, column))
        )
      case None => Vector.empty
