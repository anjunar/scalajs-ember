package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** Why a write did not happen. Every one of them is a rule, not a failure. */
enum SkipReason:

  /** The projection has not caught up with the state (§11: write only at a matching revision).
    *
    * Writing earlier would address nodes by a document that the DOM does not show yet.
    */
  case StaleProjection

  /** The focus is not inside the host, and the write did not insist (§22).
    *
    * "Hintergrundupdates stehlen weder Page- noch Textarea-Fokus." A selection write into an
    * unfocused host is visible and unasked for.
    */
  case NotFocused

  /** There is no selection API for this host -- a shadow root without `getSelection` (§15.4). */
  case Unsupported

  /** The DOM already says exactly this. Writing would fire `selectionchange` for nothing. */
  case AlreadyThere

  /** The model has no selection. Clearing the browser's would take the user's caret away. */
  case NoSelection

  def render: String = this match
    case StaleProjection => "Die Projektion zeigt noch eine aeltere Revision."
    case NotFocused      => "Der Fokus liegt nicht im Editing-Host."
    case Unsupported     => "Fuer diesen Host gibt es keine lesbare Selection."
    case AlreadyThere    => "Die Auswahl steht bereits so im DOM."
    case NoSelection     => "Das Modell hat keine Auswahl."

/** What a write did. */
enum SelectionWrite:
  case Written
  case Skipped(reason: SkipReason)
  case Failed(problem: PositionProblem)

/** What a read found. */
enum SelectionReading:

  /** No selection in this document at all. */
  case Absent

  /** There is one, but not in this host. §11: the port reads only within its editing host. */
  case Outside

  /** There is one in this host, and it does not correspond to a model position. */
  case Unmappable(problem: PositionProblem)

  /** It is inside something the editor renders but does not own (§15.2).
    *
    * A native control inside an atom view, today. The document does have a position for the atom
    * -- [[DomPositionMap.toPoint]] gives the boundary in its parent -- but a caret that went into
    * a textarea is that textarea's caret, and importing it would overwrite the document selection
    * every time someone clicked a widget.
    */
  case Foreign(owner: NodeId)

  case Mapped(selection: RangeSelection)

  def toOption: Option[RangeSelection] = this match
    case Mapped(selection) => Some(selection)
    case _                 => None

/** Whether a write insists on happening. */
enum WriteIntent:

  /** Write only into a focused host. The right default, and the one §22 asks for. */
  case FollowFocus

  /** Write regardless of focus -- a deliberate "select this" from a command.
    *
    * This port never '''calls''' focus; that decision is [[FocusController]]'s. But placing a
    * selection inside an editable host moves the focus there anyway in Chromium, and a browser
    * test found it. So `Explicit` is not "write without touching focus" -- it is "write, and
    * accept that the engine may follow". Which is why it is not the default.
    */
  case Explicit

/** The rule for whether a selection may be written, as a function of what the caller knows.
  *
  * Separated from the port because it is the part worth testing without a browser: the port
  * around it is DOM plumbing, and this is the policy §11 and §22 state.
  */
object SelectionWriteGate:

  /** `None` means: go ahead. */
  def decide(
      projected: Revision,
      state: Revision,
      focusWithin: Boolean,
      intent: WriteIntent,
      capability: SelectionCapability
  ): Option[SkipReason] =
    if !capability.isSupported then Some(SkipReason.Unsupported)
    // §11: "nur nach passender Projection-Revision schreiben". Not "at least as new" -- a
    // projection ahead of the state would be just as wrong, and cannot happen.
    else if projected != state then Some(SkipReason.StaleProjection)
    else if !focusWithin && intent == WriteIntent.FollowFocus then Some(SkipReason.NotFocused)
    else None

