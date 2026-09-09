package ember.editor.core

/** Eine primitive Dokumentaenderung.
  *
  * Bewusst wenige, bewusst klein. Fachliche Operationen wie `insertParagraph` oder `toggleMark`
  * bauen die Feature-Module darauf auf (§10); der Kern kennt sie nicht. Jede Operation ist atomar:
  * sie gelingt vollstaendig oder aendert nichts.
  *
  * Geschlossenes ADT -- Operationen sind eine strukturelle Kategorie (§8.1). Ein Feature-Modul
  * erweitert den Editor durch neue Node-Arten und Commands, nicht durch neue Primitive.
  */
enum Operation:

  /** Fuegt einen Teilbaum als `index`-tes Kind von `parent` ein.
    *
    * `descendants` enthaelt alle Nachfahren von `node` in beliebiger Reihenfolge. Ein Teilbaum
    * statt eines einzelnen Knotens, weil sonst jeder Zwischenstand ungueltig waere: ein Knoten mit
    * Kindreferenzen auf noch nicht eingefuegte Knoten verletzt die Invarianten, und §10 verlangt,
    * dass keine Zwischenzustaende sichtbar werden.
    */
  case Insert(parent: NodeId, index: Int, node: EditorNode, descendants: Vector[EditorNode])

  /** Entfernt einen Knoten samt Teilbaum. */
  case Remove(nodeId: NodeId)

  /** Verschiebt einen Knoten samt Teilbaum.
    *
    * `index` ist die endgueltige Position '''nach''' der Herausnahme aus dem bisherigen Parent --
    * dieselbe Festlegung wie bei `Runtime.move` in jfx-core. Beim Umsortieren innerhalb desselben
    * Parents ist das der Unterschied zwischen richtig und um eins daneben.
    */
  case Move(nodeId: NodeId, newParent: NodeId, index: Int)

  /** Ersetzt den Inhalt eines Knotens unter Beibehaltung von Identitaet und Kindern.
    *
    * Das ist die "updated"-Operation: Marks aendern, Ueberschriftenebene wechseln, Metadaten
    * setzen. Strukturaenderungen laufen ausdruecklich nicht hierueber -- die Kindliste muss gleich
    * bleiben, sonst waeren die bisherigen Kinder Waisen. Dafuer gibt es
    * [[Insert]]/[[Remove]]/[[Move]].
    */
  case Replace(nodeId: NodeId, replacement: EditorNode)

  /** Aendert Text an Ort und Stelle, in UTF-16-Koordinaten. */
  case SpliceText(nodeId: NodeId, start: Int, deleteCount: Int, inserted: String)

  /** Teilt einen Textlauf. Links behaelt die ID, rechts bekommt `newId` (§8.3). */
  case SplitText(nodeId: NodeId, at: Int, newId: NodeId)

  /** Fuehrt zwei benachbarte Textlaeufe zusammen. Links behaelt die ID. */
  case MergeText(left: NodeId, right: NodeId)

object Operation:

  /** Fuegt ein Blatt ohne Nachfahren ein. */
  def insertLeaf(parent: NodeId, index: Int, node: EditorNode): Operation =
    Operation.Insert(parent, index, node, Vector.empty)

/** Warum eine Operation abgewiesen wurde.
  *
  * Getrennt von [[Violation]], weil es etwas anderes beschreibt: eine Violation sagt, dass ein
  * Dokument ungueltig ist, ein OperationError sagt, dass eine Aenderung nicht anwendbar war. Das
  * Ausgangsdokument bleibt in diesem Fall unveraendert und gueltig.
  */
sealed trait OperationError extends EditorError

