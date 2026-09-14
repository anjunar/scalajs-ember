package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.browser.*
import org.scalajs.dom
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.{Cursor, DomNodes}
import ui.core.layout.TextComponent
import ui.core.state.{Disposable, Property}

/** Composable toolbar with one Tab stop, arrow navigation and native button activation. */
final class EditorToolbar(
    session: EditorSession,
    selection: SelectionPort,
    actions: Vector[ToolbarAction],
    name: String = "Text bearbeiten"
) extends AbstractComponent:
  val tagName         = "div"
  private val message = Property("")
  private var buttons = Vector.empty[CommandButton]
  private var current = 0

  def announce(text: String): Unit = message.set(text)

  override def compose(cursor: Cursor): Unit =
    setAttribute("role", "toolbar")
    setAttribute("aria-label", name)
    addClass("ember-toolbar")
    buttons = actions.map { action =>
      val button = new CommandButton(action)
      Runtime.mount(button, cursor, Some(this))
      button.buttonType("button")
      button.label(action.label)
      button.setAttribute("data-command", action.id)
      button.onHandler("mousedown") { event =>
        val mouse = event.raw.asInstanceOf[dom.MouseEvent]
        // Only a primary mouse press from the editor preserves its focus. Keyboard and
        // touch activation keep their native focus semantics.
        if mouse.button == 0 && selection.scope.focusWithin then
          selection.importNative(): Unit
          event.preventDefault()
      }
      button.onClickHandler { _ =>
        if selection.scope.focusWithin then selection.importNative(): Unit
        if action.state().enabled && !session.isDisposed then
          action.activate().fold(error => announce(error.message), _ => announce(""))
        refresh()
      }
      button.onHandler("focus") { _ =>
        current = buttons.indexOf(button)
        updateTabStops()
      }
      button.onHandler("keydown") { event =>
        val key = event.raw.asInstanceOf[dom.KeyboardEvent]
        if !key.altKey && !key.ctrlKey && !key.metaKey then
          val enabled = buttons.indices.filter(i => !buttons(i).disabled).toVector
          if enabled.nonEmpty then
            val position    = enabled.indexOf(buttons.indexOf(button))
            val destination = key.key match
              case "ArrowRight" => Some(enabled((position + 1) % enabled.size))
              case "ArrowLeft"  => Some(enabled((position - 1 + enabled.size) % enabled.size))
              case "Home"       => Some(enabled.head)
              case "End"        => Some(enabled.last)
              case _            => None
            destination.foreach { index =>
              event.preventDefault()
              current = index
              updateTabStops()
              DomNodes.option(buttons(index).host).foreach(_.asInstanceOf[dom.HTMLElement].focus())
            }
      }
      button
    }
    val status = new AbstractComponent:
      val tagName                                = "span"
      override def compose(cursor: Cursor): Unit =
        setAttribute("role", "status")
        setAttribute("aria-live", "polite")
        setAttribute("aria-atomic", "true")
        addClass("ember-toolbar-status")
        Runtime.mount(TextComponent.bind(message), cursor, Some(this))
    Runtime.mount(status, cursor, Some(this))
    val listener = session.onCommit(_ => refresh())
    addDisposable(Disposable(listener.dispose()))
    refresh()

  /** Call after application-owned mode/availability changes as well as session commits. */
  def refresh(): Unit =
    buttons.foreach(_.refresh())
    if !buttons.indices.contains(current) || buttons(current).disabled then
      current = buttons.indexWhere(!_.disabled)
    updateTabStops()

  private def updateTabStops(): Unit =
    buttons.zipWithIndex.foreach { (button, index) =>
      button.setAttribute("tabindex", if index == current && !button.disabled then "0" else "-1")
    }
