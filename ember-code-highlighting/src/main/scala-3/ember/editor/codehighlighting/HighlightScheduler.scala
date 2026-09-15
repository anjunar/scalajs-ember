package ember.editor.codehighlighting

import ember.editor.code.CodeBlockNode
import ember.editor.core.*

import scala.collection.mutable

/** Where results go. The browser paints them; a test records them. */
trait HighlightSink:

  /** Shows a current result.
    *
    * `false` means the view cannot show it yet -- the DOM text is not the committed text, which is
    * exactly the state of a block during a composition (§15.3). The block is asked for again after
    * the next projection, and nothing is written in the meantime.
    */
  def show(result: HighlightResult): Boolean

  def clear(block: NodeId): Unit

/** Decides which code blocks need colours, asks for them, and throws away late answers.
  *
  * ==Derived view state, nothing else==
  *
  * X02's acceptance: "Highlighting ausblenden aendert kein Dokument/History." This class reads
  * commits and never writes one -- it has no transaction, no command and no state field. Its entire
  * state is which blocks are dirty and which revision was last asked for, and all of it can be
  * thrown away and rebuilt from the document (§5: ViewState is "vollstaendig aus Modell und
  * Mount-Konfiguration rekonstruierbar").
  *
  * ==What counts as stale==
  *
  * An answer is shown only if it answers the '''latest''' request for its block and its text is
  * still the block's text. The first catches a slow worker overtaken by a newer keystroke; the
  * second a result that arrives after a commit but before the flush that would have asked again.
  */
final class HighlightScheduler(
    session: EditorSession,
    highlighter: Highlighter,
    sink: HighlightSink,
    wake: () => Unit = () => ()
):

  private val dirty     = mutable.LinkedHashSet.empty[NodeId]
  private val requested = mutable.HashMap.empty[NodeId, Revision]
  private val answered  = mutable.HashMap.empty[NodeId, Revision]
  private val pending   = mutable.HashMap.empty[NodeId, Subscription]
  private val shown     = mutable.HashSet.empty[NodeId]
  private var commits   = Subscription.cancelled
  private var started   = false
  private var disposed  = false
  private var stale     = 0

  def start(): Unit =
    if !started && !disposed then
      started = true
      markAll()
      commits = session.onCommit(onCommit)
      wake()

  def isDirty: Boolean = dirty.nonEmpty

  def dirtyBlocks: Set[NodeId] = dirty.toSet

  def shownBlocks: Set[NodeId] = shown.toSet

  /** Answers that arrived too late to be shown. */
  def staleResults: Int = stale

  /** Asks for every dirty block. */
  def flush(): Unit =
    if started && !disposed then
      val blocks = dirty.toVector
      dirty.clear()
      blocks.foreach(request)

  def invalidate(block: NodeId): Unit =
    if !disposed && isCode(session.document, block) then
      dirty += block
      wake()

  def invalidateAll(): Unit =
    if !disposed then
      markAll()
      wake()

  def dispose(): Unit =
    if !disposed then
      disposed = true
      commits.dispose()
      pending.values.foreach(_.dispose())
      (shown ++ requested.keySet).foreach(highlighter.forget)
      shown.foreach(sink.clear)
      pending.clear()
      shown.clear()
      requested.clear()
      answered.clear()
      dirty.clear()

  // ---------------------------------------------------------------------------------------

  private def onCommit(commit: Commit): Unit =
    if !disposed then
      val document = commit.current.document
      val changes  = commit.changes
      val before   = dirty.size

      if changes.documentReplaced then
        known.filterNot(isCode(document, _)).foreach(drop)
        markAll()
      else
        (changes.changedNodes ++ changes.touchedAncestors).foreach { id =>
          document.node(id) match
            case Some(_: CodeBlockNode) => dirty += id
            case Some(_: TextNode)      =>
              document.parentOf(id).filter(isCode(document, _)).foreach(dirty += _)
            case _ if known.contains(id) => drop(id)
            case _                       => ()
        }

      if dirty.size != before then wake()

  private def request(block: NodeId): Unit =
    session.document.node(block) match
      case Some(code: CodeBlockNode) =>
        val revision = session.state.revision
        pending.remove(block).foreach(_.dispose())
        requested(block) = revision
        val subscription = highlighter.highlight(
          HighlightRequest(
            block,
            revision,
            code.info.language.map(_.value),
            CodeBlockNode.textOf(code, session.document)
          ),
          accept
        )
        // A local highlighter has already answered by now; only an answer still to come is pending.
        if !answered.get(block).contains(revision) then pending(block) = subscription
      case _ => drop(block)

  private def accept(result: HighlightResult): Unit =
    if !disposed then
      if !requested.get(result.block).contains(result.revision) then stale += 1
      else
        session.document.node(result.block) match
          case Some(code: CodeBlockNode)
              if CodeBlockNode.textOf(code, session.document) == result.text =>
            pending.remove(result.block)
            answered(result.block) = result.revision
            if result.skipped.isDefined then
              if shown.remove(result.block) then sink.clear(result.block)
            else if sink.show(result) then shown += result.block
            else dirty += result.block
          case _ => stale += 1

  private def drop(block: NodeId): Unit =
    dirty -= block
    pending.remove(block).foreach(_.dispose())
    requested -= block
    answered -= block
    highlighter.forget(block)
    if shown.remove(block) then sink.clear(block)

  private def markAll(): Unit =
    session.document.inDocumentOrder.foreach {
      case code: CodeBlockNode => dirty += code.id
      case _                   => ()
    }

  private def known: Set[NodeId] = shown.toSet ++ requested.keySet ++ dirty

  private def isCode(document: DocumentRead, id: NodeId): Boolean =
    document.node(id).exists(_.isInstanceOf[CodeBlockNode])
