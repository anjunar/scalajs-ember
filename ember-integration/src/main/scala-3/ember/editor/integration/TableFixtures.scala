package ember.editor.integration

import ember.editor.browser.*
import ember.editor.browsersupport.*
import ember.editor.code.CodeExtension
import ember.editor.core.*
import ember.editor.history.{History, HistoryCommands}
import ember.editor.html.RenderProfile
import ember.editor.list.ListExtension
import ember.editor.richtext.*
import ember.editor.standard.TableSupport
import ember.editor.table.*
import ember.editor.ui.DocumentView
import ui.core.render.DomCursor
import org.scalajs.dom

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** Tables in a real browser (X01).
  *
  * {{{
  * root
  *   p0  "Vorher"
  *   t   table (header)
  *     r0  c00 "A"   c01 "B"
  *     r1  c10 "1"   c11 "2"
  *   p1  "Nachher"
  * }}}
  */
@JSExportTopLevel("tableFixtures")
object TableFixtures:

  private val rootId = NodeId("root")

  private var session: EditorSession             = null
  private var view: DocumentView                 = null
  private var port: SelectionPort                = null
  private var controller: BrowserInputController = null
  private var cells: TableSelectionView          = null
  private val holder                             = new CompositionHolder

  @JSExport
  def mount(container: dom.Element): Unit =
    dispose()
    val generator = NodeIdGenerator.sequential("g")
    val history   = new History()
    val gate      = new Extension:
      val id: ExtensionId                             = ExtensionId("ember.it.table-gate")
      override def contribute: ExtensionContributions =
        ExtensionContributions(preCommitRules = Vector(BrowserInputController.busyRule(holder)))

    val resolved = ExtensionResolver.resolve(
      Vector(
        RichText(generator),
        history,
        gate,
        ListExtension(generator),
        CodeExtension(generator),
        TableExtension(generator)
      )
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    def cell(id: String, text: String, header: Boolean = false): Vector[EditorNode] =
      Vector(
        TableCellNode(NodeId(id), Vector(NodeId(s"$id-p")), header),
        ParagraphNode(NodeId(s"$id-p"), Vector(NodeId(s"$id-t"))),
        TextNode(NodeId(s"$id-t"), text)
      )

    val nodes = Vector(
      RootNode(rootId, Vector(NodeId("p0"), NodeId("t"), NodeId("p1"))),
      ParagraphNode(NodeId("p0"), Vector(NodeId("p0-t"))),
      TextNode(NodeId("p0-t"), "Vorher"),
      TableNode(
        NodeId("t"),
        Vector(NodeId("r0"), NodeId("r1")),
        header = true,
        Vector(ColumnAlignment.Default, ColumnAlignment.Default)
      ),
      TableRowNode(NodeId("r0"), Vector(NodeId("c00"), NodeId("c01"))),
      TableRowNode(NodeId("r1"), Vector(NodeId("c10"), NodeId("c11")))
    ) ++ cell("c00", "A", header = true) ++ cell("c01", "B", header = true) ++
      cell("c10", "1") ++ cell("c11", "2") ++ Vector(
        ParagraphNode(NodeId("p1"), Vector(NodeId("p1-t"))),
        TextNode(NodeId("p1-t"), "Nachher")
      )

    session = EditorSession.create(
      Document.unsafe(resolved.schema, rootId, nodes),
      resolved,
      resolved.sessionConfig()
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    view = DocumentView.mount(
      session,
      DomCursor.root(container),
      TableSupport.views,
      RenderProfile.Editor
    )
    port = SelectionPort.attachTo(session, view, container)
    controller = BrowserInputController.attachTo(
      session,
      view,
      port,
      EditorBindings.everything,
      // Table Tab first: inside a cell it moves, and outside it passes to code and lists.
      TableBindings.tabNavigation ++ EditorBindings.tabIndentation ++
        EditorBindings.everythingKeyboard ++ TableBindings.keyboard,
      TabPolicy.IndentsUntilEscape,
      EditorMode.Editable,
      Some(TableSupport.everything),
      BusyPolicy.Defer
    )
    holder.bind(controller)
    HistoryBindings.groupCompositions(controller, history): Unit
    cells = TableSelectionView.attach(session, view, port)

  @JSExport
  def dispose(): Unit =
    if cells != null then cells.dispose()
    if controller != null then controller.dispose()
    if port != null then port.dispose()
    if view != null then view.dispose()
    if session != null then session.dispose()
    holder.release()
    cells = null
    controller = null
    port = null
    view = null
    session = null

  // -----------------------------------------------------------------------------------------
  // Reading
  // -----------------------------------------------------------------------------------------

  /** The first table as `*A|*B / 1|2`, header cells marked. */
  @JSExport
  def table(): String =
    val document = session.document
    document.inDocumentOrder.collectFirst { case value: TableNode => value } match
      case None        => "-"
      case Some(value) =>
        Tables.grid(document, value.id).fold("?") { grid =>
          grid.cells
            .map(
              _.map(cell => (if cell.header then "*" else "") + textOf(document, cell.id))
                .mkString("|")
            )
            .mkString(" / ")
        }

  @JSExport
  def blocks(): String =
    val document = session.document
    document
      .childrenOf(document.rootId)
      .map(id =>
        document.node(id) match
          case Some(_: TableNode) => "[table]"
          case _                  => textOf(document, id)
      )
      .mkString(" · ")

  private def textOf(document: DocumentRead, id: NodeId): String =
    document.node(id) match
      case Some(run: TextNode)        => run.text
      case Some(element: ElementNode) =>
        val parts = element.children.map(textOf(document, _))
        if element.isInstanceOf[TableCellNode] then parts.mkString("¶") else parts.mkString
      case _ => ""

  @JSExport
  def selection(): String =
    session.selection match
      case Some(RangeSelection(anchor, focus)) if anchor == focus =>
        focus match
          case Point.Text(node, offset, _) =>
            s"${Tables.cellOf(session.document, node).map(_.value).getOrElse("-")}@$offset"
          case other => other.toString
      case Some(range: RangeSelection) => "range"
      case Some(cells: TableSelection) => s"cells(${cells.anchor.value}..${cells.focus.value})"
      case other                       => other.toString

  // -----------------------------------------------------------------------------------------
  // Driving
  // -----------------------------------------------------------------------------------------

  @JSExport
  def setCaret(run: String, offset: Int): String =
    val selection = RangeSelection.caret(Point.textBefore(NodeId(run), offset))
    session.update(_.select(selection): Unit) match
      case Left(error) => s"rejected:${error.render}"
      case Right(_)    => port.write(Some(selection), WriteIntent.Explicit).toString

  @JSExport
  def insertRow(below: Boolean): String =
    session
      .dispatch(TableCommands.InsertRow, if below then RowPosition.Below else RowPosition.Above)
      .fold(_.render, _.result.toString)

  @JSExport
  def insertTable(rows: Int, columns: Int): String =
    session
      .dispatch(TableCommands.InsertTable, TableSize(rows, columns))
      .fold(_.render, _.result.toString)

  @JSExport
  def undo(): String = session.dispatch(HistoryCommands.Undo).fold(_.render, _.result.toString)
