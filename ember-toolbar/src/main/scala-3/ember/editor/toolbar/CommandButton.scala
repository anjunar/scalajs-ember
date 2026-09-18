package ember.editor.toolbar

import ember.editor.core.*
import ui.core.layout.Button

/** A typed command is closed over at construction; no string dispatch registry.
  *
  * `icon` is a Material Icons ligature name (e.g. "format_bold"). When set, it takes over the
  * button's rendered content instead of `shortLabel`/`label`; the icon font itself is the host
  * page's responsibility to load (see `EditorToolbar`).
  */
final case class ToolbarAction(
    id: String,
    label: String,
    state: () => CommandState,
    activate: () => Either[EditorError, Unit],
    shortLabel: Option[String] = None,
    icon: Option[String] = None
)

object ToolbarAction:
  def command[A](
      id: String,
      label: String,
      session: EditorSession,
      command: EditorCommand[A],
      payload: A,
      state: () => CommandState
  ): ToolbarAction =
    ToolbarAction(
      id,
      label,
      state,
      () =>
        session
          .dispatch(TransactionMeta.labelled(id).withHistory(HistoryPolicy.Push))(command, payload)
          .flatMap(outcome =>
            if outcome.wasHandled then Right(())
            else Left(ToolbarFailure("Aktion ist nicht verfügbar."))
          )
    )

final class CommandButton(val action: ToolbarAction) extends Button:
  def refresh(): Unit =
    val current = action.state()
    disabled = !current.enabled
    current.pressed match
      case Some(value) => setAttribute("aria-pressed", value)
      case None        => removeAttribute("aria-pressed")
