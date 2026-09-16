package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*

/** The size of a new table. The first row is a header row unless `header` says otherwise. */
final case class TableSize(rows: Int, columns: Int, header: Boolean = true)

enum RowPosition:
  case Above, Below

enum ColumnPosition:
  case Before, After

enum CellDirection:
  case Next, Previous

/** The commands a table contributes. Values, not names (§12). */
object TableCommands:

  /** Inserts a table after the block at the caret, or in place of an empty paragraph. */
  val InsertTable: EditorCommand[TableSize] = EditorCommand.of[TableSize]("table.insert")

  /** A row above the topmost or below the bottommost selected row. */
  val InsertRow: EditorCommand[RowPosition] = EditorCommand.of[RowPosition]("table.insert-row")

  /** A column before the leftmost or after the rightmost selected column. */
  val InsertColumn: EditorCommand[ColumnPosition] =
    EditorCommand.of[ColumnPosition]("table.insert-column")

  /** The selected rows. Deleting every row deletes the table. */
  val DeleteRow: EditorCommand[Unit] = EditorCommand.unit("table.delete-row")

  /** The selected columns. Deleting every column deletes the table. */
  val DeleteColumn: EditorCommand[Unit] = EditorCommand.unit("table.delete-column")

  val DeleteTable: EditorCommand[Unit] = EditorCommand.unit("table.delete")

  /** Makes the first row a header row, or an ordinary one. */
  val ToggleHeaderRow: EditorCommand[Unit] = EditorCommand.unit("table.toggle-header-row")

  /** Aligns the selected columns. */
  val SetColumnAlignment: EditorCommand[ColumnAlignment] =
    EditorCommand.of[ColumnAlignment]("table.set-column-alignment")

  /** Tab and Shift+Tab. `Pass` outside a table, so that list and code indentation still work. */
  val MoveToCell: EditorCommand[CellDirection] =
    EditorCommand.of[CellDirection]("table.move-to-cell")

  /** Selects the rectangle two cells of one table span. */
  val SelectCells: EditorCommand[(NodeId, NodeId)] =
    EditorCommand.of[(NodeId, NodeId)]("table.select-cells")

  /** Turns a cell selection back into a caret. `Pass` when there is none. */
  val CollapseCellSelection: EditorCommand[Unit] = EditorCommand.unit("table.collapse-selection")

/** What the table commands do.
  *
  * ==Everything is an insert, a remove or a replace of whole cells==
  *
  * Cells keep their identity through every command here: a new row is new cells, a deleted column
  * removes cells and renames nothing. A caret or a [[TableSelection]] in a cell that survives
  * therefore survives with it (§11), and undo restores exactly the cells that went.
  *
  * The column facts are written to the '''table''' only. [[TableNormalization]] copies them to the
  * cells in the same transaction, so no command has to remember to.
  */
