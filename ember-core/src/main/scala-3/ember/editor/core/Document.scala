package ember.editor.core

/** Ein gueltiges, unveraenderliches Dokument.
  *
  * Der Konstruktor ist privat. Ein `Document` entsteht ausschliesslich ueber [[Document.build]]
  * bzw. [[Document.empty]] und hat damit die Invarianten aus §8.2 immer schon erfuellt -- genau
  * eine Wurzel, eindeutige IDs, erreichbare Knoten, keine Zyklen, je Knoten genau ein Elternteil,
  * schemakonforme Inhalte. Wer einen Wert dieses Typs in der Hand hat, muss nichts mehr pruefen.
  *
  * ==Speicherung==
  *
  * Eine persistente `Map[NodeId, EditorNode]`, eine Wurzel-ID und ein '''abgeleiteter'''
  * Elternindex. Abgeleitet heisst: der Index ist eine Projektion der Kindlisten, keine zweite
  * Wahrheit. Kinder sind ausschliesslich referenzierte IDs, nie eingebettete Objekte -- deshalb
  * kostet eine Textaenderung tief im Baum den betroffenen String und wenige Indexpfade, nicht eine
  * rekursive Kopie aller Vorfahren.
  *
  * Positionsindizes innerhalb grosser Kindlisten sind bewusst '''nicht''' gespeichert. §8.2 erlaubt
  * sie nur als Cache; eine Elternsuche mit linearem Scan der Geschwister ist ehrlicher als eine
  * behauptete O(1)-Zusicherung, die es nicht gibt. Rope, Piece-Table oder Order-Index kommen erst
  * bei gemessener Last (§8.3).
  *
  * ==Snapshots==
  *
  * Ein gelesenes Dokument bleibt genau der gelesene Stand. Aeltere Snapshots bleiben gueltig und
  * lesbar; entfernte Teilbaeume verschwinden aus dem neuen Index und leben nur in noch
  * referenzierten Snapshots weiter. IDs aendern sich dabei nie.
  */
final class Document private (
    val schema: Schema,
    val rootId: NodeId,
    private[core] val byId: Map[NodeId, EditorNode],
    private[core] val parents: Map[NodeId, NodeId]
) extends DocumentRead:

  def size: Int = byId.size

  def node(id: NodeId): Option[EditorNode] = byId.get(id)

  def parentOf(id: NodeId): Option[NodeId] = parents.get(id)

  def ids: Iterator[NodeId] = byId.keysIterator

  /** Alle Knoten in Dokumentordnung. */
  def inDocumentOrder: Iterator[EditorNode] = subtreeOf(rootId).flatMap(node)

  /** Wendet eine primitive Operation an.
    *
    * Atomar: bei einem Fehler bleibt dieses Dokument unveraendert und gueltig, und der Aufrufer
    * haelt weiterhin einen brauchbaren Stand in der Hand (P03, Akzeptanz).
    */
  def applyOperation(operation: Operation): Either[OperationError, OperationResult] =
    OperationEngine(this, operation)

  /** Wendet eine Folge an und liefert das zusammengefasste Ergebnis.
    *
    * Ebenfalls atomar: die erste fehlschlagende Operation bricht die Folge ab, und weil jeder
    * Zwischenstand ein eigener unveraenderlicher Wert ist, bleibt das Ausgangsdokument dabei
    * unberuehrt. Es gibt nichts zurueckzurollen -- das ist der praktische Gewinn der
    * Unveraenderlichkeit gegenueber einem Draft, den man wieder aufraeumen muss.
    *
    * Eine leere Folge ergibt ein leeres Ergebnis auf demselben Dokument, keinen Commit (§10).
    */
  def applyAll(operations: Seq[Operation]): Either[OperationError, OperationResult] =
    operations.foldLeft[Either[OperationError, OperationResult]](
      Right(OperationResult(this, ChangeSet.empty, PositionMapping.identity))
    ) { (accumulated, operation) =>
      accumulated.flatMap(result => result.document.applyOperation(operation).map(result andThen _))
    }

  override def equals(other: Any): Boolean = other match
    case that: Document => rootId == that.rootId && byId == that.byId
    case _              => false

  // Der Elternindex geht nicht ein: er ist aus `byId` ableitbar und traegt keine eigene
  // Information. Zwei Dokumente mit gleicher Knotenmenge und gleicher Wurzel sind gleich.
  override def hashCode(): Int = (rootId, byId).hashCode()

  override def toString: String = s"Document(root=${rootId.value}, size=$size)"

object Document:

  /** Baut ein Dokument aus einer Knotenmenge und prueft dabei vollstaendig.
    *
    * Vollvalidierung ist hier richtig: `build` ist der Importweg -- JSON, Clipboard, Fixtures. Die
    * inkrementelle Pruefung nur betroffener Knoten und Strukturpfade gehoert zu den Operationen aus
    * P03, nicht hierher.
    */
  def build(
      schema: Schema,
      rootId: NodeId,
      nodes: Iterable[EditorNode]
  ): Either[Vector[Violation], Document] =
    DocumentValidator.validate(schema, rootId, nodes)

  /** Ein Dokument mit leerer Wurzel.
    *
    * Der Kern erlaubt das ausdruecklich (§8.2). Dass eine editierbare Flaeche mindestens einen
    * Paragraph und eine gueltige Caretposition braucht, ist eine Regel des Rich-Text-Profils.
    */
  def empty(schema: Schema, rootId: NodeId): Either[Vector[Violation], Document] =
    build(schema, rootId, Vector(RootNode.empty(rootId)))

  /** Wie [[build]], wirft aber bei Verletzungen. Fuer Tests und im Code feststehende Dokumente. */
  def unsafe(schema: Schema, rootId: NodeId, nodes: Iterable[EditorNode]): Document =
    build(schema, rootId, nodes) match
      case Right(document)  => document
      case Left(violations) =>
        throw EditorContractViolation(
          violations.map(_.render).mkString("Ungueltiges Dokument:\n", "\n", "")
        )

  /** Baut ohne erneute Pruefung.
    *
    * `private[core]`, damit die Operationen aus P03 ein Ergebnis zusammensetzen koennen, dessen
    * Gueltigkeit sie selbst schon sichergestellt haben, ohne bei jedem Tastendruck den ganzen Baum
    * erneut zu validieren. Ausserhalb des Kerns nicht erreichbar -- von aussen fuehrt der einzige
    * Weg ueber [[build]].
    */
  private[core] def trusted(
      schema: Schema,
      rootId: NodeId,
      byId: Map[NodeId, EditorNode],
      parents: Map[NodeId, NodeId]
  ): Document = new Document(schema, rootId, byId, parents)
