package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.link.*
import ember.editor.image.*
import ember.editor.clipboard.ClipboardCommands

/** Opaque, session-owned, one-use dialog target. */
final class DialogTarget private[toolbar] (
    private[toolbar] val owner: AnyRef,
    private[toolbar] val generation: Long,
    val range: RangeSelection,
    val revision: Revision,
    val image: Option[ImageNode]
):
  private[toolbar] var applied = false
