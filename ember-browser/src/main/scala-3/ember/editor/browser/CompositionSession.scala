package ember.editor.browser

import ember.editor.core.*

/** The area a running composition owns.
  *
  * §15.3: "Die Schreibsperre schuetzt den vollstaendigen anfaenglichen Ersetzungsbereich
  * einschliesslich aller betroffenen Leaves, Marks, Atomgrenzen und Bloecke sowie benoetigter
  * struktureller Vorfahren. Bei einer kollabierten Range ist dies mindestens der aktive Block.
  * Reicht eine lokale Schutzgrenze nicht, wird der ganze Editing-Host geschuetzt; ein
  * blockuebergreifender Start darf nicht als Ein-Leaf-Fall behandelt werden."
  */
enum ProtectedRegion:

  /** The blocks the composition may touch. Protecting a block protects everything inside it. */
  case Blocks(nodes: Vector[NodeId])

  /** Everything. For a composition whose start cannot be narrowed to blocks. */
  case WholeHost

  def isWhole: Boolean = this == ProtectedRegion.WholeHost

/** Which blocks a composition starts in.
  *
  * ==Why blocks and not leaves==
  *
  * Because §15.3 forbids the narrow reading in so many words: "ein blockuebergreifender Start darf
  * nicht als Ein-Leaf-Fall behandelt werden". An IME replacement reaches further than the run it
  * started in -- across marks, across atom boundaries, across blocks -- and a barrier drawn around
  * one leaf would let the projection rewrite the rest of the sentence underneath a composition in
  * progress.
  *
  * A block is also the smallest area that is cheap to name and always sufficient: the guard
  * protects a subtree, so protecting the block protects every leaf, mark and atom in it.
  *
  * ==Why this is a function and not part of the session==
  *
  * It is the one part of the composition protocol that is pure: a document and a selection go in, a
  * set of node ids comes out. Everything around it needs an engine.
  */
object CompositionRegion:

  def of(document: DocumentRead, selection: Option[Selection]): ProtectedRegion =
    selection match
      // No selection means no idea where the composition is. §15.3's escape hatch: "Reicht eine
      // lokale Schutzgrenze nicht, wird der ganze Editing-Host geschuetzt."
      case None => ProtectedRegion.WholeHost

      case Some(range: RangeSelection) =>
        val (from, to) = range.ordered(document)
        (blockOf(document, from.owner), blockOf(document, to.owner)) match
          case (Some(first), Some(last)) => ProtectedRegion.Blocks(span(document, first, last))
          case _                         => ProtectedRegion.WholeHost

      case Some(nodes: NodeSelection) =>
        val blocks = nodes.nodes.toVector.flatMap(blockOf(document, _)).distinct
        if blocks.isEmpty then ProtectedRegion.WholeHost
        else ProtectedRegion.Blocks(inDocumentOrder(document, blocks))

      // A selection kind this module does not know. §11 keeps the contract open, and guessing a
      // narrower area than the truth is the one mistake that costs data.
      case Some(_) => ProtectedRegion.WholeHost

  /** The child of the root that contains a node, or the node itself when it is one.
    *
    * `None` for the root: a composition that claims the root claims everything, and saying so as
    * [[ProtectedRegion.WholeHost]] is more honest than protecting "the root's block".
    */
  def blockOf(document: DocumentRead, node: NodeId): Option[NodeId] =
    if node == document.rootId then None
    else
      val blocks = document.childrenOf(document.rootId)
      if blocks.contains(node) then Some(node)
      else document.ancestorsOf(node).find(blocks.contains)

  /** Every top-level block from the first to the last, inclusive. */
  private def span(document: DocumentRead, first: NodeId, last: NodeId): Vector[NodeId] =
    val blocks = document.childrenOf(document.rootId)
    (blocks.indexOf(first), blocks.indexOf(last)) match
      case (start, end) if start >= 0 && end >= 0 =>
        blocks.slice(math.min(start, end), math.max(start, end) + 1)
      case _ => Vector(first, last).distinct

  private def inDocumentOrder(document: DocumentRead, nodes: Vector[NodeId]): Vector[NodeId] =
    val blocks = document.childrenOf(document.rootId)
    nodes.sortBy(blocks.indexOf)