object OperationError:

  final case class UnknownNode(nodeId: NodeId) extends OperationError:
    def message: String               = s"Der Knoten `${nodeId.value}` existiert nicht."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class NotAContainer(nodeId: NodeId) extends OperationError:
    def message: String               = s"`${nodeId.value}` kann keine Kinder aufnehmen."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class NotATextNode(nodeId: NodeId) extends OperationError:
    def message: String               = s"`${nodeId.value}` ist kein Textlauf."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class IndexOutOfRange(parent: NodeId, index: Int, size: Int) extends OperationError:
    def message: String =
      s"Kindposition $index liegt ausserhalb von 0..$size in `${parent.value}`."
    override def path: DiagnosticPath = DiagnosticPath.node(parent.value).field("children")

  final case class TextOffsetOutOfRange(nodeId: NodeId, offset: Int, length: Int)
      extends OperationError:
    def message: String =
      s"Textoffset $offset liegt ausserhalb von 0..$length in `${nodeId.value}`."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value).field("text")

  final case class SplitsSurrogatePair(nodeId: NodeId, offset: Int) extends OperationError:
    def message: String =
      s"Offset $offset in `${nodeId.value}` teilt ein Surrogatpaar."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value).field("text")

  final case class MalformedText(nodeId: NodeId) extends OperationError:
    def message: String =
      s"Der einzufuegende Text fuer `${nodeId.value}` enthaelt ein unpaariges Surrogat."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value).field("text")

  final case class IdAlreadyInUse(nodeId: NodeId) extends OperationError:
    def message: String               = s"Die NodeId `${nodeId.value}` ist bereits vergeben."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class RootIsImmovable(rootId: NodeId) extends OperationError:
    def message: String =
      s"Die Wurzel `${rootId.value}` laesst sich weder entfernen noch verschieben."
    override def path: DiagnosticPath = DiagnosticPath.node(rootId.value)

  final case class MoveIntoOwnSubtree(nodeId: NodeId, target: NodeId) extends OperationError:
    def message: String =
      s"`${nodeId.value}` kann nicht unter `${target.value}` verschoben werden -- das liegt in seinem eigenen Teilbaum."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class SubtreeNotSelfContained(nodeId: NodeId, reason: String) extends OperationError:
    def message: String =
      s"Der einzufuegende Teilbaum um `${nodeId.value}` ist unvollstaendig: $reason"
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class ReplacementIdMismatch(nodeId: NodeId, replacementId: NodeId)
      extends OperationError:
    def message: String =
      s"Ersatz fuer `${nodeId.value}` traegt die abweichende ID `${replacementId.value}`."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class ReplacementChangesChildren(nodeId: NodeId) extends OperationError:
    def message: String =
      s"Ersatz fuer `${nodeId.value}` aendert die Kindliste. Struktur aendert man mit Insert, Remove oder Move."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value).field("children")

  final case class NotAdjacentSiblings(left: NodeId, right: NodeId) extends OperationError:
    def message: String =
      s"`${left.value}` und `${right.value}` sind keine unmittelbar benachbarten Geschwister."
    override def path: DiagnosticPath = DiagnosticPath.node(left.value)

  final case class MarksDiffer(left: NodeId, right: NodeId) extends OperationError:
    def message: String =
      s"`${left.value}` und `${right.value}` tragen verschiedene Marks; ein Merge wuerde Formatierung verlieren."
    override def path: DiagnosticPath = DiagnosticPath.node(left.value).field("marks")

/** Ergebnis einer erfolgreichen Operation.
  *
  * Die drei Teile gehoeren zusammen und entstehen gemeinsam (§10, Schritt 2): das neue Dokument,
  * was sich geaendert hat, und wie Positionen nachzufuehren sind. Wer sie getrennt berechnete,
  * koennte sie auseinanderlaufen lassen.
  */
final case class OperationResult(
    document: Document,
    changes: ChangeSet,
    mapping: PositionMapping
):

  /** Verkettet mit dem Ergebnis einer Folgeoperation. */
  def andThen(next: OperationResult): OperationResult =
    OperationResult(next.document, changes andThen next.changes, mapping andThen next.mapping)

/** Wendet primitive Operationen an.
  *
  * ==Warum hier nicht vollvalidiert wird==
  *
  * Jede Operation prueft ihre Vorbedingungen und konstruiert das Ergebnis so, dass die Invarianten
  * aus §8.2 erhalten bleiben -- Gueltigkeit per Konstruktion statt per Nachpruefung. Eine
  * Vollvalidierung nach jedem Tastendruck waere linear in der Dokumentgroesse und genau das, was
  * §8.2 mit "lokale Aenderungen validieren betroffene Knoten und Strukturpfade" ausschliesst.
  *
  * Diese Konstruktion ist eine Behauptung, und sie wird geprueft: `DocumentOperationModelSpec`
  * laesst zufaellige Operationsfolgen laufen und validiert nach '''jedem''' Schritt vollstaendig
  * gegen [[DocumentValidator]] -- die unabhaengige Vollvalidierung, die §8.2 dafuer verlangt.
  */
