package ember.editor.list

import ember.editor.core.*
import ember.editor.richtext.*

/** The commands a list contributes. Values, not names (§12). */
object ListCommands:

  /** Makes the block at the caret a list of this kind, or unwraps it if it already is one. */
  val ToggleList: EditorCommand[ListKind] = EditorCommand.of[ListKind]("list.toggle")

  /** Moves the item at the caret one level deeper. */
  val Indent: EditorCommand[Unit] = EditorCommand.unit("list.indent")

  /** Moves the item at the caret one level out, or out of the list entirely. */
  val Outdent: EditorCommand[Unit] = EditorCommand.unit("list.outdent")
