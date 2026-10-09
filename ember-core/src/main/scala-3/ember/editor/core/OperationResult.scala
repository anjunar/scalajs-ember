package ember.editor.core

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
