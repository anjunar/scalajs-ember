package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*

/** Where a caret or a cell selection stands, in table terms. */
final case class CellContext(grid: TableGrid, cell: NodeId, row: Int, column: Int)
