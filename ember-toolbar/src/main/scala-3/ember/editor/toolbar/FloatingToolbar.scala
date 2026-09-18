package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.browser.*
import org.scalajs.dom
import ui.core.component.Runtime
import ui.core.render.{DomCursor, DomNodes}

import scala.scalajs.js

/** A small toolbar that follows the current text selection, the way Medium or Notion's does.
  *
  * It shows only while the model selection is a non-collapsed range and the editor has focus, and
  * hides the moment the selection collapses or the focus leaves -- there is no explicit close
  * action, because there is nothing to trap: moving the caret is always enough to dismiss it.
  *
  * `container` is where it mounts -- typically `dom.document.body` -- so an `overflow: auto`
  * ancestor of the editor can never clip it; it positions itself with `position: fixed`, which
  * needs no scroll-offset math against viewport-relative `getBoundingClientRect` coordinates.
  *
  * The panel itself is a plain [[EditorToolbar]] (flat `actions`, tagged with the
  * `ember-floating-toolbar` class): same button wiring, roving tab stop and arrow-key navigation as
  * the ribbon and the flat toolbar harness already have and already test.
  */
final class FloatingToolbar(
    session: EditorSession,
    selection: SelectionPort,
    actions: Vector[ToolbarAction],
    container: dom.Element,
    name: String = "Auswahl formatieren"
):
  private val margin = 8.0

  private var panel        = Option.empty[EditorToolbar]
  private var reposition   = Option.empty[js.Function1[dom.Event, Unit]]
  private var disposedFlag = false

  private def currentRange(): Option[dom.Range] =
    selection.scope.selection.filter(_.rangeCount > 0).map(_.getRangeAt(0))

  private def place(host: dom.HTMLElement, range: dom.Range): Unit =
    // `position: fixed` first: measured before it, a freshly-mounted panel is still a static-flow
    // block (full container width), not the shrink-to-fit box it becomes once fixed -- and that
    // wrong width is exactly what the centering math below would clamp against.
    host.style.position = "fixed"
    val rect        = range.getBoundingClientRect()
    val hostRect    = host.getBoundingClientRect()
    val above       = rect.top - hostRect.height - margin
    val left        = (rect.left + rect.right) / 2 - hostRect.width / 2
    val clampedLeft = left.max(margin).min(dom.window.innerWidth.toDouble - hostRect.width - margin)
    val top         = if above < margin then rect.bottom + margin else above
    host.style.left = s"${clampedLeft}px"
    host.style.top = s"${top}px"

  private def show(range: dom.Range): Unit =
    val toolbar = panel.getOrElse {
      val fresh = new EditorToolbar(
        session,
        selection,
        actions,
        name,
        extraClasses = Vector("ember-floating-toolbar")
      )
      Runtime.mount(fresh, DomCursor.root(container))
      panel = Some(fresh)
      val handler: js.Function1[dom.Event, Unit] = _ =>
        currentRange().foreach(r =>
          place(DomNodes.raw(fresh.host).asInstanceOf[dom.HTMLElement], r)
        )
      reposition = Some(handler)
      dom.window.addEventListener("scroll", handler, true)
      dom.window.addEventListener("resize", handler)
      fresh
    }
    place(DomNodes.raw(toolbar.host).asInstanceOf[dom.HTMLElement], range)

  private def hide(): Unit =
    panel.foreach(Runtime.unmount)
    panel = None
    reposition.foreach { handler =>
      dom.window.removeEventListener("scroll", handler, true)
      dom.window.removeEventListener("resize", handler)
    }
    reposition = None

  private def update(): Unit =
    val visible = selection.scope.focusWithin && session.selection.exists {
      case range: RangeSelection => !range.isCollapsed
      case _                     => false
    }
    if visible then currentRange().fold(hide())(show) else hide()

  private val commits: Subscription =
    session.onCommit(commit => if commit.selectionChanged then update())

  def dispose(): Unit =
    if !disposedFlag then
      disposedFlag = true
      commits.dispose()
      hide()
