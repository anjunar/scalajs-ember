package ember.editor.code

import ember.editor.core.*
import ember.editor.richtext.*

/** The commands a code block contributes. */
object CodeCommands:

  /** Turns the block at the caret into a code block, or back into paragraphs. */
  val ToggleCodeBlock: EditorCommand[CodeInfo] = EditorCommand.of[CodeInfo]("code.toggle")

  /** Sets the info string of the code block at the caret. */
  val SetCodeInfo: EditorCommand[CodeInfo] = EditorCommand.of[CodeInfo]("code.set-info")

  /** Indents the line at the caret by one unit. */
  val IndentLine: EditorCommand[Unit] = EditorCommand.unit("code.indent-line")

  /** Removes one unit of indentation from the line at the caret. */
  val OutdentLine: EditorCommand[Unit] = EditorCommand.unit("code.outdent-line")
