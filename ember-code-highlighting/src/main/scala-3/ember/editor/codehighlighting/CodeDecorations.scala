package ember.editor.codehighlighting

import ember.editor.code.CodeBlockNode
import ember.editor.core.*
import ember.editor.ui.{DocumentView, TextRunElement}
import org.scalajs.dom
import ui.core.render.DomNodes

import scala.collection.mutable
import scala.scalajs.js

/** When results are requested after a change. */
enum DecorationTiming:

  /** In the next animation frame. Several commits in one frame -- a paste, a burst of keys -- cost
    * one request per block.
    */
  case NextFrame

  /** Directly after the projection. For tests, and for callers that paint synchronously. */
  case AfterProjection

/** Colours code blocks in the editor without touching the document DOM.
  *
  * ==Why the CSS Custom Highlight API==
  *
  * A code block is one text node (§8.2, P15), and the rest of the editor stands on that: the
  * position map counts UTF-16 offsets into it (§11), recovery compares it with the document
  * (§15.4), and a composition must not have the DOM around it rebuilt (§15.3). Token spans would
  * split that node on every keystroke. X02 names the risk in one line: "Mehrere Textspans
  * veraendern DOM-Offsets."
  *
  * A `Highlight` is a set of `Range`s the browser paints over text that is already there. No
  * element is created, no text node is split, and a `MutationObserver` sees nothing. Live ranges
  * also move with the text they cover, so between an edit and the next result the old colours shift
  * along rather than land on the wrong characters.
  *
  * ==What is painted, and when==
  *
  * A result is painted only while the DOM text is exactly the text it was computed for. During a
  * composition it is not -- the browser is ahead of the model -- and the block simply waits: the
  * old ranges keep moving with the composed text, and the next commit brings a new result.
  *
  * A browser without the API gets no colours and no errors ([[isSupported]]).
  *
  * ==Many editors, one page==
  *
  * Highlight names are global per document. Every instance adds its ranges to the one
  * `ember-tok-keyword` highlight and removes exactly those again; the name is unregistered when the
  * last range is gone. Two editors on a page therefore share a theme and never erase each other.
  */
