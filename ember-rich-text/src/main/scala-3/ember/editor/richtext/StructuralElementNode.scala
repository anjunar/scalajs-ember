package ember.editor.richtext

import ember.editor.core.*

/** A container whose children are fixed structure rather than content: a table and its rows.
  *
  * A range deletion that covers part of such a structure '''clears''' the isolated regions inside
  * it instead of removing them. Removing a row because a selection passed through it would change
  * the table's shape, and a selection is a request to delete text, not columns.
  *
  * A structure that lies '''entirely''' inside a range still goes as a whole -- that is a table the
  * author selected, not one a selection merely touched.
  */
trait StructuralElementNode extends ElementNode
