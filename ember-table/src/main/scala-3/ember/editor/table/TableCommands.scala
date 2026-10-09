package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*

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
