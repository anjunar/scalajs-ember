package ember.editor.markdown

import scala.collection.mutable

/** One node of the inline result while it is being assembled.
  *
  * The reference mutates a linked tree in place: emphasis processing moves a run of siblings
  * '''into''' a new node, and bracket processing does the same. Building the immutable tree
  * directly would mean rebuilding a vector for every delimiter pair.
  */
private[markdown] final class InlineSlot(
    var kind: SlotKind,
    var start: Int,
    var end: Int
):
  val children: mutable.ArrayBuffer[InlineSlot] = mutable.ArrayBuffer.empty
  var text: PendingText | Null                  = null
  var removed                                   = false