private[core] object OperationEngine:

  def apply(document: Document, operation: Operation): Either[OperationError, OperationResult] =
    operation match
      case Operation.Insert(parent, index, node, descendants) =>
        insert(document, parent, index, node, descendants)
      case Operation.Remove(nodeId)                 => remove(document, nodeId)
      case Operation.Move(nodeId, newParent, index) => move(document, nodeId, newParent, index)
      case Operation.Replace(nodeId, replacement)   => replace(document, nodeId, replacement)
      case Operation.SpliceText(nodeId, start, deleteCount, inserted) =>
        spliceText(document, nodeId, start, deleteCount, inserted)
      case Operation.SplitText(nodeId, at, newId) => splitText(document, nodeId, at, newId)
      case Operation.MergeText(left, right)       => mergeText(document, left, right)

  // -----------------------------------------------------------------------------------------
  // Insert
  // -----------------------------------------------------------------------------------------

  private def insert(
      document: Document,
      parentId: NodeId,
      index: Int,
      node: EditorNode,
      descendants: Vector[EditorNode]
  ): Either[OperationError, OperationResult] =
    for
      parent <- container(document, parentId)
      _      <- inRange(parentId, index, parent.children.length)
      forest <- selfContainedForest(document, node, descendants)
    yield
      val withChild =
        ElementSupport.withChildren(document, parent, insertAt(parent.children, index, node.id))
      val byId =
        document.byId ++ forest.map(entry => entry.id -> entry) + (withChild.id -> withChild)
      val parents = document.parents ++ internalParents(forest) + (node.id -> parentId)

      OperationResult(
        Document.trusted(document.schema, document.rootId, byId, parents),
        ChangeSet(
          created = forest.map(_.id).toSet,
          childListChanged = Set(parentId),
          touchedAncestors = document.ancestorsOf(parentId).toSet
        ),
        PositionMapping.of()(point =>
          MappedPoint.Preserved(PositionMapping.insertChild(parentId, index)(point))
        )
      )

  /** Prueft, dass der einzufuegende Teilbaum vollstaendig, frisch und zusammenhaengend ist.
    *
    * Ohne diese Pruefung koennte ein Insert Waisen, Zyklen oder ID-Kollisionen erzeugen -- also
    * genau die Zustaende, die [[Document]] per Konstruktion ausschliessen soll.
    */
  private def selfContainedForest(
      document: Document,
      node: EditorNode,
      descendants: Vector[EditorNode]
  ): Either[OperationError, Vector[EditorNode]] =
    val forest = node +: descendants
    val byId   = forest.map(entry => entry.id -> entry).toMap

    val duplicate = forest.map(_.id).groupBy(Predef.identity).collectFirst {
      case (id, entries) if entries.sizeIs > 1 => id
    }
    val colliding = forest.map(_.id).find(document.contains)

    val referenced = forest.flatMap {
      case element: ElementNode => element.children
      case _                    => Vector.empty
    }
    val dangling    = referenced.find(!byId.contains(_))
    val reachable   = reachableFrom(node.id, byId)
    val detached    = byId.keys.find(!reachable.contains(_))
    val rootAsChild = referenced.contains(node.id)

    (duplicate, colliding, dangling, detached, rootAsChild) match
      case (Some(id), _, _, _, _) => Left(OperationError.IdAlreadyInUse(id))
      case (_, Some(id), _, _, _) => Left(OperationError.IdAlreadyInUse(id))
      case (_, _, Some(id), _, _) =>
        Left(
          OperationError.SubtreeNotSelfContained(node.id, s"Kindreferenz auf `${id.value}` fehlt.")
        )
      case (_, _, _, Some(id), _) =>
        Left(
          OperationError.SubtreeNotSelfContained(node.id, s"`${id.value}` haengt an keiner Wurzel.")
        )
      case (_, _, _, _, true) =>
        Left(
          OperationError.SubtreeNotSelfContained(
            node.id,
            "Die Teilbaumwurzel ist ihr eigenes Kind."
          )
        )
      case _ => Right(forest)

  /** Iterative Erreichbarkeit innerhalb des einzufuegenden Waldes. */
  private def reachableFrom(start: NodeId, byId: Map[NodeId, EditorNode]): Set[NodeId] =
    val visited = scala.collection.mutable.HashSet.empty[NodeId]
    val pending = scala.collection.mutable.Stack(start)
    while pending.nonEmpty do
      val current = pending.pop()
      if visited.add(current) then
        byId.get(current) match
          case Some(element: ElementNode) => element.children.foreach(pending.push)
          case _                          => ()
    visited.toSet

  private def internalParents(forest: Vector[EditorNode]): Map[NodeId, NodeId] =
    forest.flatMap {
      case element: ElementNode => element.children.map(_ -> element.id)
      case _                    => Vector.empty
    }.toMap

  // -----------------------------------------------------------------------------------------
  // Remove
  // -----------------------------------------------------------------------------------------

  private def remove(document: Document, nodeId: NodeId): Either[OperationError, OperationResult] =
    for
      _        <- known(document, nodeId)
      _        <- notRoot(document, nodeId)
      parentId <- parentOf(document, nodeId)
      parent   <- container(document, parentId)
    yield
      val index   = parent.children.indexOf(nodeId)
      val subtree = document.subtreeOf(nodeId).toSet

      val withoutChild =
        ElementSupport.withChildren(document, parent, parent.children.patch(index, Nil, 1))
      val byId    = (document.byId -- subtree) + (withoutChild.id -> withoutChild)
      val parents = document.parents -- subtree

      // §11: Punkte innerhalb fallen auf die erhaltene Einfuegegrenze im Parent zurueck. Der
      // Parent ueberlebt hier immer -- der Fall "wird dieser ebenfalls entfernt, weiter nach
      // aussen" entsteht erst beim Verketten mehrerer Entfernungen und faellt dort aus der
      // Komposition heraus.
      val boundary = Point.Children(parentId, index, Affinity.Before)

      OperationResult(
        Document.trusted(document.schema, document.rootId, byId, parents),
        ChangeSet(
          removed = subtree,
          childListChanged = Set(parentId),
          touchedAncestors = document.ancestorsOf(parentId).toSet
        ),
        PositionMapping.of(subtree) { point =>
          if subtree.contains(point.owner) then MappedPoint.Displaced(boundary)
          else MappedPoint.Preserved(PositionMapping.removeChild(parentId, index)(point))
        }
      )

  // -----------------------------------------------------------------------------------------
  // Move
  // -----------------------------------------------------------------------------------------

  private def move(
      document: Document,
      nodeId: NodeId,
      newParentId: NodeId,
      index: Int
  ): Either[OperationError, OperationResult] =
    for
      _           <- known(document, nodeId)
      _           <- notRoot(document, nodeId)
      oldParentId <- parentOf(document, nodeId)
      oldParent   <- container(document, oldParentId)
      newParent   <- container(document, newParentId)
      _           <- notIntoOwnSubtree(document, nodeId, newParentId)
      // Die Zielposition zaehlt in der Liste *nach* der Herausnahme. Beim Umsortieren im selben
      // Parent ist das genau die Stelle, an der sich ein Off-by-one einnistet.
      capacity =
        if oldParentId == newParentId then newParent.children.length - 1
        else newParent.children.length
      _ <- inRange(newParentId, index, capacity)
    yield
      val oldIndex = oldParent.children.indexOf(nodeId)
      val detached = oldParent.children.patch(oldIndex, Nil, 1)

      val (byId, changedLists) =
        if oldParentId == newParentId then
          val reordered =
            ElementSupport.withChildren(document, oldParent, insertAt(detached, index, nodeId))
          (document.byId + (reordered.id -> reordered), Set(oldParentId))
        else
          val shortened = ElementSupport.withChildren(document, oldParent, detached)
          val extended  =
            ElementSupport.withChildren(
              document,
              newParent,
              insertAt(newParent.children, index, nodeId)
            )
          (
            document.byId + (shortened.id -> shortened) + (extended.id -> extended),
            Set(oldParentId, newParentId)
          )

      OperationResult(
        Document.trusted(
          document.schema,
          document.rootId,
          byId,
          document.parents + (nodeId -> newParentId)
        ),
        ChangeSet(
          moved = Set(nodeId),
          childListChanged = changedLists,
          touchedAncestors =
            document.ancestorsOf(oldParentId).toSet ++ document.ancestorsOf(newParentId).toSet
        ),
        // §11: Punkte in ueberlebenden Knoten behalten ID und Offset -- der Teilbaum wandert
        // mit. Nur die Kindoffsets der beiden Parents verschieben sich, und zwar komponiert:
        // erst die Herausnahme, dann die Einfuegung in der bereits verkuerzten Liste.
        PositionMapping.of() { point =>
          val afterRemoval = PositionMapping.removeChild(oldParentId, oldIndex)(point)
          MappedPoint.Preserved(PositionMapping.insertChild(newParentId, index)(afterRemoval))
        }
      )

  private def notIntoOwnSubtree(
      document: Document,
      nodeId: NodeId,
      target: NodeId
  ): Either[OperationError, Unit] =
    if document.subtreeOf(nodeId).contains(target) then
      Left(OperationError.MoveIntoOwnSubtree(nodeId, target))
    else Right(())

  // -----------------------------------------------------------------------------------------
  // Replace
  // -----------------------------------------------------------------------------------------

  private def replace(
      document: Document,
      nodeId: NodeId,
      replacement: EditorNode
  ): Either[OperationError, OperationResult] =
    for
      existing <- known(document, nodeId)
      _        <- Either.cond(
        replacement.id == nodeId,
        (),
        OperationError.ReplacementIdMismatch(nodeId, replacement.id)
      )
      _ <- Either.cond(
        childrenOf(existing) == childrenOf(replacement),
        (),
        OperationError.ReplacementChangesChildren(nodeId)
      )
    yield
      val boundary = document
        .parentOf(nodeId)
        .flatMap(parent =>
          document.indexOfChild(nodeId).map(Point.Children(parent, _, Affinity.Before))
        )
        .getOrElse(Point.Children(nodeId, 0, Affinity.Before))

      OperationResult(
        Document.trusted(
          document.schema,
          document.rootId,
          document.byId + (nodeId -> replacement),
          document.parents
        ),
        ChangeSet(updated = Set(nodeId), touchedAncestors = document.ancestorsOf(nodeId).toSet),
        // §11 verbietet eine heuristische Zuordnung nach Textgleichheit. Hier wird auch keine
        // gemacht: die Identitaet des Knotens steht fest, geprueft wird nur, ob der Offset im
        // Ersatz ueberhaupt noch eine Position bezeichnet. Tut er das nicht, gibt es die
        // definierte Rueckfallgrenze.
        PositionMapping.of() {
          case point @ Point.Text(owner, offset, _) if owner == nodeId =>
            replacement match
              case text: TextNode if offset <= text.text.length => MappedPoint.Preserved(point)
              case _                                            => MappedPoint.Displaced(boundary)
          case point => MappedPoint.Preserved(point)
        }
      )

  // -----------------------------------------------------------------------------------------
  // Text
  // -----------------------------------------------------------------------------------------

  private def spliceText(
      document: Document,
      nodeId: NodeId,
      start: Int,
      deleteCount: Int,
      inserted: String
  ): Either[OperationError, OperationResult] =
    for
      text <- textNode(document, nodeId)
      end = start + deleteCount
      _ <- Either.cond(
        start >= 0 && deleteCount >= 0 && end <= text.text.length,
        (),
        OperationError.TextOffsetOutOfRange(
          nodeId,
          if start < 0 then start else end,
          text.text.length
        )
      )
      _ <- noSurrogateSplit(nodeId, text.text, start)
      _ <- noSurrogateSplit(nodeId, text.text, end)
      _ <- Either.cond(isWellFormed(inserted), (), OperationError.MalformedText(nodeId))
    yield
      val updated = text.text.substring(0, start) + inserted + text.text.substring(end)

      // §-Vertrag von jfx-core: gleicher Text erzeugt keinen Schreibzugriff. Dieselbe Regel
      // gilt hier eine Ebene hoeher -- ein No-op erzeugt keinen Commit und keine History-Stufe
      // (§10, Schritt 6).
      if updated == text.text then
        OperationResult(document, ChangeSet.empty, PositionMapping.identity)
      else
        OperationResult(
          Document.trusted(
            document.schema,
            document.rootId,
            document.byId + (nodeId -> text.copy(text = updated)),
            document.parents
          ),
          ChangeSet(
            textSplices = Map(nodeId -> Vector(TextSplice(start, deleteCount, inserted))),
            touchedAncestors = document.ancestorsOf(nodeId).toSet
          ),
          // Erst loeschen, dann einfuegen -- in dieser Reihenfolge, weil die Einfuegeregel aus
          // §11 die Affinitaet an der Einfuegestelle auswertet und die liegt nach dem Loeschen
          // bei `start`.
          PositionMapping.of() { point =>
            PositionMapping
              .deleteText(nodeId, start, end)(point)
              .flatMap(deleted =>
                MappedPoint.Preserved(
                  PositionMapping.insertText(nodeId, start, inserted.length)(deleted)
                )
              )
          }
        )

  private def splitText(
      document: Document,
      nodeId: NodeId,
      at: Int,
      newId: NodeId
  ): Either[OperationError, OperationResult] =
    for
      text <- textNode(document, nodeId)
      _    <- Either.cond(
        at >= 0 && at <= text.text.length,
        (),
        OperationError.TextOffsetOutOfRange(nodeId, at, text.text.length)
      )
      _        <- noSurrogateSplit(nodeId, text.text, at)
      _        <- Either.cond(!document.contains(newId), (), OperationError.IdAlreadyInUse(newId))
      parentId <- parentOf(document, nodeId)
      parent   <- container(document, parentId)
    yield
      val index = parent.children.indexOf(nodeId)
      val left  = text.copy(text = text.text.substring(0, at))
      val right = TextNode(newId, text.text.substring(at), text.marks)

      val withBoth =
        ElementSupport.withChildren(document, parent, insertAt(parent.children, index + 1, newId))

      OperationResult(
        Document.trusted(
          document.schema,
          document.rootId,
          document.byId + (nodeId   -> left) + (newId -> right) + (withBoth.id -> withBoth),
          document.parents + (newId -> parentId)
        ),
        ChangeSet(
          created = Set(newId),
          childListChanged = Set(parentId),
          textSplices = Map(nodeId -> Vector(TextSplice(at, text.text.length - at, ""))),
          touchedAncestors = document.ancestorsOf(parentId).toSet
        ),
        // §11: "Linke ID bleibt. Rechts liegende Punkte wechseln zur neuen rechten ID mit
        // Offset minus p; Gleichheit entscheidet Affinitaet."
        PositionMapping.of() {
          case point @ Point.Text(owner, offset, affinity) if owner == nodeId =>
            if offset < at then MappedPoint.Preserved(point)
            else if offset > at then MappedPoint.Preserved(Point.Text(newId, offset - at, affinity))
            else
              affinity match
                case Affinity.Before => MappedPoint.Preserved(point)
                case Affinity.After  => MappedPoint.Preserved(Point.Text(newId, 0, affinity))
          case point =>
            MappedPoint.Preserved(PositionMapping.insertChild(parentId, index + 1)(point))
        }
      )

  private def mergeText(
      document: Document,
      leftId: NodeId,
      rightId: NodeId
  ): Either[OperationError, OperationResult] =
    for
      left     <- textNode(document, leftId)
      right    <- textNode(document, rightId)
      parentId <- parentOf(document, leftId)
      parent   <- container(document, parentId)
      leftIndex = parent.children.indexOf(leftId)
      _ <- Either.cond(
        leftIndex >= 0 && parent.children.lift(leftIndex + 1).contains(rightId),
        (),
        OperationError.NotAdjacentSiblings(leftId, rightId)
      )
      // Ein Merge ueber verschiedene Marks hinweg wuerde Formatierung stillschweigend
      // verlieren. Die Normalisierung aus §8.2 fuehrt ohnehin nur gleich markierte Laeufe
      // zusammen; das Primitiv weist den Rest ab, statt sich darauf zu verlassen.
      _ <- Either.cond(left.marks == right.marks, (), OperationError.MarksDiffer(leftId, rightId))
    yield
      val merged       = left.copy(text = left.text + right.text)
      val withoutRight =
        ElementSupport.withChildren(document, parent, parent.children.patch(leftIndex + 1, Nil, 1))
      val subtree = document.subtreeOf(rightId).toSet

      OperationResult(
        Document.trusted(
          document.schema,
          document.rootId,
          (document.byId -- subtree) + (leftId -> merged) + (withoutRight.id -> withoutRight),
          document.parents -- subtree
        ),
        ChangeSet(
          removed = subtree,
          childListChanged = Set(parentId),
          textSplices = Map(leftId -> Vector(TextSplice(left.text.length, 0, right.text))),
          touchedAncestors = document.ancestorsOf(parentId).toSet
        ),
        // §11: "Rechte Punkte wechseln zur linken ID plus urspruenglicher linker Laenge."
        // Preserved, nicht Displaced: der Inhalt ist nicht verschwunden, er steht nur woanders.
        PositionMapping.of() {
          case Point.Text(owner, offset, affinity) if owner == rightId =>
            MappedPoint.Preserved(Point.Text(leftId, left.text.length + offset, affinity))
          case point =>
            MappedPoint.Preserved(PositionMapping.removeChild(parentId, leftIndex + 1)(point))
        }
      )

  // -----------------------------------------------------------------------------------------
  // Vorbedingungen
  // -----------------------------------------------------------------------------------------

  private def known(document: Document, nodeId: NodeId): Either[OperationError, EditorNode] =
    document.node(nodeId).toRight(OperationError.UnknownNode(nodeId))

  private def container(document: Document, nodeId: NodeId): Either[OperationError, ElementNode] =
    known(document, nodeId).flatMap {
      case element: ElementNode => Right(element)
      case _                    => Left(OperationError.NotAContainer(nodeId))
    }

  private def textNode(document: Document, nodeId: NodeId): Either[OperationError, TextNode] =
    known(document, nodeId).flatMap {
      case text: TextNode => Right(text)
      case _              => Left(OperationError.NotATextNode(nodeId))
    }

  private def parentOf(document: Document, nodeId: NodeId): Either[OperationError, NodeId] =
    document.parentOf(nodeId).toRight(OperationError.RootIsImmovable(nodeId))

  private def notRoot(document: Document, nodeId: NodeId): Either[OperationError, Unit] =
    Either.cond(nodeId != document.rootId, (), OperationError.RootIsImmovable(nodeId))

  private def inRange(parent: NodeId, index: Int, size: Int): Either[OperationError, Unit] =
    Either.cond(
      index >= 0 && index <= size,
      (),
      OperationError.IndexOutOfRange(parent, index, size)
    )

  private def noSurrogateSplit(
      nodeId: NodeId,
      text: String,
      offset: Int
  ): Either[OperationError, Unit] =
    Either.cond(
      !Point.splitsSurrogatePair(text, offset),
      (),
      OperationError.SplitsSurrogatePair(nodeId, offset)
    )

  /** Kein unpaariges Surrogat -- sonst entstuende ein String, den kein Wire-Format ueberlebt. */
  private def isWellFormed(text: String): Boolean =
    var index = 0
    var valid = true
    while index < text.length && valid do
      val character = text.charAt(index)
      if Character.isHighSurrogate(character) then
        valid = index + 1 < text.length && Character.isLowSurrogate(text.charAt(index + 1))
        index += 2
      else
        valid = !Character.isLowSurrogate(character)
        index += 1
    valid

  private def childrenOf(node: EditorNode): Vector[NodeId] = node match
    case element: ElementNode => element.children
    case _                    => Vector.empty

  private def insertAt(children: Vector[NodeId], index: Int, id: NodeId): Vector[NodeId] =
    children.patch(index, Vector(id), 0)

