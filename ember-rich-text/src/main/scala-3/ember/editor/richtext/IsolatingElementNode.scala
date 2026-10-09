package ember.editor.richtext

import ember.editor.core.*

/** A container whose boundary text editing never crosses: a table cell.
  *
  * ==Why the rich-text profile needs to know==
  *
  * Backspace at the start of a block joins it with the block before, and a range deletion joins the
  * block where it starts with the block where it ends. Both are right between two paragraphs and
  * wrong between two table cells: joining would move a cell's text into its neighbour and leave an
  * empty cell behind -- or none at all, once the empty parents are pruned.
  *
  * The profile cannot name the table module (§6 puts tables above rich text), so the table module
  * marks its nodes instead, the same way a link marks itself as an [[InlineElementNode]]. Editing
  * then asks for the marker and never for the type.
  */
trait IsolatingElementNode extends ElementNode
