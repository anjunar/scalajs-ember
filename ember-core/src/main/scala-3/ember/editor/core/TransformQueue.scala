package ember.editor.core

/** Fuehrt Transforms bis zum Fixpunkt aus (§10, Schritt 4).
  *
  * ==Die Reihenfolge==
  *
  * Drei Ebenen, in dieser Rangfolge:
  *
  *   1. '''Phase''' -- Early, Normalize, Late.
  *   1. '''Knoten''' -- innerhalb einer Phase die tiefsten zuerst, die Wurzel zuletzt. Das ist
  *      §3.4s Befund an Lexical: Blaetter vor absichtlich schmutzigen Elementen, Root am Ende. Ein
  *      Transform, der einen Textlauf normalisiert, soll fertig sein, bevor der Transform des
  *      Elternblocks ueber dessen Kinder urteilt.
  *   1. '''Registrierung''' -- die Reihenfolge, in der die Extensions aufgeloest wurden.
  *
  * ==Der Fixpunkt==
  *
  * Eine Runde verarbeitet alle bisher geaenderten Knoten. Aendert sie das Dokument nicht mehr, ist
  * der Fixpunkt erreicht. Weil Transforms idempotent sein muessen, ist es unschaedlich, dass eine
  * spaetere Runde auch bereits normalisierte Knoten noch einmal ansieht -- es kostet nur Zeit, und
  * begrenzt ist es ohnehin. Eine inkrementelle Dirty-Menge waere schneller und wird eingefuehrt,
  * wenn eine Messung sie rechtfertigt (§8.3), nicht auf Verdacht.
  *
  * Laeuft das Budget ab, '''scheitert''' die Transaktion. §10 ist da eindeutig: das Budget ist kein
  * stilles Abschneiden der Normalisierung. Die Meldung nennt die beteiligten Transforms, damit der
  * Verursacher auffindbar ist und nicht nur die Symptome.
  */
private[core] object TransformQueue:

  private val phases = Vector(TransformPhase.Early, TransformPhase.Normalize, TransformPhase.Late)

  def run(
      transaction: Transaction,
      transforms: Vector[Transform[?]],
      budget: TransformBudget
  ): Either[UpdateError, Unit] =
    if transforms.isEmpty then Right(())
    else
      val scope   = new TransformScope(transaction)
      var round   = 0
      var settled = false
      var failure = Option.empty[UpdateError]

      while !settled && failure.isEmpty && round < budget.maxRounds do
        val before = transaction.document
        runRound(transaction, scope, transforms)
        failure = transaction.failure
        settled = transaction.document eq before
        round += 1

      failure match
        case Some(error)     => Left(error)
        case None if settled => Right(())
        case None            =>
          Left(
            UpdateError.TransformBudgetExhausted(
              budget.maxRounds,
              transforms.map(_.name).distinct.sorted
            )
          )

  private def runRound(
      transaction: Transaction,
      scope: TransformScope,
      transforms: Vector[Transform[?]]
  ): Unit =
    phases.foreach { phase =>
      val active = transforms.filter(_.phase == phase)
      if active.nonEmpty then
        dirtyInOrder(transaction).foreach { nodeId =>
          // Ein frueherer Transform derselben Runde kann den Knoten entfernt haben.
          if transaction.failure.isEmpty && transaction.document.contains(nodeId) then
            active.foreach(applyOne(transaction, scope, _, nodeId))
        }
    }

  /** Geaenderte und noch vorhandene Knoten, tiefste zuerst, Wurzel zuletzt. */
  private def dirtyInOrder(transaction: Transaction): Vector[NodeId] =
    val document = transaction.document
    transaction.currentChanges.changedNodes.toVector
      .filter(document.contains)
      .map(nodeId => (nodeId, document.pathIndices(nodeId).length))
      .sortBy((nodeId, depth) => (-depth, nodeId.value))
      .map(_._1)

  /** Der Deskriptor liefert den Typzeugen, bevor der typisierte Transform laeuft (§8.1). */
  private def applyOne[N <: EditorNode](
      transaction: Transaction,
      scope: TransformScope,
      transform: Transform[N],
      nodeId: NodeId
  ): Unit =
    if transaction.failure.isEmpty then
      transaction.document
        .node(nodeId)
        .flatMap(transform.nodeType.project)
        .foreach(transform.transform(_, scope))