/** Baut einen Container mit neuer Kindliste ueber seinen registrierten Deskriptor.
  *
  * Der Umweg ueber das Schema ist der Kern von §8.1: der Kern kennt die `copy`-Signatur einer
  * fremden Case Class nicht und darf sie nicht erraten. `ElementNodeType.withChildren` ist der
  * Vertrag, der generisches Einfuegen und Verschieben auch fuer Fremdtypen moeglich macht -- und
  * der alle uebrigen fachlichen Felder erhaelt.
  */
private[core] object ElementSupport:

  def withChildren(document: Document, node: ElementNode, children: Vector[NodeId]): ElementNode =
    document.schema.descriptorFor(node) match
      case Some(descriptor: ElementNodeType[?]) => rebuild(descriptor, node, children)
      case _                                    =>
        // Unerreichbar fuer ein gueltiges Dokument: DocumentValidator meldet einen Container
        // ohne ElementNodeType als MissingElementDescriptor, ein solcher Knoten kommt also gar
        // nicht erst hinein. Bleibt als Vertragsverletzung stehen statt als stiller Datenverlust.
        throw EditorContractViolation(
          s"`${node.id.value}` ist ein Container ohne ElementNodeType. Das Dokument haette nicht gebaut werden duerfen."
        )

  private def rebuild[N <: ElementNode](
      descriptor: ElementNodeType[N],
      node: ElementNode,
      children: Vector[NodeId]
  ): ElementNode =
    descriptor
      .project(node)
      .map(descriptor.withChildren(_, children))
      .getOrElse(
        throw EditorContractViolation(
          s"Der Deskriptor `${descriptor.typeId.value}` weist seinen eigenen Knoten `${node.id.value}` zurueck."
        )
      )
