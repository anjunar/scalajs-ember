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

/** Where a caret or a cell selection stands, in table terms. */
final case class CellContext(grid: TableGrid, cell: NodeId, row: Int, column: Int)

/** Reading and building tables. */
object Tables:

  def grid(document: DocumentRead, table: NodeId): Option[TableGrid] =
    document.node(table).collect { case value: TableNode => value }.map { node =>
      val rows  = node.children.flatMap(document.node).collect { case row: TableRowNode => row }
      val cells = rows.map(row =>
        row.children.flatMap(document.node).collect { case cell: TableCellNode => cell }
      )
      TableGrid(node, rows, cells)
    }

  /** The nearest cell containing a node, the node itself included. */
  def cellOf(document: DocumentRead, node: NodeId): Option[NodeId] =
    (node +: document.ancestorsOf(node)).find(id =>
      document.node(id).exists(_.isInstanceOf[TableCellNode])
    )

  /** The table a cell belongs to. */
  def tableOfCell(document: DocumentRead, cell: NodeId): Option[NodeId] =
    document
      .parentOf(cell)
      .flatMap(document.parentOf)
      .filter(id => document.node(id).exists(_.isInstanceOf[TableNode]))

  def contextOfCell(document: DocumentRead, cell: NodeId): Option[CellContext] =
    for
      table         <- tableOfCell(document, cell)
      grid          <- grid(document, table)
      (row, column) <- grid.positionOf(cell)
    yield CellContext(grid, cell, row, column)

  /** The cell context of a selection: the focus of a range, or the focus cell of a cell selection.
    */
  def contextAt(document: DocumentRead, selection: Option[Selection]): Option[CellContext] =
    selection match
      case Some(cells: TableSelection) => contextOfCell(document, cells.focus)
      case Some(range: RangeSelection) =>
        cellOf(document, range.focus.owner).flatMap(contextOfCell(document, _))
      case _ => None

  /** The rows and columns a selection covers: a cell selection's rectangle, or the caret's cell. */
  def coveredAt(
      document: DocumentRead,
      selection: Option[Selection]
  ): Option[(CellContext, Range, Range)] =
    contextAt(document, selection).map { context =>
      selection match
        case Some(cells: TableSelection) =>
          context.grid.span(cells.anchor, cells.focus) match
            case Some((rows, columns)) => (context, rows, columns)
            case None => (context, context.row to context.row, context.column to context.column)
        case _ => (context, context.row to context.row, context.column to context.column)
    }

  // -----------------------------------------------------------------------------------------
  // Carets
  // -----------------------------------------------------------------------------------------

  def firstRun(document: DocumentRead, node: NodeId): Option[NodeId] =
    document.subtreeOf(node).find(id => document.node(id).exists(_.isInstanceOf[TextNode]))

  def lastRun(document: DocumentRead, node: NodeId): Option[NodeId] =
    document
      .subtreeOf(node)
      .filter(id => document.node(id).exists(_.isInstanceOf[TextNode]))
      .toVector
      .lastOption

  def startOf(document: DocumentRead, node: NodeId): Point =
    firstRun(document, node)
      .map(Point.textBefore(_, 0))
      .getOrElse(Point.childrenBefore(node, 0))

  def endOf(document: DocumentRead, node: NodeId): Point =
    lastRun(document, node) match
      case Some(run) =>
        val length =
          document.node(run).collect { case text: TextNode => text.text.length }.getOrElse(0)
        Point.textBefore(run, length)
      case None => Point.childrenBefore(node, document.childrenOf(node).length)

  // -----------------------------------------------------------------------------------------
  // Building
  // -----------------------------------------------------------------------------------------

  /** How many ids an empty cell needs: the cell, its paragraph and the paragraph's run. */
  val idsPerCell: Int = 3

  /** An empty cell with a paragraph and an empty run, from three fresh ids. */
  def emptyCell(
      ids: Vector[NodeId],
      header: Boolean = false,
      alignment: ColumnAlignment = ColumnAlignment.Default
  ): (TableCellNode, Vector[EditorNode]) =
    val Vector(cell, paragraph, text) = ids.take(idsPerCell): @unchecked
    (
      TableCellNode(cell, Vector(paragraph), header, alignment),
      Vector(ParagraphNode(paragraph, Vector(text)), TextNode(text, ""))
    )

  /** An empty row of `columns` cells, from `1 + columns * 3` fresh ids. */
  def emptyRow(
      ids: Vector[NodeId],
      table: TableNode,
      columns: Int,
      header: Boolean
  ): (TableRowNode, Vector[EditorNode]) =
    val cells = (0 until columns).map { column =>
      val from = 1 + column * idsPerCell
      emptyCell(ids.slice(from, from + idsPerCell), header, table.alignmentOf(column))
    }
    (
      TableRowNode(ids.head, cells.map(_._1.id).toVector),
      cells.toVector.flatMap((cell, below) => cell +: below)
    )
