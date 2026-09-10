package ember.editor.core

/** The restricted access transforms and command handlers get to the draft.
  *
  * ==Why not the whole transaction==
  *
  * §10 requires that transforms read only the draft and have no DOM or network side effects.
  * Documentation that merely claims as much would be weak -- this type makes it a matter of
  * construction: there is no `setSelection` here, no `dispatch` and no field access, so a
  * transform cannot quietly grow a normalisation into a selection change or a command cascade.
  *
  * Command handlers get the same access. They may set the selection through the corresponding
  * primitive, but trigger no further dispatch -- a command chain that extends itself is exactly
  * the hidden reentrancy §10 rules out.
  */
final class TransformScope private[core] (private val transaction: Transaction):

  def document: Document = transaction.document

  def apply(operation: Operation): Either[UpdateError, Unit] = transaction.apply(operation)

  def insert(
      parent: NodeId,
      index: Int,
      node: EditorNode,
      descendants: Vector[EditorNode] = Vector.empty
  ): Either[UpdateError, Unit] = transaction.insert(parent, index, node, descendants)

  def remove(nodeId: NodeId): Either[UpdateError, Unit] = transaction.remove(nodeId)

  def move(nodeId: NodeId, newParent: NodeId, index: Int): Either[UpdateError, Unit] =
    transaction.move(nodeId, newParent, index)

  def replace(nodeId: NodeId, replacement: EditorNode): Either[UpdateError, Unit] =
    transaction.replace(nodeId, replacement)

  def spliceText(
      nodeId: NodeId,
      start: Int,
      deleteCount: Int,
      inserted: String
  ): Either[UpdateError, Unit] = transaction.spliceText(nodeId, start, deleteCount, inserted)

  def splitText(nodeId: NodeId, at: Int, newId: NodeId): Either[UpdateError, Unit] =
    transaction.splitText(nodeId, at, newId)

  def mergeText(left: NodeId, right: NodeId): Either[UpdateError, Unit] =
    transaction.mergeText(left, right)

  /** Was the Ausloeser over this Transaktion gesagt has (§14).
    *
    * A Handler, dessen Verhalten von the Herkunft abhaengt -- History is the Case, for the
    * it gebaut was --, braucht sie; raten can he sie not.
    */
  def meta: TransactionMeta = transaction.meta

  /** Setzt Document and Selection on a frueheren Stand (§14). */
  def restore(document: Document, selection: Option[Selection]): Either[UpdateError, Unit] =
    transaction.restore(document, selection)

  /** The value of a state field in the running draft. */
  def field[A](stateField: StateField[A]): A = transaction.field(stateField)

  /** Sets a state field in the running draft.
    *
    * Command handlers need this -- `ToggleMark` at a collapsed caret changes nothing but
    * `TypingMarks` (§11). Transforms may use it too, but rarely should: a field that a
    * normalisation rule has to fix was probably computed in the wrong place.
    */
  def setField[A](stateField: StateField[A], value: A): Unit =
    transaction.setField(stateField, value)

  /** The aktuelle Selection im Entwurf. */
  def selection: Option[Selection] = transaction.selection

  /** Setzt the Selection. For Command-Handler gedacht, not for Transforms. */
  def select(selection: Selection): Either[UpdateError, Unit] = transaction.select(selection)

  def clearSelection(): Either[UpdateError, Unit] = transaction.setSelection(None)

/** Wann a Transform relativ to the anderen runs.
  *
  * Within a Phase entscheiden Abhaengigkeitsordnung and then Registrierungsreihenfolge
  * (§10). Drei Stufen genuegen; who more braucht, has vermutlich a Abhaengigkeit, the he besser
  * deklariert.
  */
enum TransformPhase:

  /** Vor each Normalisierung. For structural Reparatur, on the andere aufbauen. */
  case Early

  /** The Regelfall. */
  case Normalize

  /** After allen anderen. For Ergaenzungen, the a fertigen Baum voraussetzen. */
  case Late

/** A Regel, the vor the Commit Dokumentinvarianten herstellt.
  *
  * ==Wozu, and warum not in a Listener==
  *
  * §3.2: Transforms stellen Invarianten her, '''before''' etwas sichtbar is -- statt a Kaskade
  * from Listener-Updates auszuloesen, bei the each Zwischenstand short gilt. The Zusammenwachsen
  * getrennter Textlaeufe after the Entformatieren (§8.2, P12) is the Musterfall: it gehoert in
  * dieselbe Transaktion, not in a spaeteren DOM-Cleanup and not in a eigene Undo-Stufe.
  *
  * ==The Vertrag==
  *
  *   - '''Idempotent.''' A zweiter Lauf on demselben Node aendert nothing more. Ohne the is
  *     it no Fixpunkt, and the Arbeitsbudget schlaegt to.
  *   - '''Nur the Entwurf.''' No DOM, no Netz, no Seiteneffekte -- the Kandidat can
  *     unmittelbar then verworfen become.
  *   - '''Typisiert.''' Registriert against a [[NodeType]]; the Deskriptor liefert the Typzeugen,
  *     before [[transform]] runs.
  *
  * @tparam N
  *   the Knotenart, on the this Transform reagiert
  */
trait Transform[N <: EditorNode]:

  /** Nur for Diagnose -- under anderem in the Budgetmeldung. */
  def name: String

  def nodeType: NodeType[N]

  def phase: TransformPhase = TransformPhase.Normalize

  /** Bringt diesen Node in Normalform. Aendert nothing, if nothing to tun is. */
  def transform(node: N, scope: TransformScope): Unit

/** Wie viel Arbeit the Normalisierung hoechstens kosten darf.
  *
  * §10: a Arbeitsbudget verhindert Endlosschleifen, is but '''no stilles Abschneiden'''. Is
  * it erschoepft, scheitert the Transaktion with a Diagnose the beteiligten Transforms -- a
  * halb normalisiertes Document to veroeffentlichen waere schlimmer als gar none.
  *
  * @param maxRounds
  *   wie oft the Fixpunktschleife hoechstens durchlaeuft
  */
final case class TransformBudget(maxRounds: Int = 32)

object TransformBudget:
  val default: TransformBudget = TransformBudget()
