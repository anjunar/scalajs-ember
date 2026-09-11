package ember.editor.browser

import ember.editor.core.*
import ember.editor.html.HtmlSupport
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** Whether this editor accepts changes at all.
  *
  * §22 keeps two things apart that look like one: "Readonly-Policy verhindert auch
  * programmgesteuerte User-Editing-Commands; Fokusfaehigkeit und Editierbarkeit sind getrennte
  * Entscheidungen." A readonly editor is still focusable, still selectable, still readable by a
  * screen reader -- it just refuses to change.
  */
enum EditorMode:
  case Editable, ReadOnly

/** The controller's state machine (§15.2).
  *
  * > Zustaende: `Detached → Hydrating → Ready ↔ Composing → Recovering`, terminal `Disposed`.
  *
  * P22 implements `Ready`. `Composing` is entered and left, and while it lasts nothing is taken
  * over -- the full protocol with its write lock and its completion rules is P23. `Recovering` is
  * entered when a native change cannot be imported; what P22 owes there is to notice, to stop
  * claiming input, and to keep the text (§15.4). Repair is P23.
  */
enum ControllerState:
  case Detached, Ready, Composing, Recovering, Disposed

/** What the controller did with one event. */
enum InputOutcome:

  /** A command took it. The native action is prevented. */
  case TakenOver(intent: InputIntent)

  /** The editor deliberately said no -- readonly, schema, limit. Also prevented.
    *
    * P22's risk list is explicit that this is not the same as "unhandled":
    * "Readonly-/Limit-/Schema-Reject verhindert native Ersatzmutation." A refusal that let the
    * event through would leave the browser editing a document the model rejected.
    */
  case Refused(intent: InputIntent, reason: String)

  /** Nothing here answers this. The browser does it, and `input` imports the result. */
  case LeftNative(intent: InputIntent)

  /** The event did not belong to this editor (§15.2, Event-Ownership). */
  case NotOurs

  /** A native change was read back into the document. */
  case Imported(node: NodeId, splice: TextSplice)

  /** Already handled in `beforeinput`; this is its echo. */
  case Deduplicated

  /** A native change could not be expressed as a document change (§15.4). */
  case Unimported(reason: String)

  /** The controller is not in a state that processes input. */
  case Idle(state: ControllerState)

  /** A change was refused because a composition is running (§15.3). */
  case Busy(session: Long)

  /** A `keydown` that is plainly text, and therefore not the keyboard layer's business.
    *
    * §15.2: "Text generell ueber Input-Pipeline." A letter without a modifier is never a
    * shortcut, and the controller does not even consult its table for one -- so this is not a
    * decision and is not reported to observers.
    */
  case NotAShortcut(key: String)

  /** Whether the caller must call `preventDefault`.
    *
    * The rule P22 names, in one place: "Event-Ownership und erfolgreiche Modelluebernahme
    * '''oder bewusste Ablehnung''' bestimmen preventDefault, nicht die blosse Existenz eines
    * Handlers."
    */
  def preventsDefault: Boolean = this match
    case TakenOver(_)  => true
    case Refused(_, _) => true
    case _             => false

  /** Whether this is worth telling an observer about.
    *
    * A keystroke the controller never looked at is not news. Reporting one per character would
    * bury the outcomes that matter -- a refusal, a failed import -- under the typing.
    */
  def isNotable: Boolean = this match
    case NotAShortcut(_) => false
    case _               => true

/** What happened to a native input session (§15.3).
  *
  * Reported rather than acted on, because the two things that care live above this module: the
  * history, which makes one undo group of a composition (§14), and the application, which may
  * want to say that something is being typed.
  */
enum CompositionEvent:
  case Started(session: Long, region: ProtectedRegion)
  case Finished(session: Long, released: Vector[IntentOutcome])
  case Discarded(session: Long, reason: String, dropped: Vector[String])

