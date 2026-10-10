package ember.editor.core

import scala.collection.mutable.HashSet
import scala.collection.mutable.Stack

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
    val visited = HashSet.empty[NodeId]
    val pending = Stack(start)
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

      // §-Vertrag von ui-core: gleicher Text erzeugt keinen Schreibzugriff. Dieselbe Regel
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