final class CodeDecorations private (
    session: EditorSession,
    view: DocumentView,
    highlighter: Highlighter,
    timing: DecorationTiming
):

  private final case class Painted(registry: js.Dynamic, ranges: Vector[(String, dom.Range)])

  private val painted = mutable.HashMap.empty[NodeId, Painted]

  private val scheduler = new HighlightScheduler(
    session,
    highlighter,
    new HighlightSink:
      def show(result: HighlightResult): Boolean = paint(result)
      def clear(block: NodeId): Unit             = erase(block)
    ,
    () => request()
  )

  private var projections               = Subscription.cancelled
  private var frame: Option[js.Dynamic] = None
  private var frameHandle: js.Any       = js.undefined
  private var disposedFlag              = false

  def isDisposed: Boolean = disposedFlag

  /** The blocks that currently carry colours. */
  def paintedBlocks: Set[NodeId] = painted.keySet.toSet

  /** Answers that arrived for a revision nobody was waiting for any more. */
  def staleResults: Int = scheduler.staleResults

  /** Requests everything outstanding now, instead of in the next frame. */
  def flush(): Unit =
    if !disposedFlag then
      cancelFrame()
      scheduler.flush()

  /** Asks for every block again. For a caller that repaired the view outside a commit (§15.4). */
  def refresh(): Unit = scheduler.invalidateAll()

  /** Removes every range this instance added. The document is untouched, as it always was. */
  def dispose(): Unit =
    if !disposedFlag then
      disposedFlag = true
      projections.dispose()
      cancelFrame()
      scheduler.dispose()
      painted.keys.toVector.foreach(erase)

  private def attach(): Unit =
    projections = view.onProjected(_ => request())
    scheduler.start()

  private def request(): Unit =
    if !disposedFlag && scheduler.isDirty then
      timing match
        case DecorationTiming.AfterProjection =>
          // The scheduler hears about a commit after the view has projected it -- or, if a caller
          // subscribed in another order, before. Only a projected revision has a text node to
          // paint on; the view's own announcement brings us back otherwise.
          if view.projectedRevision == session.state.revision then scheduler.flush()
        case DecorationTiming.NextFrame =>
          if frame.isEmpty then
            windowOfView match
              case Some(window) if !js.isUndefined(window.requestAnimationFrame) =>
                frame = Some(window)
                frameHandle = window.requestAnimationFrame((_: Double) =>
                  frame = None
                  if !disposedFlag then scheduler.flush()
                )
              case _ => scheduler.flush()

  private def cancelFrame(): Unit =
    frame.foreach(window => window.cancelAnimationFrame(frameHandle))
    frame = None

  private def windowOfView: Option[js.Dynamic] =
    Option(view.root).flatMap(root => DomNodes.option(root.host)).flatMap(HighlightApi.windowOf)

  // ---------------------------------------------------------------------------------------
  // Painting
  // ---------------------------------------------------------------------------------------

  private def paint(result: HighlightResult): Boolean =
    textNodeOf(result.block) match
      case Some(text) if text.data == result.text =>
        HighlightApi.windowOf(text).filter(HighlightApi.supported) match
          // Nothing to paint on, and nothing to try again later either.
          case None         => true
          case Some(window) =>
            erase(result.block)
            val registry = window.CSS.highlights
            val document = text.ownerDocument
            val ranges   = result.tokens.map { token =>
              val range = document.createRange()
              range.setStart(text, token.start)
              range.setEnd(text, token.end)
              token.kind.className -> range
            }
            ranges.foreach((name, range) => HighlightApi.add(window, registry, name, range))
            painted(result.block) = Painted(registry, ranges)
            true
      case _ => false

  private def erase(block: NodeId): Unit =
    painted.remove(block).foreach { entry =>
      entry.ranges.foreach((name, range) => HighlightApi.remove(entry.registry, name, range))
    }

  /** The one text node of a code block, if the view shows one. */
  private def textNodeOf(block: NodeId): Option[dom.Text] =
    session.document
      .node(block)
      .collect { case code: CodeBlockNode => code }
      .flatMap(_.children.headOption)
      .flatMap(view.componentFor)
      .collect { case run: TextRunElement => run }
      .flatMap(_.textHost)
      .flatMap(DomNodes.option)
      .filter(_.nodeType == 3)
      .map(_.asInstanceOf[dom.Text])

object CodeDecorations:

  def attach(
      session: EditorSession,
      view: DocumentView,
      highlighter: Highlighter = new LocalHighlighter(),
      timing: DecorationTiming = DecorationTiming.NextFrame
  ): CodeDecorations =
    val decorations = new CodeDecorations(session, view, highlighter, timing)
    decorations.attach()
    decorations

  /** Whether this window can paint highlights at all. */
  def isSupported(window: dom.Window): Boolean =
    HighlightApi.supported(window.asInstanceOf[js.Dynamic])

/** The few calls into the Highlight API. `scalajs-dom` 2.8.1 has no facade for it. */
private object HighlightApi:

  /** The window a node belongs to -- an iframe's own, not the page's (§11, P21). */
  def windowOf(node: dom.Node): Option[js.Dynamic] =
    Option(node.ownerDocument).flatMap { document =>
      val window = document.asInstanceOf[js.Dynamic].defaultView
      if js.isUndefined(window) || window == null then None else Some(window)
    }

  def supported(window: js.Dynamic): Boolean =
    val css = window.CSS
    !js.isUndefined(css) && css != null && !js.isUndefined(css.highlights) &&
    !js.isUndefined(window.Highlight)

  def add(window: js.Dynamic, registry: js.Dynamic, name: String, range: dom.Range): Unit =
    val existing  = registry.get(name)
    val highlight =
      if js.isUndefined(existing) then
        val created = js.Dynamic.newInstance(window.Highlight)()
        registry.set(name, created)
        created
      else existing
    highlight.add(range): Unit

  def remove(registry: js.Dynamic, name: String, range: dom.Range): Unit =
    val existing = registry.get(name)
    if !js.isUndefined(existing) then
      existing.delete(range)
      if existing.size.asInstanceOf[Int] == 0 then registry.delete(name): Unit
