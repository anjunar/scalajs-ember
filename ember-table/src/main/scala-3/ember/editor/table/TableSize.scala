package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*

/** The size of a new table. The first row is a header row unless `header` says otherwise. */
final case class TableSize(rows: Int, columns: Int, header: Boolean = true)
