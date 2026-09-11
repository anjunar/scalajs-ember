package ember.editor.integration

import ember.editor.browser.*
import ember.editor.browsersupport.*
import ember.editor.code.CodeExtension
import ember.editor.core.*
import ember.editor.history.History
import ember.editor.html.RenderProfile
import ember.editor.ui.{DocumentView, ViewSupport}
import ember.editor.list.ListExtension
import ember.editor.richtext.*
import ember.editor.standard.RichTextSupport
import ui.core.render.DomCursor
import org.scalajs.dom

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** Typing, in a real browser (P22).
  *
  * ==Why the document is this small==
  *
  * Because everything here is driven through real keys. `page.keyboard.type` produces genuine
  * `beforeinput` and `input` events with genuine `cancelable` flags -- which is the entire point,
  * and the one thing no synthetic event reproduces. A larger document would only make the
  * assertions harder to read.
  *
  * {{{
  * root   article
  *   p0   p        t0  "Hallo Welt"
  *   p1   p        t1  "Zweite Zeile"
  * }}}
  */
@JSExportTopLevel("editingFixtures")
object EditingFixtures:

  private val rootId = NodeId("root")

  private var session: EditorSession             = null
  private var view: DocumentView                 = null
  private var port: SelectionPort                = null
  private var controller: BrowserInputController = null
  private var host: dom.Element                  = null
  private var outcomes                           = Vector.empty[String]
  private var compositions                       = Vector.empty[String]
  private var history: History                   = null
  private var recoveryOf: RecoveryController     = null
  private val holder                             = new CompositionHolder

  // Das Atom traegt eine echte Textarea. P22s Testliste nennt "native Controls in Atoms", und
  // §15.2 verlangt die Ownership-Pruefung vor jeder Eingabeverarbeitung -- ohne ein Feld im
  // Dokument gaebe es nichts, woran sie sich zeigen liesse.
  private val views = ViewSupport.of(WidgetView) ++ RichTextSupport.views

  // -----------------------------------------------------------------------------------------
  // Lebenszyklus
  // -----------------------------------------------------------------------------------------

  @JSExport
  def mount(
      container: dom.Element,
      tabIndents: Boolean = false,
      bare: Boolean = false
  ): Unit =
    dispose()

    val generator = NodeIdGenerator.sequential("g")
    history = new History()

    // The composition gate goes in as a pre-commit rule (§10, Schritt 5), which is the only
    // moment §15.3 allows -- "vor Commit". The session exists before the controller does, so the
    // rule asks through a holder.
    val gate = new Extension:
      val id: ExtensionId                             = ExtensionId("ember.it.composition-gate")
      override def contribute: ExtensionContributions =
        ExtensionContributions(preCommitRules = Vector(BrowserInputController.busyRule(holder)))

    val resolved = ExtensionResolver.resolve(
      Vector(
        RichText(generator),
        history,
        gate,
        ListExtension(generator),
        CodeExtension(generator),
        WidgetExtension
      )
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    val nodes = Vector(
      RootNode(rootId, Vector(NodeId("p0"), NodeId("p1"))),
      ParagraphNode(NodeId("p0"), Vector(NodeId("t0"))),
      TextNode(NodeId("t0"), "Hallo Welt"),
      // Das Atom sitzt '''in''' der zweiten Zeile und nicht in einem eigenen Block: so bleibt
      // die Textdarstellung der Fixture unveraendert, und ein Test ueber Ownership braucht keine
      // Sonderbehandlung in jeder anderen Erwartung.
      ParagraphNode(NodeId("p1"), Vector(NodeId("t1"), NodeId("a0"))),
      TextNode(NodeId("t1"), "Zweite Zeile"),
      WidgetNode(NodeId("a0"), "Notiz")
    )

    session = EditorSession.create(
      Document.unsafe(resolved.schema, rootId, nodes),
      resolved,
      resolved.sessionConfig()
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

    host = container
    // The composition log survives `dispose`, so a test can read what dispose reported. It is
    // reset here instead.
    compositions = Vector.empty
    view = DocumentView.mount(session, DomCursor.root(container), views, RenderProfile.Editor)
    port = SelectionPort.attachTo(session, view, container)

    controller = BrowserInputController.attachTo(
      session,
      view,
      port,
      // `bare` laesst alles nativ laufen ausser der History. So entsteht der Pfad, den §15.2
      // fuer ein nicht abbrechbares oder fehlendes `beforeinput` beschreibt -- ohne dass ein
      // Test ein Ereignis faelschen muesste, das es so nie gaebe.
      if bare then HistoryBindings.input else EditorBindings.everything,
      EditorBindings.everythingKeyboard ++
        (if tabIndents then ListBindings.tabIndentation else KeyboardBindings.empty),
      if tabIndents then TabPolicy.IndentsUntilEscape else TabPolicy.LeavesEditor,
      EditorMode.Editable,
      // What recovery compares the view against (§15.4) -- the same description that rendered it.
      Some(RichTextSupport.semantics),
      BusyPolicy.Defer
    )
    // One controller, not one per call: §15.4s limit counts attempts, and a fresh instance every
    // time would start at zero and never reach it.
    recoveryOf = new RecoveryController(session, view, port.positions, RichTextSupport.semantics)
    holder.bind(controller)
    controller.onOutcome(outcome => outcomes = outcomes :+ render(outcome)): Unit
    controller.onComposition(event => compositions = compositions :+ render(event)): Unit
    HistoryBindings.groupCompositions(controller, history): Unit

  @JSExport
  def dispose(): Unit =
    if controller != null then controller.dispose()
    if port != null then port.dispose()
    if view != null then view.dispose()
    if session != null then session.dispose()
    holder.release()
    controller = null
    history = null
    recoveryOf = null
    port = null
    view = null
    session = null
    host = null
    outcomes = Vector.empty

  // -----------------------------------------------------------------------------------------
  // Lesen
  // -----------------------------------------------------------------------------------------

  /** The model's blocks, one per line. The counter-check to the DOM. */
  @JSExport
  def text(): String =
    val document = session.document
    document
      .childrenOf(document.rootId)
      .map(block => textOf(block, document))
      .mkString("\n")

  private def textOf(id: NodeId, document: DocumentRead): String =
    document.node(id) match
      case Some(run: TextNode)        => run.text
      case Some(_: BreakNode)         => "⏎"
      case Some(element: ElementNode) => element.children.map(textOf(_, document)).mkString
      case _                          => ""

  /** The block types, so that Enter and lists can be told apart from text edits. */
  @JSExport
  def blocks(): String =
    val document = session.document
    document
      .childrenOf(document.rootId)
      .map(id =>
        document.node(id) match
          case Some(_: ParagraphNode) => "p"
          case Some(_: HeadingNode)   => "h"
          case Some(node)             => node.getClass.getSimpleName.replace("Node", "").toLowerCase
          case None                   => "?"
      )
      .mkString(",")

  /** The marks on the run that holds the caret. */
  @JSExport
  def marksAtCaret(): String =
    session.selection match
      case Some(range: RangeSelection) =>
        session.document.node(range.focus.owner) match
          case Some(run: TextNode) =>
            run.marks.marks
              .map(_.markId.value.split('.').last.takeWhile(_ != '/'))
              .sorted
              .mkString(",")
          case _ => ""
      case _ => ""

  @JSExport def state(): String = controller.state.toString

  @JSExport def mode(): String = controller.mode.toString

  @JSExport def revision(): Int = session.state.revision.value.toInt

  @JSExport def selection(): String =
    session.selection match
      case Some(range: RangeSelection) => s"${render(range.anchor)}|${render(range.focus)}"
      case _                           => "none"

  private def render(point: Point): String = point match
    case Point.Text(node, offset, _)       => s"text:${node.value}:$offset"
    case Point.Children(parent, offset, _) => s"children:${parent.value}:$offset"

  /** What the controller decided, in order. The proof of "genau einmal". */
  @JSExport def outcomeLog(): String = outcomes.mkString(",")

  @JSExport def clearOutcomes(): Unit = outcomes = Vector.empty

  /** The DOM's text, so that a test can show model and view agree. */
  @JSExport def domText(): String = host.textContent

  // -----------------------------------------------------------------------------------------
  // Steuern
  // -----------------------------------------------------------------------------------------

  @JSExport
  def setCaret(node: String, offset: Int): String =
    place(offset, offset, node, node)

  @JSExport
  def setRange(anchorNode: String, anchorOffset: Int, focusNode: String, focusOffset: Int): String =
    place(anchorOffset, focusOffset, anchorNode, focusNode)

  private def place(
      anchorOffset: Int,
      focusOffset: Int,
      anchorNode: String,
      focusNode: String
  ): String =
    val selection = RangeSelection(
      Point.textBefore(NodeId(anchorNode), anchorOffset),
      Point.textBefore(NodeId(focusNode), focusOffset)
    )
    session.update(_.select(selection): Unit) match
      case Left(error) => s"rejected:${error.render}"
      case Right(_)    => port.write(Some(selection), WriteIntent.Explicit).toString

  /** Whether the fixture's atom is still in the document. */
  @JSExport
  def hasAtom(): Boolean = session.document.node(NodeId("a0")).isDefined

  /** A caret at the child boundary right behind the atom. */
  @JSExport
  def setCaretAfterAtom(): String =
    val parent    = NodeId("p1")
    val index     = session.document.childrenOf(parent).indexOf(NodeId("a0")) + 1
    val selection = RangeSelection.caret(Point.childrenBefore(parent, index))
    session.update(_.select(selection): Unit) match
      case Left(error) => s"rejected:${error.render}"
      case Right(_)    => port.write(Some(selection), WriteIntent.Explicit).toString

  /** Selects the atom as a node (§11). */
  @JSExport
  def selectAtom(): String =
    val selection = NodeSelection(Set(NodeId("a0")))
    session.update(_.select(selection): Unit) match
      case Left(error) => s"rejected:${error.render}"
      case Right(_)    => port.write(Some(selection), WriteIntent.Explicit).toString

  @JSExport
  def setReadOnly(readOnly: Boolean): Unit =
    controller.setMode(if readOnly then EditorMode.ReadOnly else EditorMode.Editable)

  @JSExport def resume(): Unit = controller.resume()

  // -----------------------------------------------------------------------------------------
  // Composition und Recovery (P23)
  // -----------------------------------------------------------------------------------------

  /** The id of the composition in progress, or `0`. */
  @JSExport
  def compositionId(): Double = controller.composition.map(_.id.toDouble).getOrElse(0d)

  /** The blocks the write barrier covers, or `<host>` when everything is protected. */
  @JSExport
  def protectedRegion(): String =
    controller.composition.map(_.region) match
      case Some(ProtectedRegion.Blocks(nodes)) => nodes.map(_.value).mkString(",")
      case Some(ProtectedRegion.WholeHost)     => "<host>"
      case None                                => ""

  /** What the controller reported about compositions, in order. */
  @JSExport def compositionLog(): String = compositions.mkString(",")

  @JSExport def clearCompositions(): Unit = compositions = Vector.empty

  /** Edits the document straight through the session, past the controller.
    *
    * What a feature command, a timer or an arriving upload does -- and therefore what §15.3's
    * pre-commit gate has to catch, since none of those go through the controller.
    */
  @JSExport
  def splice(node: String, start: Int, deleteCount: Int, inserted: String): Boolean =
    session.update(_.spliceText(NodeId(node), start, deleteCount, inserted): Unit).isRight

  @JSExport
  def remove(node: String): Boolean =
    session.update(_.remove(NodeId(node)): Unit).isRight

  /** Offers an independent change -- the thing §15.3 refuses or defers. */
  @JSExport
  def offerEdit(label: String): String =
    val intent = DeferredIntent(
      label,
      None,
      (target, _) => target.update(_.spliceText(NodeId("t0"), 0, 0, label): Unit).map(_ => ())
    )
    controller.offer(intent) match
      case Left(busy)     => s"busy:${busy.session}"
      case Right(outcome) => render(outcome)

  /** Offers one that wants to land at a bookmark taken now. */
  @JSExport
  def offerBookmarked(label: String, node: String, offset: Int): String =
    val mark   = Bookmark(Point.textBefore(NodeId(node), offset), session.state.revision)
    val intent = DeferredIntent(
      label,
      Some(mark),
      (target, point) =>
        point match
          case Some(Point.Text(id, at, _)) =>
            target.update(_.spliceText(id, at, 0, label): Unit).map(_ => ())
          case _ => Right(())
    )
    controller.offer(intent) match
      case Left(busy)     => s"busy:${busy.session}"
      case Right(outcome) => render(outcome)

  /** How many undo steps the history holds. One composition must make exactly one. */
  @JSExport def undoDepth(): Int = history.state.undo.length

  /** What the view gets wrong, if anything (§15.4). */
  @JSExport
  def viewProblems(): String =
    recoveryOf.check().map(_.render).mkString(" | ")

  @JSExport
  def repairView(): String = recoveryOf.repair().render

  private def render(outcome: IntentOutcome): String = outcome match
    case IntentOutcome.Applied(label)     => s"applied:$label"
    case IntentOutcome.Rejected(label, _) => s"rejected:$label"
    case IntentOutcome.Expired(label)     => s"expired:$label"

  private def render(event: CompositionEvent): String = event match
    case CompositionEvent.Started(id, _)         => s"start:$id"
    case CompositionEvent.Finished(id, released) =>
      s"end:$id${released.map(o => "/" + render(o)).mkString}"
    case CompositionEvent.Discarded(id, reason, dropped) =>
      s"discard:$id:$reason${dropped.map("/" + _).mkString}"

  private def render(outcome: InputOutcome): String = outcome match
    case InputOutcome.TakenOver(intent)  => s"taken:${name(intent)}"
    case InputOutcome.Refused(intent, _) => s"refused:${name(intent)}"
    case InputOutcome.LeftNative(intent) => s"native:${name(intent)}"
    case InputOutcome.NotOurs            => "not-ours"
    case InputOutcome.Imported(node, _)  => s"imported:${node.value}"
    case InputOutcome.Deduplicated       => "deduplicated"
    case InputOutcome.Unimported(_)      => "unimported"
    case InputOutcome.Idle(state)        => s"idle:$state"

  private def name(intent: InputIntent): String = intent match
    case InputIntent.InsertText(_)     => "insert-text"
    case InputIntent.ReplaceText(_)    => "replace-text"
    case InputIntent.InsertParagraph   => "insert-paragraph"
    case InputIntent.InsertLineBreak   => "insert-line-break"
    case InputIntent.Delete(dir, _)    => s"delete-${dir.toString.toLowerCase}"
    case InputIntent.Format(format)    => s"format-${format.toString.toLowerCase}"
    case InputIntent.History(dir)      => s"history-${dir.toString.toLowerCase}"
    case InputIntent.Transfer(kind, _) => s"transfer-${kind.toString.toLowerCase}"
    case InputIntent.Unknown(what)     => s"unknown-$what"
