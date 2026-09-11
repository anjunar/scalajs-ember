package ember.editor.browser

import ember.editor.core.*
import ember.editor.html.{HtmlSupport, RenderProfile}
import ember.editor.jfx.DocumentView

/** What a repair came to. */
enum RecoveryOutcome:

  /** The view already says what the document says. Nothing to do. */
  case Clean

  /** The view was rebuilt from the document and now matches.
    *
    * @param attempt
    *   which try this was. §15.4 allows a limited number, and saying which one it took is the
    *   difference between "it worked" and "it worked, barely".
    */
  case Repaired(nodes: Vector[NodeId], attempt: Int)

  /** It did not take, and no further try is allowed.
    *
    * §15.4: "Bei erneuter Abweichung bleibt ein bedienbarer Source-Fallback mit verstaendlicher
    * Statusmeldung." What is left is the text, and someone has to be told.
    */
  case Exhausted(problems: Vector[HydrationProblem])

  def render: String = this match
    case Clean                     => "Die Ansicht stimmt mit dem Dokument ueberein."
    case Repaired(nodes, attempt)  =>
      s"Ansicht in Versuch $attempt neu aufgebaut: ${nodes.map(_.value).mkString(", ")}"
    case Exhausted(problems) =>
      problems.map(_.render).mkString("Die Ansicht laesst sich nicht reparieren: ", "; ", "")

/** Bringing the view back to the document, with a limit (§15.4).
  *
  * ==What is being repaired==
  *
  * The '''view''', never the document. §15.4: "Der Controller prueft den betroffenen
  * Besitzbereich und importiert entweder ein zulaessiges Fragment oder laesst JFX diesen Bereich
  * aus dem gueltigen State neu aufbauen." Importing is [[NativeInputReader]]'s job and happens
  * first; this is the other branch, for what cannot be read back as a document change.
  *
  * The document is the authority here by assumption -- whatever the browser or an extension did
  * to the DOM was not a document change, so the document is still right and the DOM is not.
  *
  * ==Why the comparison is the hydration check==
  *
  * Because it is the same question: does this markup say what this document says? §17.5 asks it
  * before claiming a server-rendered page; §15.4 asks it after a mutation nobody announced. Two
  * implementations of one question would drift, and the drift would look like a real mismatch.
  *
  * ==Why there is a limit==
  *
  * §15.4: "Reparatur hat einen begrenzten Wiederholungsversuch... keine Endlosschleife aus
  * Observer→Render→Observer." A rebuild is itself a mutation; the observer sees it; if the repair
  * does not hold, repairing again produces the same records and the same failure, faster each
  * time. One retry, then the text and a message.
  */
final class RecoveryController(
    session: EditorSession,
    view: DocumentView,
    positions: DomPositionMap,
    semantics: HtmlSupport,
    val limit: Int = RecoveryController.DefaultLimit
):

  private var used = 0

  def attempts: Int = used

  /** Forgets the attempts. For a caller that has established the view is sound again. */
  def reset(): Unit = used = 0

  /** What the view gets wrong, if anything. */
  def check(): Vector[HydrationProblem] =
    // An editor without semantics has nothing to compare against. Reporting every node as
    // "keine HtmlSemantics registriert" would turn a missing description into a broken view.
    if semantics.entries.isEmpty then Vector.empty
    else positions.hostOf(session.document.rootId) match
      case Right(root) =>
        EditorHydration
          .check(root, session.document, semantics, RenderProfile.Editor)
          // A node with a custom `NodeView` has no `HtmlSemantics` by design -- §15.1 keeps
          // adapters for atoms "deren Inneres kein Textbereich ist", and their markup is the
          // adapter's business, not the document's. Reading that as damage would put every editor
          // with an atom into permanent recovery, which a browser test duly did.
          //
          // Hydration reports it, and rightly: there a node it cannot describe is a node it may
          // not claim. Here it is a node this check has nothing to say about.
          .filterNot(_.isInstanceOf[HydrationProblem.NoSemantics])
      // Not projected at all is not a repairable mismatch -- there is nothing to compare.
      case Left(_) => Vector.empty

  /** Rebuilds what does not match, once more than it has already tried. */
  def repair(): RecoveryOutcome =
    val problems = check()

    if problems.isEmpty then
      used = 0
      RecoveryOutcome.Clean
    else if used >= limit then RecoveryOutcome.Exhausted(problems)
    else
      used += 1
      val rebuilt = problems.map(nodeOf).distinct.filter(rebuildOne)
      val left    = check()

      if left.isEmpty then RecoveryOutcome.Repaired(rebuilt, used)
      else RecoveryOutcome.Exhausted(left)

  /** A run gets its text back; anything else is remounted from the document.
    *
    * The cheaper repair first, and not only for cost: resetting a run keeps its wrapper, and with
    * it the node identity the selection is expressed in (§15.1). A remount does not.
    */
  private def rebuildOne(node: NodeId): Boolean =
    session.document.node(node) match
      case Some(_: TextNode) => view.resetRun(node)
      case Some(_)           => view.rebuild(node)
      case None              => false

  private def nodeOf(problem: HydrationProblem): NodeId = problem match
    case HydrationProblem.TagMismatch(node, _, _)          => node
    case HydrationProblem.AttributeMismatch(node, _, _, _) => node
    case HydrationProblem.TextMismatch(node, _, _)         => node
    case HydrationProblem.ChildCount(node, _, _)           => node
    case HydrationProblem.NoSemantics(node)                => node

object RecoveryController:

  /** One retry. §15.4 asks for "einen begrenzten Wiederholungsversuch", singular. */
  val DefaultLimit: Int = 1
