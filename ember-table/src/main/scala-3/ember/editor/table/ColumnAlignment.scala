package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*

/** How a column's content is aligned. GFM's four states; `Default` writes no marker at all. */
enum ColumnAlignment:
  case Default, Left, Center, Right
