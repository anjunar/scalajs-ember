package ember.editor.integration

import ember.editor.browser.*
import ember.editor.browsersupport.*
import ember.editor.code.*
import ember.editor.codehighlighting.*
import ember.editor.core.*
import ember.editor.history.History
import ember.editor.html.RenderProfile
import ember.editor.list.ListExtension
import ember.editor.richtext.*
import ember.editor.standard.CodeSupport
import ember.editor.ui.DocumentView
import ui.core.render.DomCursor
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** Syntax highlighting in a real browser (X02).
  *
  * A full editor -- selection port, input controller, composition gate -- because what X02 has to
  * show is that colouring leaves all of them alone: "Text/Selection/IME unveraendert".
  *
  * {{{
  * root
  *   p0   p            t0  "Intro"
  *   c0   pre (scala)  tc  "val x = 1 // note\ndef f = \"s\""
  *   p1   p            t1  "Outro"
  * }}}
  */
@JSExportTopLevel("highlightFixtures")
object HighlightFixtures:

  private val rootId = NodeId("root")
  private val source = "val x = 1 // note\ndef f = \"s\""

  /** A worker that answers only when told to, and answers withdrawn questions too. */
  private final class HeldHighlighter extends Highlighter:
    private val local = new LocalHighlighter()
    val held          = mutable.ArrayBuffer.empty[(HighlightRequest, HighlightResult => Unit)]

    def highlight(request: HighlightRequest, reply: HighlightResult => Unit): Subscription =
      held += request -> reply
      Subscription.cancelled

    def forget(block: NodeId): Unit = local.forget(block)

    def answer(index: Int): Unit =
      val (request, reply) = held.remove(index)
      reply(local.compute(request))

  private final class Editor(
      container: dom.Element,
      input: Boolean,
      highlighter: Highlighter,
      timing: DecorationTiming
  ):
    private val holder    = new CompositionHolder
    private val generator = NodeIdGenerator.sequential("g")
    private val history   = new History()

    private val gate = new Extension:
      val id: ExtensionId                             = ExtensionId("ember.it.highlight-gate")
      override def contribute: ExtensionContributions =
        ExtensionContributions(preCommitRules = Vector(BrowserInputController.busyRule(holder)))

    private val resolved = ExtensionResolver.resolve(
      Vector(RichText(generator), history, gate, ListExtension(generator), CodeExtension(generator))
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    val session: EditorSession = EditorSession.create(
      Document.unsafe(
        resolved.schema,
        rootId,
        Vector(
          RootNode(rootId, Vector(NodeId("p0"), NodeId("c0"), NodeId("p1"))),
          ParagraphNode(NodeId("p0"), Vector(NodeId("t0"))),
          TextNode(NodeId("t0"), "Intro"),
          CodeBlockNode(NodeId("c0"), Vector(NodeId("tc")), CodeInfo.of("scala")),
          TextNode(NodeId("tc"), source),
          ParagraphNode(NodeId("p1"), Vector(NodeId("t1"))),
          TextNode(NodeId("t1"), "Outro")
        )
      ),
      resolved,
      resolved.sessionConfig()
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    val view: DocumentView =
      DocumentView.mount(
        session,
        DomCursor.root(container),
        CodeSupport.views,
        RenderProfile.Editor
      )

    val port: SelectionPort =
      if input then SelectionPort.attachTo(session, view, container) else null

    val controller: BrowserInputController =
      if !input then null
      else
        val created = BrowserInputController.attachTo(
          session,
          view,
          port,
          EditorBindings.everything,
          EditorBindings.everythingKeyboard,
          TabPolicy.LeavesEditor,
          EditorMode.Editable,
          Some(CodeSupport.everything),
          BusyPolicy.Defer
        )
        holder.bind(created)
        HistoryBindings.groupCompositions(created, history): Unit
        created

    val decorations: CodeDecorations = CodeDecorations.attach(session, view, highlighter, timing)

    def dispose(): Unit =
      decorations.dispose()
      if controller != null then controller.dispose()
      if port != null then port.dispose()
      view.dispose()
      session.dispose()
      holder.release()

  private var editor: Editor          = null
  private var second: Editor          = null
  private var worker: HeldHighlighter = null

  // -----------------------------------------------------------------------------------------
  // Lifecycle
  // -----------------------------------------------------------------------------------------

  /** `held` puts a worker in front that answers on request; `frame` paints in the next frame. */
  @JSExport
  def mount(container: dom.Element, held: Boolean = false, frame: Boolean = false): Unit =
    dispose()
    worker = if held then new HeldHighlighter else null
    editor = new Editor(
      container,
      input = true,
      if held then worker else new LocalHighlighter(),
      if frame then DecorationTiming.NextFrame else DecorationTiming.AfterProjection
    )

  /** A second, read-only editor with the same document, for the shared highlight names. */
  @JSExport
  def mountSecond(container: dom.Element): Unit =
    disposeSecond()
    second =
      new Editor(container, input = false, new LocalHighlighter(), DecorationTiming.AfterProjection)

  @JSExport
  def disposeSecond(): Unit =
    if second != null then second.dispose()
    second = null

  @JSExport
  def dispose(): Unit =
    disposeSecond()
    if editor != null then editor.dispose()
    editor = null
    worker = null

  /** Removes the decorations only; the editor stays. */
  @JSExport
  def disposeDecorations(): Unit = editor.decorations.dispose()

  // -----------------------------------------------------------------------------------------
  // Reading
  // -----------------------------------------------------------------------------------------

  @JSExport
  def supported(): Boolean = CodeDecorations.isSupported(dom.window)

  /** The text under every range of one token kind, across every editor on the page. */
  @JSExport
  def ranges(kind: String): js.Array[String] =
    val out = js.Array[String]()
    if supported() then
      val highlight = dom.window.asInstanceOf[js.Dynamic].CSS.highlights.get(s"ember-tok-$kind")
      if !js.isUndefined(highlight) then
        highlight.forEach((range: js.Dynamic) =>
          out.push(range.toString().asInstanceOf[String]): Unit
        )
    out

  /** The registered highlight names that belong to Ember. */
  @JSExport
  def names(): js.Array[String] =
    if !supported() then js.Array()
    else
      val keys =
        js.Dynamic.global.Array.from(dom.window.asInstanceOf[js.Dynamic].CSS.highlights.keys())
      keys.asInstanceOf[js.Array[String]].filter(_.startsWith("ember-tok-"))

  @JSExport def painted(): Int = editor.decorations.paintedBlocks.size

  @JSExport def stale(): Int = editor.decorations.staleResults

  @JSExport def held(): Int = worker.held.size

  @JSExport def code(): String =
    editor.session.document
      .node(NodeId("tc"))
      .collect { case run: TextNode => run.text }
      .getOrElse("")

  @JSExport def revision(): Int = editor.session.state.revision.value.toInt

  @JSExport def state(): String = editor.controller.state.toString

  // -----------------------------------------------------------------------------------------
  // Driving
  // -----------------------------------------------------------------------------------------

  @JSExport def answer(index: Int): Unit = worker.answer(index)

  @JSExport def flush(): Unit = editor.decorations.flush()

  @JSExport def refresh(): Unit =
    editor.decorations.refresh()
    editor.decorations.flush()

  @JSExport
  def setCaret(node: String, offset: Int): String =
    val selection = RangeSelection.caret(Point.textBefore(NodeId(node), offset))
    editor.session.update(_.select(selection): Unit) match
      case Left(error) => s"rejected:${error.render}"
      case Right(_)    => editor.port.write(Some(selection), WriteIntent.Explicit).toString

  @JSExport
  def setLanguage(language: String): String =
    setCaret("tc", 0): Unit
    editor.session
      .dispatch(CodeCommands.SetCodeInfo, CodeInfo.of(language))
      .fold(_.render, _.toString)

  @JSExport
  def toParagraph(): String =
    setCaret("tc", 0): Unit
    editor.session.dispatch(CodeCommands.ToggleCodeBlock, CodeInfo.empty).fold(_.render, _.toString)
