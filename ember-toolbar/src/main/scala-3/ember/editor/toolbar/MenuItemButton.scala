package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.browser.*
import org.scalajs.dom
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.{Cursor, DomNodes}
import ui.core.layout.{Button, TextComponent}
import ui.core.state.{Disposable, Property}
import scala.scalajs.js

/** Deliberately a plain [[AbstractComponent]], not [[Button]]: unlike `Button`'s single managed
  * `label`, a menu item mounts an icon *and* text, and both must be mounted from inside this
  * component's own `compose` -- exactly like the ribbon's group caption does
  * (`EditorToolbar.scala`) -- rather than from the outside after `Runtime.mount` has already
  * returned, which does not register them as this component's children at all.
  */
private final class MenuItemButton(val action: ToolbarAction) extends AbstractComponent:
  val tagName           = "button"
  var disabled: Boolean = false

  override def compose(cursor: Cursor): Unit =
    setAttribute("type", "button")
    addClass("ember-menu__item")
    action.icon.foreach { iconName =>
      val icon = new AbstractComponent:
        val tagName                                = "span"
        override def compose(cursor: Cursor): Unit =
          addClass("material-icons")
          addClass("ember-menu__icon")
          setAttribute("aria-hidden", "true")
          Runtime.mount(new TextComponent(iconName), cursor, Some(this))
      Runtime.mount(icon, cursor, Some(this))
    }
    Runtime.mount(new TextComponent(action.label), cursor, Some(this))

  def refresh(): Unit =
    val current = action.state()
    disabled = !current.enabled
    if disabled then setAttribute("disabled", "") else removeAttribute("disabled")
    setAttribute("aria-disabled", disabled.toString)
    current.pressed match
      case Some(value) =>
        setAttribute("role", "menuitemcheckbox")
        setAttribute("aria-checked", value)
      case None =>
        setAttribute("role", "menuitem")
        removeAttribute("aria-checked")
