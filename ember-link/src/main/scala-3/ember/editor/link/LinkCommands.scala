package ember.editor.link

import ember.editor.core.*
import ember.editor.richtext.*

/** The commands a link contributes. Values, not names (§12). */
object LinkCommands:

  /** Links the selection, or retargets the link the caret stands in. */
  val SetLink: EditorCommand[LinkTarget] = EditorCommand.of[LinkTarget]("link.set")

  /** Takes the link away and keeps its content. */
  val RemoveLink: EditorCommand[Unit] = EditorCommand.unit("link.remove")
