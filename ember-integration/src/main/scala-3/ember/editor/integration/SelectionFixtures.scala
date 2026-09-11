package ember.editor.integration

import ember.editor.browser.*
import ember.editor.code.{CodeBlockNode, CodeExtension}
import ember.editor.core.*
import ember.editor.html.{HtmlAttribute, RenderProfile}
import ember.editor.jfx.{DocumentView, NodeView, ViewSupport}
import ember.editor.richtext.*
import ember.editor.standard.ImageSupport
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.TextArea.textArea
import jfx.core.render.{Cursor, DomCursor}
import org.scalajs.dom

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** An atom whose inside is not editor text.
  *
  * §15.2 names the case: "native Inputs/Textareas in Atom-Views, unmanaged Bereiche und
  * verschachtelte Editoren gehoeren nicht automatisch zum aeusseren Editor." A picture is the
  * usual atom and makes a poor test of it -- `<img>` is void, so nothing can be selected inside.
  * This one holds a real textarea, which is exactly the shape the rule is about.
  */
final case class WidgetNode(id: NodeId, label: String) extends AtomNode

object WidgetNode extends NodeType[WidgetNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.it.widget/1")

  def project(node: EditorNode): Option[WidgetNode] = node match
    case widget: WidgetNode => Some(widget)
    case _                  => None

  def rekey(node: WidgetNode, id: NodeId): WidgetNode = node.copy(id = id)

/** The component for [[WidgetNode]]: an element with a native control inside it. */
private final class WidgetComponent(node: WidgetNode) extends AbstractComponent:

  val tagName = "span"

  override def compose(cursor: Cursor): Unit =
    host.setAttribute("data-ember-node", node.id.value)
    host.setAttribute("data-widget", "")
    DslLayer.render(this, cursor) {
      val area = textArea(node.label) {}
      area.setAttribute("data-widget-input", "")
      // Out of the tab order. §22 wants atomic media reachable by keyboard, but that is the
      // media view's own affordance (P26) -- a bare control inside an atom would simply be what
      // Tab hits first, and the tests about the editing surface's own Tab behaviour would then
      // be testing this textarea instead.
      area.setAttribute("tabindex", "-1")
    }

private object WidgetView extends NodeView[WidgetNode]:

  val nodeType: NodeType[WidgetNode] = WidgetNode

  def create(node: WidgetNode, profile: RenderProfile): AbstractComponent =
    new WidgetComponent(node)

  def accepts(component: AbstractComponent, node: WidgetNode, profile: RenderProfile): Boolean =
    component.isInstanceOf[WidgetComponent]

  def update(component: AbstractComponent, node: WidgetNode, profile: RenderProfile): Unit = ()

private object WidgetExtension extends Extension:

  val id: ExtensionId = ExtensionId("ember.it.widget")

  override def contribute: ExtensionContributions =
    ExtensionContributions(nodeTypes = Vector(WidgetNode))

/** Selection and focus in a real engine (P21).
  *
  * ==The document, and why it has these five blocks==
  *
  * Each one is a row of §11's mapping table that a simpler document would not reach:
  *
  * {{{
  * root    article
  *   h0    h2      t0  "Titel"
  *   p0    p       t1  "Hallo "        ein gewoehnlicher Lauf
  *                 t2  "Welt"  strong  ein Lauf mit Mark -> <span><strong>Text</strong></span>
  *   p1    p                           ein leerer Absatz -- nur Gruppenanker im DOM
  *   c0    pre>code t3 "zeile"         Innentags: die Kinder haengen im <code>
  *   p2    p       a0  Widget          ein Atom mit einer nativen Textarea darin
  * }}}
  *
  * Fixed ids so that the driver can name them.
  */
@JSExportTopLevel("selectionFixtures")
object SelectionFixtures:

  private val rootId = NodeId("root")

  private var session: EditorSession    = null
  private var view: DocumentView        = null
  private var port: SelectionPort       = null
  private var focus: FocusController    = null
  private var host: dom.Element         = null
  private var imports                   = 0
  private var focusChanges              = Vector.empty[Boolean]
  private var bookmark: Option[SelectionBookmark] = None

  private val views: ViewSupport = ViewSupport.of(WidgetView) ++ ImageSupport.views

  // Ein zweiter, vollstaendig eigener Editor. P21s Testliste nennt "zwei Editoren", "iframe" und
  // Shadow-DOM ausdruecklich, und alle drei fragen dasselbe: haelt ein Port seinen Host
  // auseinander von allem anderen. Ein Dokument mit anderen IDs macht die Antwort im Test lesbar.
  private var neighbourSession: EditorSession = null
  private var neighbourView: DocumentView     = null
  private var neighbourPort: SelectionPort    = null
  private var neighbourHost: dom.Element      = null

  // -----------------------------------------------------------------------------------------
  // Lebenszyklus
  // -----------------------------------------------------------------------------------------

  @JSExport
  def mount(container: dom.Element): Unit =
    dispose()

    val generator = NodeIdGenerator.sequential("g")
    val resolved = ExtensionResolver.resolve(
      Vector(RichText(generator), CodeExtension(generator), WidgetExtension)
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    val nodes = Vector(
      RootNode(rootId, Vector(NodeId("h0"), NodeId("p0"), NodeId("p1"), NodeId("c0"), NodeId("p2"))),
      HeadingNode(NodeId("h0"), Vector(NodeId("t0")), HeadingLevel.H2),
      TextNode(NodeId("t0"), "Titel"),
      ParagraphNode(NodeId("p0"), Vector(NodeId("t1"), NodeId("t2"))),
      TextNode(NodeId("t1"), "Hallo "),
      TextNode(NodeId("t2"), "Welt", MarkSet.of(StandardMarks.Strong)),
      ParagraphNode(NodeId("p1"), Vector.empty),
      CodeBlockNode(NodeId("c0"), Vector(NodeId("t3"))),
      TextNode(NodeId("t3"), "zeile"),
      ParagraphNode(NodeId("p2"), Vector(NodeId("a0"))),
      WidgetNode(NodeId("a0"), "Notiz")
    )

    session = EditorSession.create(
      Document.unsafe(resolved.schema, rootId, nodes),
      resolved,
      resolved.sessionConfig()
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    // `contenteditable` so the host can take focus and show a caret. §22 keeps focusability and
    // editability apart; this fixture wants both, because that is what P22 will drive.
    container.setAttribute("contenteditable", "true")
    host = container

    view = DocumentView.mount(session, DomCursor.root(container), views, RenderProfile.Editor)

    port = SelectionPort.attachTo(session, view, container)
    port.onImport(_ => imports += 1): Unit

    focus = FocusController.attachTo(session, view, port)
    focus.onFocusChange(inside => focusChanges = focusChanges :+ inside): Unit

  /** A second editor in another container -- another document or shadow root, if the test wants.
    *
    * Nothing is shared: its own session, its own projection, its own port. That is the point of
    * [[BrowserScope]] -- everything it touches comes from '''its''' host's `ownerDocument`.
    */
  @JSExport
  def mountNeighbour(container: dom.Element): Unit =
    disposeNeighbour()

    val generator = NodeIdGenerator.sequential("n")
    val resolved = ExtensionResolver.resolve(Vector(RichText(generator))) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    val nodes = Vector(
      RootNode(NodeId("nroot"), Vector(NodeId("np"))),
      ParagraphNode(NodeId("np"), Vector(NodeId("nt"))),
      TextNode(NodeId("nt"), "Nachbar")
    )

    neighbourSession = EditorSession.create(
      Document.unsafe(resolved.schema, NodeId("nroot"), nodes),
      resolved,
      resolved.sessionConfig()
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    container.setAttribute("contenteditable", "true")
    neighbourHost = container
    neighbourView =
      DocumentView.mount(neighbourSession, DomCursor.root(container), views, RenderProfile.Editor)
    neighbourPort = SelectionPort.attachTo(neighbourSession, neighbourView, container)

  @JSExport
  def neighbourRead(): String = render(neighbourPort.read())

  @JSExport
  def neighbourSelection(): String =
    neighbourSession.selection match
      case Some(range: RangeSelection) => renderRange(range)
      case _                           => "none"

  @JSExport def neighbourCapability(): String = neighbourPort.scope.capability.toString

  @JSExport def neighbourFocusWithin(): Boolean = neighbourPort.scope.focusWithin

  @JSExport
  def neighbourSelectText(node: String, offset: Int): String =
    val selection = RangeSelection(
      Point.textBefore(NodeId(node), offset),
      Point.textBefore(NodeId(node), offset)
    )
    neighbourSession.update(_.select(selection): Unit) match
      case Left(error) => s"rejected:${error.render}"
      case Right(_)    => render(neighbourPort.write(Some(selection), WriteIntent.Explicit))

  @JSExport
  def disposeNeighbour(): Unit =
    if neighbourPort != null then neighbourPort.dispose()
    if neighbourView != null then neighbourView.dispose()
    if neighbourSession != null then neighbourSession.dispose()
    if neighbourHost != null then neighbourHost.removeAttribute("contenteditable")
    neighbourPort = null
    neighbourView = null
    neighbourSession = null
    neighbourHost = null

  /** Appends many runs to a paragraph -- the "viele Leaves" case of P21's test list. */
  @JSExport
  def appendRuns(parent: String, count: Int): Boolean =
    val parentId = NodeId(parent)
    session.update { tx =>
      var index = 0
      while index < count do
        val at = session.document.node(parentId) match
          case Some(element: ElementNode) => element.children.length
          case _                          => 0
        tx.insert(
          parentId,
          at,
          TextNode(
            NodeId(s"x$index"),
            s"Lauf$index",
            if index % 2 == 0 then MarkSet.of(StandardMarks.Strong) else MarkSet.empty
          )
        ): Unit
        index += 1
    }.isRight

  @JSExport
  def dispose(): Unit =
    disposeNeighbour()
    if focus != null then focus.dispose()
    if port != null then port.dispose()
    if view != null then view.dispose()
    if session != null then session.dispose()
    if host != null then host.removeAttribute("contenteditable")
    focus = null
    port = null
    view = null
    session = null
    host = null
    imports = 0
    focusChanges = Vector.empty
    bookmark = None

  // -----------------------------------------------------------------------------------------
  // Lesen
  // -----------------------------------------------------------------------------------------

  /** The native selection as the model sees it, rendered for a test. */
  @JSExport
  def read(): String = render(port.read())

  /** The session's current selection, rendered the same way. */
  @JSExport
  def modelSelection(): String =
    session.selection match
      case Some(range: RangeSelection) => renderRange(range)
      case Some(nodes: NodeSelection)  => nodes.nodes.toVector.map(_.value).sorted.mkString("nodes:", ",", "")
      case Some(_)                     => "other"
      case None                        => "none"

  /** The direction of the model selection in document order (§11). */
  @JSExport
  def direction(): String =
    session.selection match
      case Some(range: RangeSelection) => range.direction(session.document).toString
      case _                           => "none"

  @JSExport def importCount(): Int = imports

  @JSExport def capability(): String = port.scope.capability.toString

  @JSExport def focusWithin(): Boolean = port.scope.focusWithin

  @JSExport def focusLog(): String = focusChanges.map(_.toString).mkString(",")

  /** Which document node owns a DOM node, addressed the way a test can name it. */
  @JSExport
  def nodeAt(selector: String, childIndex: Int): String =
    val element = host.querySelector(selector)
    if element == null then "missing"
    else
      val target =
        if childIndex < 0 then element
        else if childIndex < element.childNodes.length then element.childNodes(childIndex)
        else element
      port.positions.nodeAt(target, session.document).map(_.value).getOrElse("none")

  // -----------------------------------------------------------------------------------------
  // Schreiben
  // -----------------------------------------------------------------------------------------

  /** Puts a caret or a range into the model and writes it to the DOM. */
  @JSExport
  def selectText(
      anchorNode: String,
      anchorOffset: Int,
      focusNode: String,
      focusOffset: Int
  ): String =
    apply(
      RangeSelection(
        Point.textBefore(NodeId(anchorNode), anchorOffset),
        Point.textBefore(NodeId(focusNode), focusOffset)
      )
    )

  @JSExport
  def selectChildren(parent: String, anchorOffset: Int, focusOffset: Int): String =
    apply(
      RangeSelection(
        Point.childrenBefore(NodeId(parent), anchorOffset),
        Point.childrenBefore(NodeId(parent), focusOffset)
      )
    )

  @JSExport
  def selectNodes(ids: String): String =
    apply(NodeSelection(ids.split(",").filter(_.nonEmpty).map(NodeId.apply).toSet))

  private def apply(selection: Selection): String =
    session.update(_.select(selection): Unit) match
      case Left(error) => s"rejected:${error.render}"
      case Right(_)    => render(port.write(Some(selection), WriteIntent.Explicit))

  /** Writes a selection straight through the port, without asking the core first.
    *
    * The core rejects a child point on an atom before the port ever sees it -- rightly, and the
    * test says so. This is the way to show that the port has its own answer for the same
    * position instead of relying on somebody else having refused it.
    */
  @JSExport
  def writeRawChildren(parent: String, offset: Int): String =
    render(
      port.write(
        Some(
          RangeSelection(
            Point.childrenBefore(NodeId(parent), offset),
            Point.childrenBefore(NodeId(parent), offset)
          )
        ),
        WriteIntent.Explicit
      )
    )

  /** The model point for a DOM position, without going through a selection. */
  @JSExport
  def pointAt(selector: String, childIndex: Int): String =
    val element = host.querySelector(selector)
    if element == null then "missing"
    else
      val target =
        if childIndex < 0 then element
        else if childIndex < element.childNodes.length then element.childNodes(childIndex)
        else element
      port.positions.toPoint(DomPosition(target, 0), session.document) match
        case Right(point)  => renderPoint(point)
        case Left(problem) => s"failed:${problem.toString.takeWhile(_ != '(')}"

  /** Commits a selection '''without''' writing it to the DOM.
    *
    * Needed because writing one into an editable host focuses that host in Chromium -- so a test
    * about an unfocused editor cannot get there through a write.
    */
  @JSExport
  def selectModelOnly(node: String, anchorOffset: Int, focusOffset: Int): Boolean =
    session
      .update(
        _.select(
          RangeSelection(
            Point.textBefore(NodeId(node), anchorOffset),
            Point.textBefore(NodeId(node), focusOffset)
          )
        ): Unit
      )
      .isRight

  /** Writes the session's selection with the default intent -- the background case (§22). */
  @JSExport
  def syncBackground(): String = render(port.sync())

  /** Reads the DOM and commits, without waiting for the event. */
  @JSExport
  def importNative(): String = render(port.importNative())

  // -----------------------------------------------------------------------------------------
  // Fokus und Bookmarks
  // -----------------------------------------------------------------------------------------

  @JSExport
  def capture(): Boolean =
    bookmark = focus.capture()
    bookmark.isDefined

  @JSExport def capturedHadFocus(): Boolean = bookmark.exists(_.hadFocus)

  @JSExport
  def restore(intent: String): String =
    bookmark match
      case None => "no-bookmark"
      case Some(saved) =>
        val chosen = intent match
          case "always"      => FocusIntent.Always
          case "if-it-was-ours" => FocusIntent.IfItWasOurs
          case _             => FocusIntent.SelectionOnly

        focus.restore(saved, chosen) match
          case RestoreOutcome.Restored(selection, focused) =>
            s"restored:${renderRange(selection)}:$focused"
          case RestoreOutcome.Expired(error)  => s"expired:${error.render}"
          case RestoreOutcome.NotWritten(why) => s"not-written:${render(why)}"

  /** Edits the document while a bookmark is outstanding, so the mapping has work to do. */
  @JSExport
  def splice(nodeId: String, start: Int, deleteCount: Int, inserted: String): Boolean =
    session.update(_.spliceText(NodeId(nodeId), start, deleteCount, inserted): Unit).isRight

  /** Removes a node, so a bookmark inside it has to fall back to a boundary (§11). */
  @JSExport
  def remove(nodeId: String): Boolean =
    session.update(_.remove(NodeId(nodeId)): Unit).isRight

  @JSExport def revision(): Int = session.state.revision.value.toInt

  @JSExport def projected(): Int = view.projectedRevision.value.toInt

  // -----------------------------------------------------------------------------------------
  // Darstellung
  // -----------------------------------------------------------------------------------------

  private def render(reading: SelectionReading): String = reading match
    case SelectionReading.Absent              => "absent"
    case SelectionReading.Outside             => "outside"
    case SelectionReading.Unmappable(problem) => s"unmappable:${problem.toString.takeWhile(_ != '(')}"
    case SelectionReading.Foreign(owner)      => s"foreign:${owner.value}"
    case SelectionReading.Mapped(selection)   => renderRange(selection)

  private def render(write: SelectionWrite): String = write match
    case SelectionWrite.Written          => "written"
    case SelectionWrite.Skipped(reason)  => s"skipped:$reason"
    case SelectionWrite.Failed(problem)  => s"failed:${problem.toString.takeWhile(_ != '(')}"

  private def renderRange(selection: RangeSelection): String =
    s"${renderPoint(selection.anchor)}|${renderPoint(selection.focus)}"

  private def renderPoint(point: Point): String = point match
    case Point.Text(node, offset, _)       => s"text:${node.value}:$offset"
    case Point.Children(parent, offset, _) => s"children:${parent.value}:$offset"