object TableEditing:

  // -----------------------------------------------------------------------------------------
  // Inserting a table
  // -----------------------------------------------------------------------------------------

  def insertTable(
      scope: TransformScope,
      generator: NodeIdGenerator,
      size: TableSize
  ): CommandResult =
    if size.rows < 1 || size.columns < 1 then CommandResult.Pass
    else
      val document                          = scope.document
      val (container, index, keepsFollower) = placement(document, scope.selection)

      val ids = generator.nextBatch(
        1 + size.rows * (1 + size.columns * Tables.idsPerCell),
        document.contains
      )
      val table = TableNode(
        ids.head,
        Vector.empty,
        size.header,
        Vector.fill(size.columns)(ColumnAlignment.Default)
      )
      val rows = (0 until size.rows).map { row =>
        val from = 1 + row * (1 + size.columns * Tables.idsPerCell)
        Tables.emptyRow(
          ids.slice(from, from + 1 + size.columns * Tables.idsPerCell),
          table,
          size.columns,
          size.header && row == 0
        )
      }
      val built = table.copy(children = rows.map(_._1.id).toVector)

      for
        _ <- scope.insert(
          container,
          index,
          built,
          rows.toVector.flatMap((row, below) => row +: below)
        )
        _ <-
          // A table must not be the last thing in its container: there would be no line after it
          // to put a caret on.
          if keepsFollower || scope.document.childrenOf(container).lastOption != Some(built.id) then
            Right(())
          else
            val Vector(paragraph, text) =
              generator.nextBatch(2, scope.document.contains): @unchecked
            scope.insert(
              container,
              index + 1,
              ParagraphNode(paragraph, Vector(text)),
              Vector(TextNode(text, ""))
            )
        _ <- scope.select(
          RangeSelection.caret(Tables.startOf(scope.document, rows.head._1.children.head))
        )
      yield ()
      CommandResult.Handled

  /** Where a new table goes: into the container of the block at the caret.
    *
    * An empty paragraph is where an author who wants a table usually stands, and it is kept
    * '''behind''' the table rather than removed -- it becomes the line after it. Anywhere else the
    * table goes after the block.
    */
  private def placement(
      document: DocumentRead,
      selection: Option[Selection]
  ): (NodeId, Int, Boolean) =
    val point = selection match
      case Some(range: RangeSelection) => Some(range.focus)
      case Some(cells: TableSelection) => Some(Tables.startOf(document, cells.focus))
      case _                           => None

    val block = point.flatMap { at =>
      (at.owner +: document.ancestorsOf(at.owner)).find { id =>
        id != document.rootId && (document.node(id) match
          case Some(_: InlineElementNode) => false
          case Some(_: TableCellNode)     => false
          case Some(_: ElementNode)       => true
          case _                          => false)
      }
    }

    block.flatMap(id => document.parentOf(id).zip(document.indexOfChild(id)).map((id, _))) match
      case Some((id, (container, index))) =>
        val empty = document.node(id).exists(_.isInstanceOf[ParagraphNode]) &&
          document
            .subtreeOf(id)
            .forall(child =>
              document.node(child) match
                case Some(text: TextNode) => text.text.isEmpty
                case Some(_: ElementNode) => true
                case _                    => false
            )
        if empty then (container, index, true) else (container, index + 1, false)
      case None =>
        (document.rootId, document.childrenOf(document.rootId).length, false)

  // -----------------------------------------------------------------------------------------
  // Rows and columns
  // -----------------------------------------------------------------------------------------

  def insertRow(
      scope: TransformScope,
      generator: NodeIdGenerator,
      position: RowPosition
  ): CommandResult =
    Tables.coveredAt(scope.document, scope.selection) match
      case None                     => CommandResult.Pass
      case Some((context, rows, _)) =>
        val grid    = context.grid
        val at      = if position == RowPosition.Above then rows.start else rows.end + 1
        val columns = math.max(1, grid.columns)
        val ids     = generator.nextBatch(1 + columns * Tables.idsPerCell, scope.document.contains)
        val (row, below) = Tables.emptyRow(ids, grid.table, columns, header = false)
        for
          _ <- scope.insert(grid.table.id, at, row, below)
          _ <- scope.select(
            RangeSelection.caret(
              Tables.startOf(scope.document, row.children(math.min(context.column, columns - 1)))
            )
          )
        yield ()
        CommandResult.Handled

  def insertColumn(
      scope: TransformScope,
      generator: NodeIdGenerator,
      position: ColumnPosition
  ): CommandResult =
    Tables.coveredAt(scope.document, scope.selection) match
      case None                        => CommandResult.Pass
      case Some((context, _, columns)) =>
        val grid    = context.grid
        val at      = if position == ColumnPosition.Before then columns.start else columns.end + 1
        var created = Vector.empty[NodeId]

        grid.rows.zip(grid.cells).foreach { (row, cells) =>
          val ids           = generator.nextBatch(Tables.idsPerCell, scope.document.contains)
          val (cell, below) = Tables.emptyCell(ids)
          scope.insert(row.id, math.min(at, cells.length), cell, below): Unit
          created = created :+ cell.id
        }

        val alignments    = grid.table.alignments.padTo(grid.columns, ColumnAlignment.Default)
        val (left, right) = alignments.splitAt(math.min(at, alignments.length))
        scope.document.node(grid.table.id).collect { case table: TableNode => table }.foreach {
          table =>
            scope.replace(
              table.id,
              table.copy(alignments = (left :+ ColumnAlignment.Default) ++ right)
            ): Unit
        }

        created.lift(context.row).foreach { cell =>
          scope.select(RangeSelection.caret(Tables.startOf(scope.document, cell))): Unit
        }
        CommandResult.Handled

  def deleteRow(scope: TransformScope, generator: NodeIdGenerator): CommandResult =
    Tables.coveredAt(scope.document, scope.selection) match
      case None                     => CommandResult.Pass
      case Some((context, rows, _)) =>
        val grid = context.grid
        if rows.length >= grid.rowCount then deleteTable(scope, generator)
        else
          rows.reverse.foreach(row => scope.remove(grid.rows(row).id): Unit)
          val remaining = Tables.grid(scope.document, grid.table.id)
          remaining
            .flatMap { fresh =>
              fresh
                .cellAt(math.min(rows.start, fresh.rowCount - 1), context.column)
                .orElse(fresh.cellAt(math.min(rows.start, fresh.rowCount - 1), 0))
            }
            .foreach { cell =>
              scope.select(RangeSelection.caret(Tables.startOf(scope.document, cell.id))): Unit
            }
          CommandResult.Handled

  def deleteColumn(scope: TransformScope, generator: NodeIdGenerator): CommandResult =
    Tables.coveredAt(scope.document, scope.selection) match
      case None                        => CommandResult.Pass
      case Some((context, _, columns)) =>
        val grid = context.grid
        if columns.length >= grid.columns then deleteTable(scope, generator)
        else
          grid.cells.foreach { cells =>
            columns.reverse.foreach(column =>
              cells.lift(column).foreach(cell => scope.remove(cell.id): Unit)
            )
          }
          scope.document.node(grid.table.id).collect { case table: TableNode => table }.foreach {
            table =>
              val kept = table.alignments.zipWithIndex.collect {
                case (alignment, column) if !columns.contains(column) => alignment
              }
              scope.replace(table.id, table.copy(alignments = kept)): Unit
          }
          Tables
            .grid(scope.document, grid.table.id)
            .flatMap { fresh =>
              fresh.cellAt(context.row, math.min(columns.start, fresh.columns - 1))
            }
            .foreach { cell =>
              scope.select(RangeSelection.caret(Tables.startOf(scope.document, cell.id))): Unit
            }
          CommandResult.Handled

  /** Removes the table and puts the caret where it stood.
    *
    * The caret goes to the start of what follows, or the end of what came before. When the table
    * was the only thing in its container, an empty paragraph takes its place -- a document, a quote
    * or a list item with nothing in it has nowhere for a caret.
    */
  def deleteTable(scope: TransformScope, generator: NodeIdGenerator): CommandResult =
    Tables.contextAt(scope.document, scope.selection) match
      case None          => CommandResult.Pass
      case Some(context) =>
        val document = scope.document
        val table    = context.grid.table.id
        (document.parentOf(table), document.indexOfChild(table)) match
          case (Some(container), Some(index)) =>
            val siblings = document.childrenOf(container)
            val caret    = siblings
              .lift(index + 1)
              .map(Tables.startOf(document, _))
              .orElse(siblings.lift(index - 1).map(Tables.endOf(document, _)))

            for
              _ <- scope.remove(table)
              _ <-
                if caret.isDefined then scope.select(RangeSelection.caret(caret.get))
                else
                  val Vector(paragraph, text) =
                    generator.nextBatch(2, scope.document.contains): @unchecked
                  for
                    _ <- scope.insert(
                      container,
                      index,
                      ParagraphNode(paragraph, Vector(text)),
                      Vector(TextNode(text, ""))
                    )
                    _ <- scope.select(RangeSelection.caret(Point.textBefore(text, 0)))
                  yield ()
            yield ()
            CommandResult.Handled
          case _ => CommandResult.Pass

  def toggleHeaderRow(scope: TransformScope): CommandResult =
    Tables.contextAt(scope.document, scope.selection) match
      case None          => CommandResult.Pass
      case Some(context) =>
        scope.replace(
          context.grid.table.id,
          context.grid.table.copy(header = !context.grid.table.header)
        ): Unit
        CommandResult.Handled

  def setColumnAlignment(scope: TransformScope, alignment: ColumnAlignment): CommandResult =
    Tables.coveredAt(scope.document, scope.selection) match
      case None                        => CommandResult.Pass
      case Some((context, _, columns)) =>
        val table   = context.grid.table
        val current = table.alignments.padTo(context.grid.columns, ColumnAlignment.Default)
        val updated = current.zipWithIndex.map((value, column) =>
          if columns.contains(column) then alignment else value
        )
        scope.replace(table.id, table.copy(alignments = updated)): Unit
        CommandResult.Handled

  // -----------------------------------------------------------------------------------------
  // Navigation and cell selection
  // -----------------------------------------------------------------------------------------

  /** Tab moves to the end of the next cell, and from the last cell into a new row.
    *
    * The end and not the start: after Tab an author usually continues what the cell already says,
    * and a caret at the start would type in front of it. The new row is the convention every
    * spreadsheet and word processor shares; it is what makes Tab enough to fill a table.
    */
  def moveToCell(
      scope: TransformScope,
      generator: NodeIdGenerator,
      direction: CellDirection
  ): CommandResult =
    Tables.contextAt(scope.document, scope.selection) match
      case None          => CommandResult.Pass
      case Some(context) =>
        val ordered = context.grid.ordered.map(_.id)
        val index   = ordered.indexOf(context.cell)
        direction match
          case CellDirection.Next if index + 1 < ordered.length =>
            scope.select(
              RangeSelection.caret(Tables.endOf(scope.document, ordered(index + 1)))
            ): Unit
          case CellDirection.Next =>
            scope.select(RangeSelection.caret(Tables.startOf(scope.document, context.cell))): Unit
            insertRow(scope, generator, RowPosition.Below): Unit
            Tables
              .grid(scope.document, context.grid.table.id)
              .flatMap(_.cellAt(context.grid.rowCount, 0))
              .foreach { cell =>
                scope.select(RangeSelection.caret(Tables.startOf(scope.document, cell.id))): Unit
              }
          case CellDirection.Previous if index > 0 =>
            scope.select(
              RangeSelection.caret(Tables.endOf(scope.document, ordered(index - 1)))
            ): Unit
          case CellDirection.Previous => ()
        CommandResult.Handled

  def selectCells(scope: TransformScope, anchor: NodeId, focus: NodeId): CommandResult =
    val document = scope.document
    (Tables.tableOfCell(document, anchor), Tables.tableOfCell(document, focus)) match
      case (Some(table), Some(other)) if table == other =>
        scope.select(TableSelection(table, anchor, focus)): Unit
        CommandResult.Handled
      case _ => CommandResult.Pass

  def collapseCellSelection(scope: TransformScope): CommandResult =
    scope.selection match
      case Some(cells: TableSelection) =>
        scope.select(RangeSelection.caret(Tables.endOf(scope.document, cells.focus))): Unit
        CommandResult.Handled
      case _ => CommandResult.Pass

  // -----------------------------------------------------------------------------------------
  // Editing over a cell selection
  // -----------------------------------------------------------------------------------------

  /** Backspace, Delete, typing and Enter over a [[TableSelection]]: the cells are emptied.
    *
    * These sit above the rich-text handlers (§12) and step aside for every other selection. A cell
    * selection is a request to clear content; the rich-text handlers would not even know which text
    * it covers.
    */
  def clearSelectedCells(scope: TransformScope, generator: NodeIdGenerator): Option[NodeId] =
    scope.selection match
      case Some(cells: TableSelection) =>
        val document = scope.document
        val selected = cells.cells(document)
        selected.foreach { cell =>
          document.childrenOf(cell).reverse.foreach(child => scope.remove(child): Unit)
          val Vector(paragraph, text) = generator.nextBatch(2, scope.document.contains): @unchecked
          scope.insert(
            cell,
            0,
            ParagraphNode(paragraph, Vector(text)),
            Vector(TextNode(text, ""))
          ): Unit
        }
        val caretCell = if selected.contains(cells.anchor) then cells.anchor
        else selected.headOption.getOrElse(cells.anchor)
        scope.select(RangeSelection.caret(Tables.startOf(scope.document, caretCell))): Unit
        Some(caretCell)
      case _ => None

  def deleteOverCells(scope: TransformScope, generator: NodeIdGenerator): CommandResult =
    clearSelectedCells(scope, generator).fold(CommandResult.Pass)(_ => CommandResult.Handled)

  def typeOverCells(
      scope: TransformScope,
      generator: NodeIdGenerator,
      text: String
  ): CommandResult =
    clearSelectedCells(scope, generator) match
      case None    => CommandResult.Pass
      case Some(_) =>
        TextEditing.insertText(scope, generator, text): Unit
        CommandResult.Handled