/** One run of native text input, from `compositionstart` to its completion.
  *
  * ==Why a session and not a flag==
  *
  * §15.3 asks for four things a boolean cannot carry: "Die laufende Composition besitzt eine
  * Session-ID, Ausgangsrevision, gemappte Selection und die letzte erfasste native Eingabe."
  *
  * The revision is what makes the completion check possible -- "ein revisionierter Vergleich des
  * erfassten Textes verhindert doppelte Einfuegung". The id is what lets a late event be recognised
  * as belonging to a composition that has already ended, which is the shape of most of the browser
  * bugs §15.2 lists.
  *
  * @param captured
  *   the last text read out of the DOM, and the node it came from. Compared on completion so that a
  *   final `input` after `compositionend` does not insert the same text twice.
  */
final case class CompositionSession(
    id: Long,
    baseRevision: Revision,
    region: ProtectedRegion,
    selection: Option[Selection],
    captured: Option[(NodeId, String)]
):

  def withCapture(node: NodeId, text: String): CompositionSession =
    copy(captured = Some((node, text)))

  /** Whether this text has already been taken from this node. */
  def alreadyCaptured(node: NodeId, text: String): Boolean = captured.contains((node, text))

  /** The nodes the write barrier covers, empty when the whole host is protected. */
  def protectedNodes: Vector[NodeId] = region match
    case ProtectedRegion.Blocks(nodes) => nodes
    case ProtectedRegion.WholeHost     => Vector.empty

object CompositionSession:

  private var counter: Long = 0L

  /** Opens a session for the state a composition starts in. */
  def start(
      document: DocumentRead,
      selection: Option[Selection],
      revision: Revision
  ): CompositionSession =
    counter += 1
    CompositionSession(
      id = counter,
      baseRevision = revision,
      region = CompositionRegion.of(document, selection),
      selection = selection,
      captured = None
    )

/** A change that is not the running composition's, refused before it commits.
  *
  * §15.3: "Waehrend Composition werden '''alle unabhaengigen Dokumenttransaktionen''', auch
  * ausserhalb des geschuetzten Bereichs, vor Commit als `CompositionBusy` abgewiesen oder als
  * expliziter Intent mit Bookmark in eine begrenzte Queue gelegt."
  *
  * Outside the protected area too, and that is the part worth explaining: the reason is not the DOM
  * but the history. §14 records a composition as '''one''' group, and a snapshot history undoes a
  * group whole. An independent commit landing inside that group would be taken back with it --
  * someone else's edit, undone by a keystroke that had nothing to do with it. Allowing them needs
  * selective history, and §15.3 rules that out for the MVP by name.
  */
final case class CompositionBusy(session: Long, override val path: DiagnosticPath)
    extends EditorError:
  def message: String =
    s"Eine Texteingabe laeuft (Composition $session); unabhaengige Aenderungen sind so lange " +
      "nicht erlaubt."

/** The pre-commit rule that enforces it.
  *
  * ==Why a `PreCommitRule` and not a check in the controller==
  *
  * Because §10's step 5 is exactly the moment §15.3 names -- "vor Commit" -- and because a check in
  * the controller would only see what goes through the controller. A feature module's command, a
  * timer, an arriving upload: all of those reach the session directly, and all of them are
  * "unabhaengige Dokumenttransaktionen".
  *
  * ==What stays allowed==
  *
  * §15.3: "Erlaubt bleiben zugehoerige native Composition-Updates sowie reine Selection-/View-/
  * Effect-Aenderungen." So the rule looks at two things: whether the document changed at all, and
  * whether the transaction carries the composition's own tag.
  */
object CompositionGate:

  /** The tag a composition's own transactions carry.
    *
    * Named in the core (§14's typed metadata) because the rich-text profile reads the same word to
    * defer its merges (§8.2), and two modules that never meet cannot agree on a string each spells
    * for itself.
    */
  val Tag: String = TransactionMeta.CompositionTag

  /** Meta for a transaction that belongs to the running composition. */
  def meta(label: String): TransactionMeta =
    TransactionMeta(origin = Origin.User, label = Some(label), tags = Set(Tag))

  /** @param running
    *   the id of the composition in progress, if there is one. Read at check time, because a rule
    *   outlives any single composition.
    */
  def rule(running: () => Option[Long]): PreCommitRule =
    PreCommitRule("browser.composition-busy") { candidate =>
      running().flatMap { session =>
        if !candidate.documentChanged then None
        else if candidate.meta.tags.contains(Tag) then None
        else Some(CompositionBusy(session, DiagnosticPath.Root))
      }
    }
