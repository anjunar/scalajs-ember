package ember.editor.core

import scala.collection.mutable

/** Vollstaendige Strukturpruefung einer Knotenmenge.
  *
  * Prueft die Zusicherungen aus §8.2: genau eine Wurzel, eindeutige IDs, erreichbare Knoten, keine
  * Zyklen, je Knoten genau ein Elternteil, aufloesbare Kindreferenzen und schemakonforme Inhalte.
  *
  * ==Phasen und Abbruch==
  *
  * Die Pruefung laeuft in Stufen und bricht nach der ersten fehlerhaften ab. Das ist kein
  * vorzeitiges Aufgeben, sondern Diagnosequalitaet: aus einer einzigen ins Leere zeigenden
  * Kindreferenz folgen sonst zwangslaeufig Unerreichbarkeits- und Schemafehler, die nur die
  * eigentliche Ursache zudecken.
  *
  *   1. Identitaet -- doppelte IDs.
  *   1. Referenzen -- Wurzel vorhanden und Container, Kinder aufloesbar, je Knoten ein Elternteil,
  *      Wurzel nirgends Kind.
  *   1. Zyklen.
  *   1. Erreichbarkeit von der Wurzel.
  *   1. Schema -- Deskriptor vorhanden, Container als [[ElementNodeType]] registriert, fachliche
  *      Pruefung des Deskriptors.
  *
  * Erst Stufe 5 braucht ein lesbares Dokument; sie laeuft deshalb gegen den bereits gebauten,
  * strukturell gueltigen Kandidaten.
  *
  * Alle Traversierungen sind iterativ (§8.3).
  */