/** The browser side of §11: read the native selection, write the model's, and never loop.
  *
  * ==The two directions are not symmetric==
  *
  * Reading is nearly free and always allowed: `selectionchange` reports where the user went, and
  * §11 lets arrow navigation run natively and imports the result. Writing is the dangerous half.
  * It can move a caret out from under someone, steal focus, and -- worst -- trigger the very
  * event that made it happen. So every write passes [[SelectionWriteGate]] first, and every write
  * records what it wrote.
  *
  * ==How the loop is broken==
  *
  * §15.2: "eigene Selection-Schreibvorgaenge anhand Revision und tatsaechlichem Wert erkennen."
  * Both halves matter. A synchronous flag does not work, because `selectionchange` is delivered
  * asynchronously, after the flag is long reset. A revision alone does not work either, because
  * the user can move the caret without the revision changing. So the port remembers the four DOM
  * values it last wrote together with the revision it wrote them for, and an event carrying
  * exactly those is its own echo.
  *
  * ==What it does not do==
  *
  * It does not focus anything, and it does not decide when a dialog gives focus back. That is
  * [[FocusController]], and keeping the two apart is what makes "write the selection but do not
  * take the focus" expressible at all.
  */
final class SelectionPort private (
    session: EditorSession,
    view: DocumentView,
    val scope: BrowserScope,
    val positions: DomPositionMap
):

  private var lastWrite: Option[WrittenSelection] = None
  private var listeners = Vector.empty[(Long, Option[Selection] => Unit)]
  private var nextHandle = 0L
  private var nativeListener: js.Function1[dom.Event, Unit] = null
  private var disposedFlag = false

  def isDisposed: Boolean = disposedFlag

  // -----------------------------------------------------------------------------------------
  // Lesen
  // -----------------------------------------------------------------------------------------

  /** The native selection as a model selection, or why it is not one. */
  def read(): SelectionReading =
    scope.selection match
      case None => SelectionReading.Absent
      case Some(native) =>
        val anchor = Option(native.anchorNode)
        val focus  = Option(native.focusNode)
        (anchor, focus) match
          case (Some(anchorNode), Some(focusNode)) =>
            if !scope.contains(anchorNode) || !scope.contains(focusNode) then
              SelectionReading.Outside
            else
              val document = session.document
              // §15.2s Event-Ownership. Drei Fragen, nicht eine: "Composed Event-Target, aktives
              // Element und tatsaechlich betroffener Editing-Host muessen zusammenpassen."
              //
              // Das aktive Element steht zuerst, und ein Firefox-Lauf hat gezeigt warum: waehrend
              // ein natives Feld in einem Atom den Fokus hat, setzt Firefox die Dokumentauswahl
              // an den Anfang des Editing-Hosts. Der Anker liegt dann voellig woanders als der
              // Benutzer, und eine Pruefung nur der Endpunkte haette den Caret an den
              // Dokumentanfang importiert.
              val foreign = scope.activeElement
                .flatMap(active => positions.atomAround(active, document))
                .orElse(positions.atomAround(anchorNode, document))
                .orElse(positions.atomAround(focusNode, document))

              if foreign.isDefined then SelectionReading.Foreign(foreign.get)
              else
                mapBoth(anchorNode, native.anchorOffset, focusNode, native.focusOffset, document)
          case _ => SelectionReading.Absent

  private def mapBoth(
      anchorNode: dom.Node,
      anchorOffset: Int,
      focusNode: dom.Node,
      focusOffset: Int,
      document: DocumentRead
  ): SelectionReading =
    val mapped =
      for
        anchorPoint <- positions.toPoint(DomPosition(anchorNode, anchorOffset), document)
        focusPoint  <- positions.toPoint(DomPosition(focusNode, focusOffset), document)
      yield RangeSelection(anchorPoint, focusPoint)

    mapped match
      case Right(selection) => SelectionReading.Mapped(selection)
      case Left(problem)    => SelectionReading.Unmappable(problem)

  /** Reads and commits, if there is something to commit.
    *
    * A selection-only transaction, marked so the history does not record it: §14 keeps the
    * selection '''with''' a document snapshot, and a caret move is not a step to undo.
    */
  def importNative(): SelectionReading =
    val reading = read()
    reading match
      case SelectionReading.Mapped(selection) if session.selection.contains(selection) => reading
      case SelectionReading.Mapped(selection) =>
        session.update(SelectionPort.importMeta)(_.select(selection): Unit) match
          case Right(_) => announce(Some(selection))
          case Left(_)  =>
            // A selection the core rejects is not a reason to fight the browser: the user is
            // standing somewhere the document cannot express, and P22 decides what that means.
            ()
        reading
      case other => other

  // -----------------------------------------------------------------------------------------
  // Schreiben
  // -----------------------------------------------------------------------------------------

  /** Writes the session's current selection into the DOM. */
  def sync(intent: WriteIntent = WriteIntent.FollowFocus): SelectionWrite =
    write(session.selection, intent)

  /** Writes a selection into the DOM, or says why it did not. */
  def write(
      selection: Option[Selection],
      intent: WriteIntent = WriteIntent.FollowFocus
  ): SelectionWrite =
    SelectionWriteGate.decide(
      view.projectedRevision,
      session.state.revision,
      scope.focusWithin,
      intent,
      scope.capability
    ) match
      case Some(reason) => SelectionWrite.Skipped(reason)
      case None =>
        selection match
          case None                     => SelectionWrite.Skipped(SkipReason.NoSelection)
          case Some(range: RangeSelection) => writePoints(range.anchor, range.focus)
          case Some(nodes: NodeSelection)  => writeNodes(nodes)
          case Some(_)                     =>
            // A foreign selection kind (§11 keeps the contract open). It has a mapper in the
            // core but no DOM representation here, and inventing one would put the caret
            // somewhere its owner never meant.
            SelectionWrite.Skipped(SkipReason.NoSelection)

  private def writePoints(anchor: Point, focus: Point): SelectionWrite =
    val document = session.document
    (positions.toDom(anchor, document), positions.toDom(focus, document)) match
      case (Left(problem), _) => SelectionWrite.Failed(problem)
      case (_, Left(problem)) => SelectionWrite.Failed(problem)
      case (Right(anchorAt), Right(focusAt)) =>
        scope.selection match
          case None => SelectionWrite.Skipped(SkipReason.Unsupported)
          case Some(native) =>
            if matches(native, anchorAt, focusAt) then SelectionWrite.Skipped(SkipReason.AlreadyThere)
            else
              // `collapse` then `extend`, not two ranges: this is the pair that keeps anchor and
              // focus apart, so a backward selection stays backward and the next arrow key moves
              // the end the user was moving (§11, §17.7).
              native.collapse(anchorAt.node, anchorAt.offset)
              if anchorAt != focusAt then native.extend(focusAt.node, focusAt.offset)
              lastWrite = Some(
                WrittenSelection(
                  anchorAt.node,
                  anchorAt.offset,
                  focusAt.node,
                  focusAt.offset,
                  session.state.revision
                )
              )
              SelectionWrite.Written

  /** A node selection as the span that covers it.
    *
    * §11 keeps `NodeSelection` as its own kind because it '''is''' one -- several selected images
    * are not a text range. The browser has no such concept, so what it gets is the range from
    * before the first node to after the last, in document order. Reading it back gives a range,
    * not the node set; the model keeps the node set, and that asymmetry is the honest one.
    */
  private def writeNodes(selection: NodeSelection): SelectionWrite =
    val document = session.document
    val bounds = selection.nodes.toVector.flatMap { id =>
      document.parentOf(id).flatMap { parent =>
        document.node(parent) match
          case Some(element: ElementNode) =>
            val index = element.children.indexOf(id)
            Option.when(index >= 0)(
              (Point.childrenBefore(parent, index), Point.childrenAfter(parent, index + 1))
            )
          case _ => None
      }
    }

    if bounds.isEmpty then SelectionWrite.Skipped(SkipReason.NoSelection)
    else
      val first = bounds.map(_._1).minBy(identity)(using pointOrder(document))
      val last  = bounds.map(_._2).maxBy(identity)(using pointOrder(document))
      writePoints(first, last)

  private def pointOrder(document: DocumentRead): Ordering[Point] =
    (left, right) => document.comparePoints(left, right)

  private def matches(native: dom.Selection, anchor: DomPosition, focus: DomPosition): Boolean =
    (Option(native.anchorNode).exists(_ eq anchor.node)) && native.anchorOffset == anchor.offset &&
      (Option(native.focusNode).exists(_ eq focus.node)) && native.focusOffset == focus.offset

  // -----------------------------------------------------------------------------------------
  // Beobachten
  // -----------------------------------------------------------------------------------------

  /** Starts listening for `selectionchange` on this host's own document.
    *
    * The event has no target inside the host -- it fires on the document -- so the filtering
    * happens here: anything anchored outside is somebody else's selection and is left alone.
    */
  def attach(): Unit =
    if !disposedFlag && nativeListener == null then
      val handler: js.Function1[dom.Event, Unit] = _ => onNativeChange()
      nativeListener = handler
      scope.ownerDocument.addEventListener("selectionchange", handler)

  /** Called for every native selection change in this document. */
  private def onNativeChange(): Unit =
    if !disposedFlag && !isOwnEcho then importNative(): Unit

  /** Whether the current DOM selection is exactly what this port last wrote (§15.2). */
  private def isOwnEcho: Boolean =
    (scope.selection, lastWrite) match
      case (Some(native), Some(written)) =>
        written.revision == session.state.revision &&
          Option(native.anchorNode).exists(_ eq written.anchorNode) &&
          native.anchorOffset == written.anchorOffset &&
          Option(native.focusNode).exists(_ eq written.focusNode) &&
          native.focusOffset == written.focusOffset
      case _ => false

  /** Notified after a native selection was imported into the session. */
  def onImport(listener: Option[Selection] => Unit): Subscription =
    if disposedFlag then Subscription.cancelled
    else
      nextHandle += 1
      val handle = nextHandle
      listeners = listeners :+ (handle, listener)
      Subscription(() => listeners = listeners.filterNot(_._1 == handle))

  private def announce(selection: Option[Selection]): Unit =
    listeners.foreach { (_, listener) =>
      try listener(selection)
      catch case _: Throwable => ()
    }

  def dispose(): Unit =
    if !disposedFlag then
      disposedFlag = true
      if nativeListener != null then
        scope.ownerDocument.removeEventListener("selectionchange", nativeListener)
        nativeListener = null
      listeners = Vector.empty
      lastWrite = None

object SelectionPort:

  /** A selection change is not a document change and not an undo step (§14). */
  val importMeta: TransactionMeta =
    TransactionMeta(
      origin = Origin.User,
      label = Some("selectionchange"),
      history = Some(HistoryPolicy.Ignore)
    )

  def attachTo(session: EditorSession, view: DocumentView, host: dom.Element): SelectionPort =
    val scope = BrowserScope.of(host)
    val port  = new SelectionPort(session, view, scope, new DomPositionMap(view, scope))
    port.attach()
    port

  /** Without the native listener. For a caller that drives the port itself, and for tests. */
  def detached(session: EditorSession, view: DocumentView, host: dom.Element): SelectionPort =
    val scope = BrowserScope.of(host)
    new SelectionPort(session, view, scope, new DomPositionMap(view, scope))

/** What the port last wrote, and for which revision. */
private final case class WrittenSelection(
    anchorNode: dom.Node,
    anchorOffset: Int,
    focusNode: dom.Node,
    focusOffset: Int,
    revision: Revision
)
