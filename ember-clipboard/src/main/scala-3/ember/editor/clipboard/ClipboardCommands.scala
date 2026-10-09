package ember.editor.clipboard

import ember.editor.core.*
import ember.editor.richtext.*

object ClipboardCommands:
  val Paste: EditorCommand[DocumentFragment] = EditorCommand.of("clipboard.paste")
  val DeleteSelection: EditorCommand[Unit]   = EditorCommand.unit("clipboard.delete-selection")
  val Move: EditorCommand[FragmentMove]      = EditorCommand.of("clipboard.move")
  def meta(label: String): TransactionMeta   =
    TransactionMeta.labelled(label).withHistory(HistoryPolicy.Push)
