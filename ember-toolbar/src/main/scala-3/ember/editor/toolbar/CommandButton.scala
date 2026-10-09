package ember.editor.toolbar

import ember.editor.core.*
import ui.core.layout.Button
final class CommandButton(val action: ToolbarAction) extends Button:
  def refresh(): Unit =
    val current = action.state()
    disabled = !current.enabled
    current.pressed match
      case Some(value) => setAttribute("aria-pressed", value)
      case None        => removeAttribute("aria-pressed")