/** Browser editing: events in, commands out, and nothing in between that touches the DOM.
  *
  * ==What it never does==
  *
  * It does not write to the document DOM, and it does not call `execCommand`. P22's acceptance
  * says so -- "keine direkte Feature-DOM-Manipulation oder execCommand" -- and the reason is
  * §15.1: the projection owns the DOM. Every change here becomes a command, the command becomes
  * a transaction, the transaction becomes a commit, and UI does the writing.
  *
  * ==The three routes in, and why there are three==
  *
  *   - '''`beforeinput`, cancelable.''' The good case. The intent is known before anything
  *     happens, a command runs, and the native action is prevented.
  *   - '''`input`.''' What is left when `beforeinput` was not cancelable or never came. The DOM
  *     is already ahead; [[NativeInputReader]] brings the model to it. §15.2: "IME, Autokorrektur,
  *     Spracherkennung und Browserfunktionen sind nicht vollstaendig durch `keydown` steuerbar."
  *   - '''`keydown`.''' Shortcuts and structural keys only. Text never comes through here --
  *     §15.2: "Text generell ueber Input-Pipeline" -- because a `keydown` table cannot see
  *     dictation, autocorrect or a mobile keyboard.
  *
  * ==Exactly once==
  *
  * The same user action reaches the editor twice: `beforeinput` then `input`, or `paste` then
  * `beforeinput`. [[InputOperationLog]] is what makes the second one a no-op, and it is a log
  * rather than a flag because the events are not reliably paired.
  */
