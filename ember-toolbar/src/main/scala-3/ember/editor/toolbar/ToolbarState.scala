package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.richtext.*

final case class CommandState(enabled: Boolean, pressed: Option[String] = None)

/** Queries the same stored marks and text runs as the formatting commands. */
object ToolbarState:
  def mark(state: EditorState, mark: TextMark, editable: Boolean): CommandState =
    state.selection match
      case Some(range: RangeSelection) =>
        val values = if range.isCollapsed then
          Vector(TypingMarks.marksFor(state.fields(TypingMarks), state.document, Some(range.focus)))
        else RangeFormatting.runsIn(state.document, range).map(_.marks)
        val count   = values.count(_.contains(mark.markId))
        val pressed =
          if count == 0 then "false" else if count == values.size then "true" else "mixed"
        CommandState(editable && values.nonEmpty, Some(pressed))
      case _ => CommandState(false, Some("false"))

final case class ToolbarFailure(message: String) extends EditorError
