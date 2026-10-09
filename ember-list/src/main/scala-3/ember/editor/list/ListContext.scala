package ember.editor.list

import ember.editor.core.*
import ember.editor.richtext.*

/** Where the caret is, in list terms.
  *
  * Every list command starts by asking this, and none of them can do anything useful without the
  * whole answer: the item, the list it is in, and where in that list it sits. Computing it once, in
  * one place, is the difference between five commands that agree and five that drift.
  */
private[list] final case class ListContext(
    block: NodeId,
    item: NodeId,
    list: NodeId,
    kind: ListKind,
    indexInList: Int
)
