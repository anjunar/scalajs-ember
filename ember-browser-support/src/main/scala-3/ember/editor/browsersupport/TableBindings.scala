package ember.editor.browsersupport

import ember.editor.browser.*
import ember.editor.core.*
import ember.editor.table.*
import ember.editor.ui.DocumentView
import org.scalajs.dom
import ui.core.render.DomNodes

import scala.scalajs.js

/** Tables on the keyboard (X01).
  *
  * ==Tab, with the same exit as everywhere==
  *
  * Tab moves to the next cell and Shift+Tab to the previous one -- the one convention every table
  * editor shares. It is still §22's opt-in: [[tabNavigation]] belongs with
  * [[TabPolicy.IndentsUntilEscape]], and Escape then Tab leaves the editor from inside a table
  * exactly as it does from a list. Outside a table the command reports `Pass`, so list and code
  * indentation keep the key.
  */
object TableBindings:

  val tabNavigation: KeyboardBindings =
    KeyboardBindings.of {
      case Shortcut("Tab", false, false, false) =>
        session => session.dispatch(TableCommands.MoveToCell, CellDirection.Next)
      case Shortcut("Tab", false, true, false) =>
        session => session.dispatch(TableCommands.MoveToCell, CellDirection.Previous)
    }

  /** Escape turns a cell selection back into a caret. `Pass` otherwise. */
  val keyboard: KeyboardBindings =
    KeyboardBindings.of { case Shortcut("Escape", false, false, false) =>
      session => session.dispatch(TableCommands.CollapseCellSelection)
    }

/** What a [[TableSelection]] looks like in the browser, and where one comes from.
  *
  * ==Where it comes from==
  *
  * From dragging. A native selection that starts in one cell and ends in another cell of the same
  * table is imported as a range and promoted to a cell rectangle at once. Every edit then treats it
  * as the rectangle -- which is what the author dragged across -- and not as the text between the
  * two points in reading order.
  *
  * ==What it looks like==
  *
  * The selected cells get a background from a stylesheet in the document's head, addressed by their
  * node ids and scoped to this editor's host. Nothing inside the editor's DOM is written: the
  * projection, recovery and a running composition all see the DOM they would see without a cell
  * selection (§15.1, §15.4). The native highlight inside the table is hidden while a rectangle is
  * selected, because the browser's own would run row by row through the cells in between.
  *
  * ==How the browser sees it==
  *
  * As the range from the start of the anchor cell to the end of the focus cell, written through
  * [[SelectionPort.represent]]. The port recognises its own write, so the rectangle stays in the
  * model.
  */
final class TableSelectionView private (
    session: EditorSession,
    view: DocumentView,
    port: SelectionPort
):

  private var subscriptions               = Vector.empty[Subscription]
  private var sheet: dom.HTMLStyleElement = null
  private val scopeName                   = TableSelectionView.nextScope()
  private var disposedFlag                = false

  def isDisposed: Boolean = disposedFlag

  private def attach(): Unit =
    port.scope.host.setAttribute(TableSelectionView.ScopeAttribute, scopeName)
    subscriptions = Vector(
      port.represent {
        case cells: TableSelection => representation(cells)
        case _                     => None
      },
      port.onImport {
        case Some(range: RangeSelection) => promote(range)
        case _                           => ()
      },
      view.onProjected(_ => paint())
    )
    paint()

  private def representation(cells: TableSelection): Option[(Point, Point)] =
    val document = session.document
    Tables.grid(document, cells.table).flatMap { grid =>
      for
        anchorAt <- grid.positionOf(cells.anchor)
        focusAt  <- grid.positionOf(cells.focus)
      yield
        if Ordering[(Int, Int)].lteq(anchorAt, focusAt) then
          (Tables.startOf(document, cells.anchor), Tables.endOf(document, cells.focus))
        else (Tables.endOf(document, cells.anchor), Tables.startOf(document, cells.focus))
    }

  private def promote(range: RangeSelection): Unit =
    val document = session.document
    (Tables.cellOf(document, range.anchor.owner), Tables.cellOf(document, range.focus.owner)) match
      case (Some(anchor), Some(focus)) if anchor != focus =>
        (Tables.tableOfCell(document, anchor), Tables.tableOfCell(document, focus)) match
          case (Some(table), Some(other)) if table == other =>
            session.update(SelectionPort.importMeta)(
              _.select(TableSelection(table, anchor, focus)): Unit
            ): Unit
          case _ => ()
      case _ => ()

  private def paint(): Unit =
    if !disposedFlag then
      val rules = session.selection match
        case Some(cells: TableSelection) =>
          val selected = cells.cells(session.document)
          if selected.isEmpty then ""
          else
            val scope         = s"[${TableSelectionView.ScopeAttribute}=\"$scopeName\"]"
            val cellSelectors =
              selected.map(id =>
                s"""$scope [data-ember-node="${TableSelectionView.quote(id.value)}"]"""
              )
            s"${cellSelectors.mkString(",")}{background-color:var(--ember-table-selection,rgba(56,120,255,.2));}" +
              s"""$scope [data-ember-node="${TableSelectionView.quote(
                  cells.table.value
                )}"] *::selection{background:transparent;}"""
        case _ => ""
      stylesheet() match
        case Some(element) => if element.textContent != rules then element.textContent = rules
        case None          => ()

  private def stylesheet(): Option[dom.HTMLStyleElement] =
    if sheet != null then Some(sheet)
    else
      Option(port.scope.host.ownerDocument)
        .map(_.asInstanceOf[dom.HTMLDocument])
        .flatMap(document => Option(document.head).map(document -> _))
        .map { (document, head) =>
          val created = document.createElement("style").asInstanceOf[dom.HTMLStyleElement]
          created.setAttribute("data-ember-table-selection", scopeName)
          head.appendChild(created)
          sheet = created
          created
        }

  def dispose(): Unit =
    if !disposedFlag then
      disposedFlag = true
      subscriptions.foreach(_.dispose())
      subscriptions = Vector.empty
      if sheet != null then
        Option(sheet.parentNode).foreach(_.removeChild(sheet))
        sheet = null
      port.scope.host.removeAttribute(TableSelectionView.ScopeAttribute)

object TableSelectionView:

  val ScopeAttribute: String = "data-ember-table-scope"

  private var counter = 0

  private def nextScope(): String =
    counter += 1
    s"t$counter"

  private def quote(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

  def attach(session: EditorSession, view: DocumentView, port: SelectionPort): TableSelectionView =
    val created = new TableSelectionView(session, view, port)
    created.attach()
    created
