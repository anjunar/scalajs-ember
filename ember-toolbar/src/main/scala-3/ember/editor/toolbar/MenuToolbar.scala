package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.browser.*
import org.scalajs.dom
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.{Cursor, DomNodes}
import ui.core.layout.{Button, TextComponent}
import ui.core.state.{Disposable, Property}
import scala.scalajs.js

/** A classic dropdown menu bar: one trigger per [[ToolbarGroup]], each opening a menu listing that
  * group's actions by their full label (and icon, where set). Exactly one menu is open at a time --
  * the WAI-ARIA menu bar pattern: arrow keys move between triggers, arrow keys move within an open
  * menu, Escape and an outside click close it, and Tab always closes it rather than trapping focus
  * inside (§22's "no keyboard trap", the same rule the ribbon's table Tab handling already
  * follows).
  */
final class MenuToolbar(
    session: EditorSession,
    selection: SelectionPort,
    groups: Vector[ToolbarGroup],
    name: String = "Menü"
) extends AbstractComponent:
  val tagName         = "div"
  private val message = Property("")

  private var triggers    = Vector.empty[TriggerButton]
  private var dropdowns   = Vector.empty[AbstractComponent]
  private var items       = Vector.empty[Vector[MenuItemButton]]
  private var current     = 0  // roving tabindex among triggers
  private var openGroup   = -1 // which dropdown is open, -1 = none
  private var currentItem = 0  // roving tabindex within the open dropdown

  def announce(text: String): Unit = message.set(text)

  private def open(groupIndex: Int): Unit =
    if openGroup != groupIndex then
      openGroup = groupIndex
      dropdowns.zipWithIndex.foreach { (dropdown, index) =>
        if index == groupIndex then dropdown.removeAttribute("hidden")
        else dropdown.setAttribute("hidden", "")
      }
      triggers.zipWithIndex.foreach { (trigger, index) =>
        trigger.setAttribute("aria-expanded", (index == groupIndex).toString)
      }
      focusFirstItem(groupIndex)

  private def focusFirstItem(groupIndex: Int): Unit =
    currentItem = items(groupIndex).indices.find(i => !items(groupIndex)(i).disabled).getOrElse(0)
    updateItemTabStops()
    DomNodes
      .option(items(groupIndex)(currentItem).host)
      .foreach(_.asInstanceOf[dom.HTMLElement].focus())

  private def focusLastItem(groupIndex: Int): Unit =
    currentItem =
      items(groupIndex).indices.reverse.find(i => !items(groupIndex)(i).disabled).getOrElse(0)
    updateItemTabStops()
    DomNodes
      .option(items(groupIndex)(currentItem).host)
      .foreach(_.asInstanceOf[dom.HTMLElement].focus())

  private def close(returnFocus: Boolean): Unit =
    if openGroup >= 0 then
      val closedGroup = openGroup
      dropdowns.foreach(_.setAttribute("hidden", ""))
      triggers.foreach(_.setAttribute("aria-expanded", "false"))
      openGroup = -1
      if returnFocus then
        DomNodes.option(triggers(closedGroup).host).foreach(_.asInstanceOf[dom.HTMLElement].focus())

  private def toggle(groupIndex: Int): Unit =
    if openGroup == groupIndex then close(returnFocus = false) else open(groupIndex)

  private def updateTriggerTabStops(): Unit =
    triggers.zipWithIndex.foreach { (trigger, index) =>
      trigger.setAttribute("tabindex", if index == current && !trigger.disabled then "0" else "-1")
    }

  private def updateItemTabStops(): Unit =
    if openGroup >= 0 then
      items(openGroup).zipWithIndex.foreach { (item, index) =>
        item.setAttribute("tabindex", if index == currentItem && !item.disabled then "0" else "-1")
      }

  override def compose(cursor: Cursor): Unit =
    setAttribute("role", "menubar")
    setAttribute("aria-label", name)
    addClass("ember-toolbar")
    addClass("ember-toolbar--menu")

    val visibleGroups = groups.filter(_.actions.nonEmpty)
    visibleGroups.zipWithIndex.foreach { (group, groupIndex) =>
      val menu = new AbstractComponent:
        val tagName                                = "div"
        override def compose(cursor: Cursor): Unit =
          addClass("ember-menu")

          val trigger = new TriggerButton(group)
          Runtime.mount(trigger, cursor, Some(this))
          trigger.buttonType("button")
          trigger.label(group.label)
          trigger.setAttribute("role", "menuitem")
          trigger.setAttribute("aria-haspopup", "true")
          trigger.setAttribute("aria-expanded", "false")
          trigger.onClickHandler { _ => toggle(groupIndex) }
          trigger.onHandler("focus") { _ =>
            current = triggers.indexOf(trigger)
            updateTriggerTabStops()
          }
          trigger.onHandler("keydown") { event =>
            val key = event.raw.asInstanceOf[dom.KeyboardEvent]
            if !key.altKey && !key.ctrlKey && !key.metaKey then
              val enabled = triggers.indices.filter(i => !triggers(i).disabled).toVector
              if enabled.nonEmpty then
                val position = enabled.indexOf(triggers.indexOf(trigger))
                key.key match
                  case "ArrowRight" =>
                    key.preventDefault()
                    val index = enabled((position + 1) % enabled.size)
                    current = index
                    updateTriggerTabStops()
                    DomNodes
                      .option(triggers(index).host)
                      .foreach(_.asInstanceOf[dom.HTMLElement].focus())
                  case "ArrowLeft" =>
                    key.preventDefault()
                    val index = enabled((position - 1 + enabled.size) % enabled.size)
                    current = index
                    updateTriggerTabStops()
                    DomNodes
                      .option(triggers(index).host)
                      .foreach(_.asInstanceOf[dom.HTMLElement].focus())
                  case "Home" =>
                    key.preventDefault()
                    current = enabled.head
                    updateTriggerTabStops()
                    DomNodes
                      .option(triggers(enabled.head).host)
                      .foreach(_.asInstanceOf[dom.HTMLElement].focus())
                  case "End" =>
                    key.preventDefault()
                    current = enabled.last
                    updateTriggerTabStops()
                    DomNodes
                      .option(triggers(enabled.last).host)
                      .foreach(_.asInstanceOf[dom.HTMLElement].focus())
                  case "ArrowDown" =>
                    key.preventDefault()
                    open(groupIndex)
                  case "ArrowUp" =>
                    key.preventDefault()
                    open(groupIndex)
                    focusLastItem(groupIndex)
                  case _ => ()
          }
          triggers :+= trigger

          val dropdown = new AbstractComponent:
            val tagName                                = "div"
            override def compose(cursor: Cursor): Unit =
              addClass("ember-menu__dropdown")
              setAttribute("role", "menu")
              setAttribute("aria-label", group.label)
              setAttribute("hidden", "")
              var groupItems = Vector.empty[MenuItemButton]
              group.actions.foreach { action =>
                val item = new MenuItemButton(action)
                Runtime.mount(item, cursor, Some(this))
                item.setAttribute("title", action.label)
                item.setAttribute("data-command", action.id)
                item.onHandler("mousedown") { event =>
                  val mouse = event.raw.asInstanceOf[dom.MouseEvent]
                  if mouse.button == 0 && selection.scope.focusWithin then
                    selection.importNative(): Unit
                    event.preventDefault()
                }
                item.onHandler("click") { _ =>
                  if selection.scope.focusWithin then selection.importNative(): Unit
                  if action.state().enabled && !session.isDisposed then
                    action.activate().fold(error => announce(error.message), _ => announce(""))
                  refresh()
                  close(returnFocus = true)
                }
                item.onHandler("keydown") { event =>
                  val key = event.raw.asInstanceOf[dom.KeyboardEvent]
                  if key.key == "Tab" then
                    // §22: a menu never traps Tab. It closes and lets focus continue natively.
                    close(returnFocus = false)
                  else if key.key == "Escape" then
                    key.preventDefault()
                    close(returnFocus = true)
                  else if !key.altKey && !key.ctrlKey && !key.metaKey then
                    val groupItems = items(groupIndex)
                    val enabled = groupItems.indices.filter(i => !groupItems(i).disabled).toVector
                    if enabled.nonEmpty then
                      val position = enabled.indexOf(groupItems.indexOf(item))
                      key.key match
                        case "ArrowDown" =>
                          key.preventDefault()
                          currentItem = enabled((position + 1) % enabled.size)
                          updateItemTabStops()
                          DomNodes
                            .option(groupItems(currentItem).host)
                            .foreach(_.asInstanceOf[dom.HTMLElement].focus())
                        case "ArrowUp" =>
                          key.preventDefault()
                          currentItem = enabled((position - 1 + enabled.size) % enabled.size)
                          updateItemTabStops()
                          DomNodes
                            .option(groupItems(currentItem).host)
                            .foreach(_.asInstanceOf[dom.HTMLElement].focus())
                        case "Home" =>
                          key.preventDefault()
                          currentItem = enabled.head
                          updateItemTabStops()
                          DomNodes
                            .option(groupItems(currentItem).host)
                            .foreach(_.asInstanceOf[dom.HTMLElement].focus())
                        case "End" =>
                          key.preventDefault()
                          currentItem = enabled.last
                          updateItemTabStops()
                          DomNodes
                            .option(groupItems(currentItem).host)
                            .foreach(_.asInstanceOf[dom.HTMLElement].focus())
                        case _ => ()
                }
                groupItems :+= item
              }
              items :+= groupItems
          Runtime.mount(dropdown, cursor, Some(this))
          dropdowns :+= dropdown
      Runtime.mount(menu, cursor, Some(this))
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

    if cursor.isBrowser then
      val onDocumentMouseDown: js.Function1[dom.Event, Unit] = event =>
        if openGroup >= 0 then
          val target = event.asInstanceOf[dom.MouseEvent].target
          val inside = target != null &&
            DomNodes
              .option(this.host)
              .exists(_.asInstanceOf[dom.Node].contains(target.asInstanceOf[dom.Node]))
          if !inside then close(returnFocus = false)
      dom.document.addEventListener("mousedown", onDocumentMouseDown)
      addDisposable(Disposable(dom.document.removeEventListener("mousedown", onDocumentMouseDown)))

    val listener = session.onCommit(_ => refresh())
    addDisposable(Disposable(listener.dispose()))
    updateTriggerTabStops()
    refresh()

  /** Call after application-owned mode/availability changes as well as session commits. */
  def refresh(): Unit =
    triggers.foreach(_.refresh())
    items.foreach(_.foreach(_.refresh()))
    if !triggers.indices.contains(current) || triggers(current).disabled then
      current = triggers.indices.find(i => !triggers(i).disabled).getOrElse(-1)
    updateTriggerTabStops()
    if openGroup >= 0 then
      val groupItems = items(openGroup)
      if !groupItems.indices.contains(currentItem) || groupItems(currentItem).disabled then
        currentItem = groupItems.indices.find(i => !groupItems(i).disabled).getOrElse(-1)
      updateItemTabStops()

private final class TriggerButton(val group: ToolbarGroup) extends Button:
  def refresh(): Unit = disabled = !group.actions.exists(_.state().enabled)

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
