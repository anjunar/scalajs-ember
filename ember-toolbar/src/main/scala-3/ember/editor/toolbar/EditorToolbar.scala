package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.browser.*
import org.scalajs.dom
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.{Cursor, DomNodes}
import ui.core.layout.TextComponent
import ui.core.state.{Disposable, Property}
import scala.scalajs.js

/** A labelled ribbon group; actions still share the toolbar's single roving Tab stop. */
final case class ToolbarGroup(label: String, actions: Vector[ToolbarAction])

/** Composable toolbar with one Tab stop, arrow navigation and native button activation.
  *
  * With `groups` set, this renders a ribbon: each group becomes a labelled section with its own
  * disclosure caption. Below `RibbonMobileQuery`, sections collapse and exactly one stays open at a
  * time -- above it, every section stays visibly open and the caption is inert. The mobile
  * threshold and the open/closed state live entirely in this component; the host page only needs to
  * give it room to lay out in.
  */
final class EditorToolbar(
    session: EditorSession,
    selection: SelectionPort,
    actions: Vector[ToolbarAction],
    name: String = "Text bearbeiten",
    groups: Vector[ToolbarGroup] = Vector.empty,
    // Lets a caller distinguish an instance for its own CSS, e.g. FloatingToolbar's positioning.
    extraClasses: Vector[String] = Vector.empty
) extends AbstractComponent:
  import EditorToolbar.RibbonMobileQuery
  val tagName         = "div"
  private val message = Property("")
  private var buttons = Vector.empty[CommandButton]
  private var current = 0
  // Parallel to `buttons`: the owning ribbon group's index, or -1 outside ribbon mode.
  private var buttonGroup     = Vector.empty[Int]
  private var ribbonSections  = Vector.empty[(AbstractComponent, AbstractComponent)]
  private var ribbonMobile    = false
  private var ribbonOpenGroup = 0

  def announce(text: String): Unit = message.set(text)

  private def isVisible(index: Int): Boolean =
    buttonGroup.lift(index) match
      case Some(group) if group >= 0 => !ribbonMobile || group == ribbonOpenGroup
      case _                         => true

  private def applyRibbonCollapse(): Unit =
    ribbonSections.zipWithIndex.foreach { case ((caption, controls), index) =>
      val expanded = !ribbonMobile || index == ribbonOpenGroup
      if expanded then controls.removeAttribute("hidden") else controls.setAttribute("hidden", "")
      caption.setAttribute("aria-expanded", expanded.toString)
    }

  override def compose(cursor: Cursor): Unit =
    setAttribute("role", "toolbar")
    setAttribute("aria-label", name)
    addClass("ember-toolbar")
    extraClasses.foreach(addClass)
    def mountButtons(
        items: Vector[ToolbarAction],
        cursor: Cursor,
        owner: AbstractComponent,
        groupIndex: Int = -1
    ): Unit =
      items.foreach { action =>
        val button = new CommandButton(action)
        Runtime.mount(button, cursor, Some(owner))
        button.buttonType("button")
        action.icon match
          case Some(iconName) =>
            button.label(iconName)
            button.addClass("material-icons")
            button.addClass("ember-toolbar__icon")
          case None =>
            button.label(action.shortLabel.getOrElse(action.label))
        button.setAttribute("aria-label", action.label)
        button.setAttribute("title", action.label)
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
            val enabled = buttons.indices.filter(i => !buttons(i).disabled && isVisible(i)).toVector
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
                DomNodes
                  .option(buttons(index).host)
                  .foreach(_.asInstanceOf[dom.HTMLElement].focus())
              }
        }
        buttons :+= button
        buttonGroup :+= groupIndex
      }
    if groups.isEmpty then mountButtons(actions, cursor, this)
    else
      addClass("ember-toolbar--ribbon")
      val visibleGroups = groups.filter(_.actions.nonEmpty)
      visibleGroups.zipWithIndex.foreach { (group, groupIndex) =>
        val section = new AbstractComponent:
          val tagName                                = "div"
          override def compose(cursor: Cursor): Unit =
            addClass("ember-ribbon-group")
            setAttribute("role", "group")
            setAttribute("aria-label", group.label)
            val caption = new AbstractComponent:
              val tagName                                = "button"
              override def compose(cursor: Cursor): Unit =
                addClass("ember-ribbon-group__caption")
                setAttribute("type", "button")
                setAttribute("aria-expanded", "true")
                Runtime.mount(new TextComponent(group.label), cursor, Some(this))
                val chevron = new AbstractComponent:
                  val tagName                                = "span"
                  override def compose(cursor: Cursor): Unit =
                    addClass("material-icons")
                    addClass("ember-ribbon-group__chevron")
                    setAttribute("aria-hidden", "true")
                    Runtime.mount(new TextComponent("expand_more"), cursor, Some(this))
                Runtime.mount(chevron, cursor, Some(this))
                // Only a live disclosure below RibbonMobileQuery; a no-op above it, where every
                // section stays open (see `applyRibbonCollapse`).
                onHandler("click") { _ =>
                  if ribbonMobile && ribbonOpenGroup != groupIndex then
                    ribbonOpenGroup = groupIndex
                    applyRibbonCollapse()
                }
            Runtime.mount(caption, cursor, Some(this))
            val controls = new AbstractComponent:
              val tagName                                = "div"
              override def compose(cursor: Cursor): Unit =
                addClass("ember-ribbon-group__controls")
                mountButtons(group.actions, cursor, this, groupIndex)
            Runtime.mount(controls, cursor, Some(this))
            ribbonSections :+= (caption -> controls)
        Runtime.mount(section, cursor, Some(this))
      }
      // SSR has no window/matchMedia; it renders the desktop-open markup, and a real browser
      // corrects it to the actual viewport once mounted (during hydration or a fresh mount).
      if cursor.isBrowser then
        val media = dom.window.matchMedia(RibbonMobileQuery)
        ribbonMobile = media.matches
        val onRibbonMediaChange: js.Function1[dom.Event, Unit] = _ =>
          ribbonMobile = media.matches
          applyRibbonCollapse()
        media.addEventListener("change", onRibbonMediaChange)
        addDisposable(Disposable(media.removeEventListener("change", onRibbonMediaChange)))
      applyRibbonCollapse()
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
    if !buttons.indices.contains(current) || buttons(current).disabled || !isVisible(current) then
      current = buttons.indices.find(i => !buttons(i).disabled && isVisible(i)).getOrElse(-1)
    updateTabStops()

  private def updateTabStops(): Unit =
    buttons.zipWithIndex.foreach { (button, index) =>
      button.setAttribute("tabindex", if index == current && !button.disabled then "0" else "-1")
    }

object EditorToolbar:
  /** Below this width, ribbon sections collapse to one open at a time. */
  private val RibbonMobileQuery = "(max-width: 640px)"