final class BrowserInputController private (
    session: EditorSession,
    view: DocumentView,
    port: SelectionPort,
    reader: NativeInputReader,
    guard: ProjectionWriteGuard,
    mutations: NativeMutationObserver,
    recovery: RecoveryController,
    queue: DeferredIntentQueue,
    bindings: InputBindings,
    keyboard: KeyboardBindings,
    tabPolicy: TabPolicy,
    busyPolicy: BusyPolicy,
    initialMode: EditorMode
):

  private var currentState: ControllerState = ControllerState.Detached
  private var currentMode: EditorMode       = initialMode
  private val operations                    = new InputOperationLog()
  private var escapeArmed                   = false
  private var observers                     = Vector.empty[(Long, InputOutcome => Unit)]
  private var compositionObservers          = Vector.empty[(Long, CompositionEvent => Unit)]
  private var current: Option[CompositionSession] = None
  private var nextHandle                    = 0L

  private var beforeInputListener: js.Function1[dom.Event, Unit] = null
  private var inputListener: js.Function1[dom.Event, Unit]       = null
  private var keyDownListener: js.Function1[dom.Event, Unit]     = null
  private var compositionStart: js.Function1[dom.Event, Unit]    = null
  private var compositionEnd: js.Function1[dom.Event, Unit]      = null
  private var focusOut: js.Function1[dom.Event, Unit]            = null

  def state: ControllerState = currentState

  def mode: EditorMode = currentMode

  def scope: BrowserScope = port.scope

  /** Switches between editable and readonly.
    *
    * Writes `contenteditable` too: that attribute '''is''' the editability decision as far as the
    * browser is concerned, and leaving it on while refusing every change would mean fighting the
    * engine on every keystroke instead of telling it once.
    */
  def setMode(next: EditorMode): Unit =
    currentMode = next
    if currentState != ControllerState.Detached && currentState != ControllerState.Disposed then
      applyEditingAttributes()

  // -----------------------------------------------------------------------------------------
  // Lebenszyklus
  // -----------------------------------------------------------------------------------------

  /** Connects the controller to its host.
    *
    * §17 decides '''when''' this may happen: "Nach lokal vollstaendig erfolgreichem Claim und
    * aeusserem Hydration-Abschluss werden Controller und `contenteditable` aktiviert." The
    * controller does not make that decision; [[EditorActivation]] does, and the caller acts on it.
    */
  def attach(): Unit =
    if currentState == ControllerState.Detached then
      val host = scope.host

      beforeInputListener = event => dispatchEvent(event, handleBeforeInput)
      inputListener       = event => dispatchEvent(event, handleInput)
      keyDownListener     = event => dispatchEvent(event, handleKeyDown)
      compositionStart    = _ => onCompositionStart()
      compositionEnd      = _ => onCompositionEnd()
      // §15.3: "Blur erfasst noch offene native Aenderung." Focus leaving is not a reason to
      // throw a half-typed word away -- it is a reason to take it.
      focusOut            = _ => if current.isDefined then onCompositionEnd()

      host.addEventListener("beforeinput", beforeInputListener)
      host.addEventListener("input", inputListener)
      host.addEventListener("keydown", keyDownListener)
      host.addEventListener("compositionstart", compositionStart)
      host.addEventListener("compositionend", compositionEnd)
      host.addEventListener("focusout", focusOut)

      mutations.start()
      currentState = ControllerState.Ready
      applyEditingAttributes()

  def dispose(): Unit =
    if currentState != ControllerState.Disposed then
      val host = scope.host
      if beforeInputListener != null then host.removeEventListener("beforeinput", beforeInputListener)
      if inputListener != null then host.removeEventListener("input", inputListener)
      if keyDownListener != null then host.removeEventListener("keydown", keyDownListener)
      if compositionStart != null then host.removeEventListener("compositionstart", compositionStart)
      if compositionEnd != null then host.removeEventListener("compositionend", compositionEnd)
      if focusOut != null then host.removeEventListener("focusout", focusOut)
      // §15.3: "Dispose raeumt auf und meldet ggf. nicht abgeschlossene Eingabe an den Host."
      discardComposition("dispose")
      mutations.stop()
      host.removeAttribute("contenteditable")
      host.removeAttribute("tabindex")
      host.removeAttribute("aria-readonly")
      beforeInputListener = null
      inputListener = null
      keyDownListener = null
      compositionStart = null
      compositionEnd = null
      focusOut = null
      operations.clear()
      observers = Vector.empty
      compositionObservers = Vector.empty
      currentState = ControllerState.Disposed

  /** The editing surface's own attributes.
    *
    * §22: "Die aktive Editierflaeche erhaelt einen zugaenglichen Namen, `role="textbox"` und
    * `aria-multiline="true"`." They belong to the surface and not to any node, which is why the
    * render profile does not carry them -- a node does not know it is inside an editor.
    */
  private def applyEditingAttributes(): Unit =
    val host = scope.host
    host.setAttribute("contenteditable", if currentMode == EditorMode.Editable then "true" else "false")
    host.setAttribute("role", "textbox")
    host.setAttribute("aria-multiline", "true")
    // `tabindex` in '''both''' modes, and a browser test is why. `contenteditable="false"` takes
    // the element out of the tab order, so a readonly editor became unreachable by keyboard --
    // while §22 says the opposite: "Fokusfaehigkeit und Editierbarkeit sind getrennte
    // Entscheidungen." Setting it unconditionally also means switching modes does not move the
    // focus, which would throw a reader out of the text they were in.
    host.setAttribute("tabindex", "0")
    if currentMode == EditorMode.ReadOnly then host.setAttribute("aria-readonly", "true")
    else host.removeAttribute("aria-readonly")

  /** Reports what the controller did with each event.
    *
    * Not only for tests. §15.4 asks for a "verstaendliche Statusmeldung" when recovery starts,
    * and §16 for a visible refusal when a format boundary rejects an edit -- both need to know
    * that something was refused, and the controller is the only place that knows.
    */
  def onOutcome(listener: InputOutcome => Unit): Subscription =
    if currentState == ControllerState.Disposed then Subscription.cancelled
    else
      nextHandle += 1
      val handle = nextHandle
      observers = observers :+ (handle, listener)
      Subscription(() => observers = observers.filterNot(_._1 == handle))

  private def announce(outcome: InputOutcome): InputOutcome =
    observers.foreach { (_, listener) =>
      try listener(outcome)
      catch case _: Throwable => ()
    }
    outcome

  private def dispatchEvent(event: dom.Event, handler: dom.Event => InputOutcome): Unit =
    val outcome = handler(event)
    if outcome.isNotable then announce(outcome): Unit
    if outcome.preventsDefault then event.preventDefault()

  // -----------------------------------------------------------------------------------------
  // beforeinput
  // -----------------------------------------------------------------------------------------

  def handleBeforeInput(event: dom.Event): InputOutcome =
    if currentState != ControllerState.Ready then InputOutcome.Idle(currentState)
    else if !owns(event) then InputOutcome.NotOurs
    else
      val input     = event.asInstanceOf[dom.InputEvent]
      val inputType = input.inputType.toString
      val intent = BeforeInputAdapter.intentOf(
        inputType,
        Option(input.data).filter(_ != null),
        transferTextOf(input)
      )

      if currentMode == EditorMode.ReadOnly then
        // A refusal, not an omission. Letting it through would have the browser edit a document
        // that said no -- P22's risk list calls that out by name.
        if intent.editsDocument then
          InputOutcome.Refused(intent, "Der Editor ist readonly.")
        else InputOutcome.LeftNative(intent)
      // A non-cancelable event has already decided. Running the command now would apply it twice:
      // once through the model and once through the browser.
      else if !event.cancelable then InputOutcome.LeftNative(intent)
      else
        run(bindings.resolveAll(intent)) match
          case Outcome.Handled =>
            operations.record(inputType): Unit
            // The model moved the caret; the DOM still shows the old one, because the native
            // action is about to be prevented.
            port.sync(): Unit
            InputOutcome.TakenOver(intent)

          // Nobody wanted it. §12: `Pass` is side-effect free, so the document is untouched and
          // the browser may do what it would have done.
          case Outcome.Passed          => InputOutcome.LeftNative(intent)
          case Outcome.Rejected(error) => InputOutcome.Refused(intent, error)

  // -----------------------------------------------------------------------------------------
  // input
  // -----------------------------------------------------------------------------------------

  def handleInput(event: dom.Event): InputOutcome =
    if !owns(event) then InputOutcome.NotOurs
    // §15.3: "Native Zwischenstaende werden als zusammengehoerige Transaktionen uebernommen."
    // The model follows the composition as it goes, tagged as its own so the gate lets it
    // through and the rich-text profile holds back its merges (§8.2).
    else if currentState == ControllerState.Composing then
      importNative(CompositionGate.meta("composition-input"))
    else if currentState != ControllerState.Ready then InputOutcome.Idle(currentState)
    else
      val inputType = event match
        case input: dom.InputEvent => input.inputType.toString
        case _                     => ""

      if operations.consume(inputType) then InputOutcome.Deduplicated
      else importNative(NativeInput)

  /** Brings the model to what the DOM already says.
    *
    * @param hint
    *   a run to look at before asking the selection. Blur is why it exists: WebKit drops the
    *   document selection when focus leaves, so the reader had nothing to anchor on and a
    *   half-composed word was lost -- exactly the case §15.3 wants kept ("Blur erfasst noch
    *   offene native Aenderung"). The composition knows where it started; the selection may not
    *   be there any more.
    */
  private def importNative(meta: TransactionMeta, hint: Option[NodeId] = None): InputOutcome =
    readNative(hint) match
      case NativeImport.Nothing => InputOutcome.Deduplicated

      case NativeImport.Text(node, splice) =>
        session.update(meta) { transaction =>
          transaction.spliceText(node, splice.start, splice.deleteCount, splice.inserted): Unit
        } match
          case Right(_) =>
            current = current.map(_.withCapture(node, splice.inserted))
            // The caret is wherever the browser left it, and that is the truth now. Not while a
            // composition runs: §15.3 forbids a selection write there, and the browser is the one
            // holding the caret anyway.
            if current.isEmpty then port.importNative(): Unit
            InputOutcome.Imported(node, splice)
          case Left(error) =>
            enterRecovery()
            InputOutcome.Unimported(error.render)

      // The text is importable; the view is not what the projection left behind. Repairing the
      // run is §15.4's "laesst UI diesen Bereich aus dem gueltigen State neu aufbauen" -- and the
      // caret is computed rather than read, because the split DOM cannot be addressed with the
      // one-text-node assumption the position map is built on.
      case NativeImport.SplitRun(node, splice) =>
        val caret = Point.textBefore(node, splice.start + splice.inserted.length)
        session.update(meta) { transaction =>
          transaction.spliceText(node, splice.start, splice.deleteCount, splice.inserted): Unit
          transaction.select(RangeSelection.caret(caret)): Unit
        } match
          case Right(_) =>
            view.resetRun(node): Unit
            // §15.3: no selection write while a composition holds the caret.
            if current.isEmpty then port.sync(WriteIntent.Explicit): Unit
            InputOutcome.Imported(node, splice)
          case Left(error) =>
            enterRecovery()
            InputOutcome.Unimported(error.render)

      case NativeImport.Unimportable(reason, _) =>
        enterRecovery()
        InputOutcome.Unimported(reason)

  private def readNative(hint: Option[NodeId]): NativeImport =
    val document = session.document
    hint
      .filter(id => document.node(id).exists(_.isInstanceOf[TextNode]))
      .map(reader.readRun(_, document))
      .filterNot(_ == NativeImport.Nothing)
      .getOrElse(reader.read(document))

  private def enterRecovery(): Unit =
    if currentState == ControllerState.Ready then currentState = ControllerState.Recovering

  /** Leaves recovery. P23 owns the repair; this is what a caller uses once it has done it. */
  def resume(): Unit =
    if currentState == ControllerState.Recovering then currentState = ControllerState.Ready

  // -----------------------------------------------------------------------------------------
  // keydown
  // -----------------------------------------------------------------------------------------

  def handleKeyDown(event: dom.Event): InputOutcome =
    if currentState != ControllerState.Ready then InputOutcome.Idle(currentState)
    else if !owns(event) then InputOutcome.NotOurs
    else
      val key      = event.asInstanceOf[dom.KeyboardEvent]
      val shortcut = Shortcut.of(key)

      // §22's readonly, on the keyboard side. WebKit still navigates back on Backspace in a
      // non-editable element, and a browser test found it by losing the whole page -- so a
      // readonly editor refuses the editing keys outright instead of hoping nothing happens.
      if currentMode == EditorMode.ReadOnly && KeyboardRule.editsOrNavigates(shortcut.key) then
        InputOutcome.Refused(InputIntent.Unknown(shortcut.key), "Der Editor ist readonly.")
      else if !KeyboardRule.isShortcutCandidate(shortcut) then
        // Plain text. §15.2 routes it through `beforeinput`, where autocorrect, dictation and a
        // mobile keyboard also arrive -- none of which a keydown table would ever see.
        escapeArmed = false
        InputOutcome.NotAShortcut(shortcut.key)
      else if shortcut.key == TabRule.TabKey && !TabRule.handlesTab(tabPolicy, escapeArmed) then
        // §22: no permanent keyboard trap. Either this editor never takes Tab, or Escape has just
        // opened the door -- and either way the focus moves on natively.
        escapeArmed = false
        InputOutcome.LeftNative(InputIntent.Unknown("Tab"))
      else
        escapeArmed = TabRule.arms(shortcut.key)

        val named     = InputIntent.Unknown(shortcut.key)
        val candidates = keyboard.resolveAll(shortcut)

        if candidates.isEmpty then InputOutcome.LeftNative(named)
        else if currentMode == EditorMode.ReadOnly then
          InputOutcome.Refused(named, "Der Editor ist readonly.")
        else
          run(candidates) match
            case Outcome.Handled =>
              port.sync(): Unit
              InputOutcome.TakenOver(named)
            case Outcome.Passed          => InputOutcome.LeftNative(named)
            case Outcome.Rejected(error) => InputOutcome.Refused(named, error)

  // -----------------------------------------------------------------------------------------
  // -----------------------------------------------------------------------------------------
  // Composition (§15.3)
  // -----------------------------------------------------------------------------------------

  /** The composition in progress, if there is one. */
  def composition: Option[CompositionSession] = current

  /** Whether an independent change would be refused right now. */
  def isBusy: Boolean = current.isDefined

  /** Offers a change that is not the composition's.
    *
    * §15.3 allows two answers and the application picks: refuse now, or queue with a bookmark and
    * re-validate later. Outside a composition the change simply runs.
    */
  def offer(intent: DeferredIntent): Either[CompositionBusy, IntentOutcome] =
    current match
      case None => Right(DeferredIntentQueue.runNow(session, intent))
      case Some(open) =>
        val busy = CompositionBusy(open.id, DiagnosticPath.Root)
        busyPolicy match
          case BusyPolicy.Reject => Left(busy)
          case BusyPolicy.Defer  => queue.offer(intent, busy).map(_ => IntentOutcome.Applied(intent.label))

  /** Notified when a composition begins, ends or is thrown away.
    *
    * The history group hangs on this (§14: a composition is '''one''' undo step), and it hangs
    * outside this module because §7 keeps history out of `browser`. The wiring lives where the
    * features do.
    */
  def onComposition(listener: CompositionEvent => Unit): Subscription =
    if currentState == ControllerState.Disposed then Subscription.cancelled
    else
      nextHandle += 1
      val handle = nextHandle
      compositionObservers = compositionObservers :+ (handle, listener)
      Subscription(() => compositionObservers = compositionObservers.filterNot(_._1 == handle))

  private def announceComposition(event: CompositionEvent): Unit =
    compositionObservers.foreach { (_, listener) =>
      try listener(event)
      catch case _: Throwable => ()
    }

  private def onCompositionStart(): Unit =
    if currentState == ControllerState.Ready then
      val open = CompositionSession.start(session.document, session.selection, session.state.revision)
      current = Some(open)
      currentState = ControllerState.Composing

      // The barrier first, then the announcement: a listener that edits on `Started` would
      // otherwise write into an unprotected region.
      guard.protect(open.region)
      mutations.drain()
      announceComposition(CompositionEvent.Started(open.id, open.region))

  /** Ends a composition and takes what it left behind.
    *
    * §15.3's completion protocol, in the order it states:
    *
    *   1. Release the lease -- ui-core requires it before the projection runs again.
    *   1. Read the DOM once, revisioned, so a trailing `input` cannot insert the same text twice.
    *   1. Normalise: the merges deferred during the session run now, on a state nobody is typing
    *      into.
    *   1. Project the final state.
    *   1. Restore the selection '''only''' while the focus is still inside the editor.
    *   1. Release the queued intents, each re-validated against the document as it is now.
    */
  private def onCompositionEnd(): Unit =
    current match
      case None => ()
      case Some(open) =>
        guard.release()
        current = None
        currentState = ControllerState.Ready

        // The run the composition started in, so that a lost selection does not lose the text.
        val started = open.selection.collect { case range: RangeSelection => range.focus.owner }
        val taken   = importNative(NativeInput, started)
        mutations.drain()

        // A last chance for the view to be wrong: an IME can leave structure behind that no
        // splice describes. Repairing here, while the guard is down, is the only moment it can
        // be done without fighting the composition.
        val repaired = recovery.repair()
        repaired match
          case RecoveryOutcome.Exhausted(_) => enterRecovery()
          case _                            => ()

        if scope.focusWithin then port.sync(): Unit

        val released = queue.release(session)
        announceComposition(CompositionEvent.Finished(open.id, released))
        taken: Unit

  /** Throws the session away without taking its text. Blur and dispose. */
  private def discardComposition(reason: String): Unit =
    current.foreach { open =>
      guard.release()
      current = None
      if currentState == ControllerState.Composing then currentState = ControllerState.Ready
      val dropped = queue.clear()
      announceComposition(CompositionEvent.Discarded(open.id, reason, dropped))
    }

  /** What a chain of bindings came to. */
  private enum Outcome:
    case Handled
    case Passed
    case Rejected(error: String)

  /** Runs bindings until one takes the action or one refuses it.
    *
    * A refusal stops the chain: it is an answer, and §12's `Pass` is explicitly not one. Letting
    * the next binding try after a rejection would mean a schema limit could be stepped around by
    * whoever registered later.
    */
  private def run(
      candidates: Vector[EditorSession => Either[UpdateError, DispatchOutcome]]
  ): Outcome =
    var index  = 0
    var result: Outcome = Outcome.Passed
    while index < candidates.length && result == Outcome.Passed do
      candidates(index)(session) match
        case Right(outcome) if outcome.result == CommandResult.Handled => result = Outcome.Handled
        case Right(_)                                                  => ()
        case Left(error) => result = Outcome.Rejected(error.render)
      index += 1
    result

  // -----------------------------------------------------------------------------------------
  // Ownership
  // -----------------------------------------------------------------------------------------

  /** Whether this event is this editor's to handle (§15.2).
    *
    * Two questions, and both have to be yes: the target is inside the host, and it is not inside
    * something the editor renders but does not own. A textarea in an atom view produces real
    * `beforeinput` events that bubble to the editing host, and treating them as document edits
    * would type the user's note into the document.
    */
  private def owns(event: dom.Event): Boolean =
    Option(event.target)
      .collect { case node: dom.Node => node }
      .exists { node =>
        scope.contains(node) && port.positions.atomAround(node, session.document).isEmpty
      }

  private def transferTextOf(event: dom.InputEvent): Option[String] =
    Option(event.dataTransfer).map(_.getData("text/plain")).filter(_.nonEmpty)

  /** Origin for a change the browser made and the model is catching up with.
    *
    * `User`, because a person typed it -- the editor merely learned about it late. §14 groups by
    * origin, and calling this `System` would put a keystroke outside the undo history.
    */
  private val NativeInput: TransactionMeta =
    TransactionMeta(origin = Origin.User, label = Some("native-input"))

