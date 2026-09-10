package ember.editor.core

/** Der eingeschraenkte Zugriff, den Transforms und Command-Handler auf den Entwurf bekommen.
  *
  * ==Warum nicht die ganze Transaktion==
  *
  * §10 verlangt, dass Transforms ausschliesslich den Entwurf lesen und keine DOM- oder
  * Netzwerknebenwirkungen haben. Eine Dokumentation, die das nur behauptet, waere schwach -- dieser
  * Typ macht es zur Konstruktion: es gibt hier weder `setSelection` noch `dispatch` noch
  * Feldzugriff, also kann ein Transform eine Normalisierung nicht heimlich zu einer
  * Auswahlaenderung oder einer Command-Kaskade ausbauen.
  *
  * Command-Handler bekommen denselben Zugriff. Sie duerfen die Auswahl ueber das entsprechende
  * Primitiv setzen, aber keinen weiteren Dispatch ausloesen -- eine Command-Kette, die sich selbst
  * verlaengert, ist genau die verdeckte Reentranz, die §10 ausschliesst.
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

  /** Die aktuelle Auswahl im Entwurf. */
  /** Was der Ausloeser ueber diese Transaktion gesagt hat (§14).
    *
    * Ein Handler, dessen Verhalten von der Herkunft abhaengt -- History ist der Fall, fuer den
    * es gebaut wurde --, braucht sie; raten kann er sie nicht.
    */
  def meta: TransactionMeta = transaction.meta

  /** Setzt Dokument und Auswahl auf einen frueheren Stand (§14). */
  def restore(document: Document, selection: Option[Selection]): Either[UpdateError, Unit] =
    transaction.restore(document, selection)

  def selection: Option[Selection] = transaction.selection

  /** Setzt die Auswahl. Fuer Command-Handler gedacht, nicht fuer Transforms. */
  def select(selection: Selection): Either[UpdateError, Unit] = transaction.select(selection)

  def clearSelection(): Either[UpdateError, Unit] = transaction.setSelection(None)

/** Wann ein Transform relativ zu den anderen laeuft.
  *
  * Innerhalb einer Phase entscheiden Abhaengigkeitsordnung und dann Registrierungsreihenfolge
  * (§10). Drei Stufen genuegen; wer mehr braucht, hat vermutlich eine Abhaengigkeit, die er besser
  * deklariert.
  */
enum TransformPhase:

  /** Vor jeder Normalisierung. Fuer strukturelle Reparatur, auf der andere aufbauen. */
  case Early

  /** Der Regelfall. */
  case Normalize

  /** Nach allen anderen. Fuer Ergaenzungen, die einen fertigen Baum voraussetzen. */
  case Late

/** Eine Regel, die vor dem Commit Dokumentinvarianten herstellt.
  *
  * ==Wozu, und warum nicht in einem Listener==
  *
  * §3.2: Transforms stellen Invarianten her, '''bevor''' etwas sichtbar wird -- statt eine Kaskade
  * aus Listener-Updates auszuloesen, bei der jeder Zwischenstand kurz gilt. Das Zusammenwachsen
  * getrennter Textlaeufe nach dem Entformatieren (§8.2, P12) ist der Musterfall: es gehoert in
  * dieselbe Transaktion, nicht in einen spaeteren DOM-Cleanup und nicht in eine eigene Undo-Stufe.
  *
  * ==Der Vertrag==
  *
  *   - '''Idempotent.''' Ein zweiter Lauf auf demselben Knoten aendert nichts mehr. Ohne das gibt
  *     es keinen Fixpunkt, und das Arbeitsbudget schlaegt zu.
  *   - '''Nur den Entwurf.''' Kein DOM, kein Netz, keine Seiteneffekte -- der Kandidat kann
  *     unmittelbar danach verworfen werden.
  *   - '''Typisiert.''' Registriert gegen einen [[NodeType]]; der Deskriptor liefert den Typzeugen,
  *     bevor [[transform]] laeuft.
  *
  * @tparam N
  *   die Knotenart, auf die dieser Transform reagiert
  */
trait Transform[N <: EditorNode]:

  /** Nur fuer Diagnose -- unter anderem in der Budgetmeldung. */
  def name: String

  def nodeType: NodeType[N]

  def phase: TransformPhase = TransformPhase.Normalize

  /** Bringt diesen Knoten in Normalform. Aendert nichts, wenn nichts zu tun ist. */
  def transform(node: N, scope: TransformScope): Unit

/** Wie viel Arbeit die Normalisierung hoechstens kosten darf.
  *
  * §10: ein Arbeitsbudget verhindert Endlosschleifen, ist aber '''kein stilles Abschneiden'''. Wird
  * es erschoepft, scheitert die Transaktion mit einer Diagnose der beteiligten Transforms -- ein
  * halb normalisiertes Dokument zu veroeffentlichen waere schlimmer als gar keines.
  *
  * @param maxRounds
  *   wie oft die Fixpunktschleife hoechstens durchlaeuft
  */
final case class TransformBudget(maxRounds: Int = 32)

object TransformBudget:
  val default: TransformBudget = TransformBudget()