object DocumentValidator:

  private val White = 0
  private val Gray  = 1
  private val Black = 2

  /** Prueft und liefert das Dokument, oder alle Verletzungen der ersten fehlerhaften Stufe. */
  def validate(
      schema: Schema,
      rootId: NodeId,
      nodes: Iterable[EditorNode]
  ): Either[Vector[Violation], Document] =
    indexById(nodes) match
      case Left(violations) => Left(violations)
      case Right(byId)      =>
        checkReferences(rootId, byId) match
          case Left(violations) => Left(violations)
          case Right(parents)   =>
            val cycles = findCycles(byId)
            if cycles.nonEmpty then Left(cycles)
            else
              val unreachable = findUnreachable(rootId, byId)
              if unreachable.nonEmpty then Left(unreachable)
              else
                val candidate  = Document.trusted(schema, rootId, byId, parents)
                val schemaWork = checkSchema(candidate)
                if schemaWork.nonEmpty then Left(schemaWork) else Right(candidate)

  /** Stufe 1: eindeutige IDs. */
  private def indexById(
      nodes: Iterable[EditorNode]
  ): Either[Vector[Violation], Map[NodeId, EditorNode]] =
    val byId       = mutable.LinkedHashMap.empty[NodeId, EditorNode]
    val duplicates = Vector.newBuilder[Violation]
    nodes.foreach { node =>
      if byId.contains(node.id) then duplicates += Violation.DuplicateNodeId(node.id)
      else byId.update(node.id, node)
    }
    val found = duplicates.result()
    if found.nonEmpty then Left(found) else Right(byId.toMap)

  /** Stufe 2: Wurzel, aufloesbare Kinder, genau ein Elternteil. Liefert den Elternindex. */
  private def checkReferences(
      rootId: NodeId,
      byId: Map[NodeId, EditorNode]
  ): Either[Vector[Violation], Map[NodeId, NodeId]] =
    val violations = Vector.newBuilder[Violation]

    byId.get(rootId) match
      case None                 => violations += Violation.MissingRoot(rootId)
      case Some(_: ElementNode) => ()
      case Some(_)              => violations += Violation.RootNotAContainer(rootId)

    val parents = mutable.HashMap.empty[NodeId, NodeId]

    // Deterministische Reihenfolge der Meldungen: sonst haengt die Fehlerausgabe an der
    // Hash-Reihenfolge der Map, und Testfixtures waeren nicht reproduzierbar.
    byId.keys.toVector.sorted.foreach { parentId =>
      byId(parentId) match
        case element: ElementNode =>
          val seen = mutable.HashSet.empty[NodeId]
          element.children.zipWithIndex.foreach { (childId, index) =>
            if !byId.contains(childId) then
              violations += Violation.MissingChild(parentId, childId, index)
            else if !seen.add(childId) then
              violations += Violation.DuplicateChild(parentId, childId, index)
            else if childId == rootId then violations += Violation.RootIsChild(rootId, parentId)
            else
              parents.get(childId) match
                case Some(existing) =>
                  violations += Violation.MultipleParents(childId, existing, parentId)
                case None => parents.update(childId, parentId)
          }
        case _ => ()
    }

    val found = violations.result()
    if found.nonEmpty then Left(found) else Right(parents.toMap)

  /** Stufe 3: Zyklen, iterative Tiefensuche mit Faerbung.
    *
    * Laeuft ueber *alle* Knoten, nicht nur ueber die von der Wurzel erreichbaren. Ein Zyklus in
    * einem abgehaengten Teilgraphen ist genauso ein Vertragsbruch, und die Erreichbarkeitsstufe
    * danach koennte ihn nur als Unerreichbarkeit melden -- eine wahre, aber irrefuehrende Diagnose.
    */
  private def findCycles(byId: Map[NodeId, EditorNode]): Vector[Violation] =
    val color  = mutable.HashMap.empty[NodeId, Int].withDefaultValue(White)
    val cycles = mutable.LinkedHashSet.empty[NodeId]

    def childrenOf(id: NodeId): Vector[NodeId] = byId.get(id) match
      case Some(element: ElementNode) => element.children
      case _                          => Vector.empty

    byId.keys.toVector.sorted.foreach { start =>
      if color(start) == White then
        val stack = mutable.Stack.empty[(NodeId, Iterator[NodeId])]
        color.update(start, Gray)
        stack.push(start -> childrenOf(start).iterator)
        while stack.nonEmpty do
          val (current, remaining) = stack.top
          if remaining.hasNext then
            val child = remaining.next()
            color(child) match
              case White =>
                color.update(child, Gray)
                stack.push(child -> childrenOf(child).iterator)
              case Gray => cycles += child
              case _    => ()
          else
            stack.pop()
            color.update(current, Black)
    }

    cycles.toVector.map(Violation.Cycle.apply)

  /** Stufe 4: Erreichbarkeit von der Wurzel, iterativ. */
  private def findUnreachable(rootId: NodeId, byId: Map[NodeId, EditorNode]): Vector[Violation] =
    val visited = mutable.HashSet.empty[NodeId]
    val pending = mutable.Stack(rootId)
    while pending.nonEmpty do
      val current = pending.pop()
      if visited.add(current) then
        byId.get(current) match
          case Some(element: ElementNode) => element.children.foreach(pending.push)
          case _                          => ()

    byId.keys.toVector.sorted.filterNot(visited.contains).map(Violation.UnreachableNode.apply)

  /** Stufe 5: Schemakonformitaet, gegen den strukturell bereits gueltigen Kandidaten. */
  private def checkSchema(document: Document): Vector[Violation] =
    val violations = Vector.newBuilder[Violation]
    document.ids.toVector.sorted.foreach { id =>
      val node = document.node(id).get
      document.schema.descriptorFor(node) match
        case None =>
          violations += Violation.UnknownNodeType(id, node.getClass.getSimpleName)
        case Some(descriptor) =>
          node match
            case _: ElementNode if !descriptor.isInstanceOf[ElementNodeType[?]] =>
              violations += Violation.MissingElementDescriptor(id, descriptor.typeId)
            case _ =>
              violations ++= validateWith(descriptor, node, document)
    }
    violations.result()

  /** Ruft `validate` typsicher auf: der Deskriptor liefert seinen eigenen Typzeugen (§8.1). */
  private def validateWith[N <: EditorNode](
      descriptor: NodeType[N],
      node: EditorNode,
      document: DocumentRead
  ): Vector[Violation] =
    descriptor.project(node).fold(Vector.empty)(descriptor.validate(_, document))