object BrowserInputController:

  /** Builds a controller and connects it.
    *
    * `semantics` is what the recovery compares the view against (§15.4) -- the same description
    * that rendered it. Without one there is nothing to check, and the controller then repairs
    * nothing rather than guessing.
    */
  def attachTo(
      session: EditorSession,
      view: DocumentView,
      port: SelectionPort,
      bindings: InputBindings,
      keyboard: KeyboardBindings = KeyboardBindings.empty,
      tabPolicy: TabPolicy = TabPolicy.LeavesEditor,
      mode: EditorMode = EditorMode.Editable,
      semantics: Option[HtmlSupport] = None,
      busyPolicy: BusyPolicy = BusyPolicy.Reject
  ): BrowserInputController =
    val controller =
      detached(session, view, port, bindings, keyboard, tabPolicy, mode, semantics, busyPolicy)
    controller.attach()
    controller

  /** Without the listeners. For a caller that drives the handlers itself, and for tests. */
  def detached(
      session: EditorSession,
      view: DocumentView,
      port: SelectionPort,
      bindings: InputBindings,
      keyboard: KeyboardBindings = KeyboardBindings.empty,
      tabPolicy: TabPolicy = TabPolicy.LeavesEditor,
      mode: EditorMode = EditorMode.Editable,
      semantics: Option[HtmlSupport] = None,
      busyPolicy: BusyPolicy = BusyPolicy.Reject
  ): BrowserInputController =
    new BrowserInputController(
      session,
      view,
      port,
      new NativeInputReader(port.positions, port.scope),
      new ProjectionWriteGuard(view, port.scope),
      new NativeMutationObserver(port.scope.host),
      new RecoveryController(
        session,
        view,
        port.positions,
        semantics.getOrElse(HtmlSupport.empty)
      ),
      new DeferredIntentQueue(),
      bindings,
      keyboard,
      tabPolicy,
      busyPolicy,
      mode
    )

  /** The rule that refuses independent changes while a composition runs (§15.3).
    *
    * Handed to the session at construction, because §10's step 5 is where it has to run and a
    * session's rules are fixed when it is built. The controller comes later and reports itself
    * through the holder.
    */
  def busyRule(holder: CompositionHolder): PreCommitRule =
    CompositionGate.rule(() => holder.runningComposition)

/** What the pre-commit rule asks, once the controller exists.
  *
  * A session is built before its controller -- the controller needs the view, the view needs the
  * session. The rule has to be in place from the first commit, so it asks through this rather
  * than holding a controller it could not have been given.
  */
final class CompositionHolder:

  private var controller: Option[BrowserInputController] = None

  def bind(value: BrowserInputController): Unit = controller = Some(value)

  def release(): Unit = controller = None

  def runningComposition: Option[Long] = controller.flatMap(_.composition).map(_.id)
