package ember.editor.core

/** The restricted access transforms and command handlers get to the draft.
  *
  * ==Why not the whole transaction==
  *
  * §10 requires that transforms read only the draft and have no DOM or network side effects.
  * Documentation that merely claims as much would be weak -- this type makes it a matter of
  * construction: there is no `setSelection` here, no `dispatch` and no field access, so a transform
  * cannot quietly grow a normalisation into a selection change or a command cascade.
  *
  * Command handlers get the same access. They may set the selection through the corresponding
  * primitive, but trigger no further dispatch -- a command chain that extends itself is exactly the
  * hidden reentrancy §10 rules out.
  */
final class TransformScope private[core] (private val transaction: Transaction):

  def document: Document = transaction.document

  def reject(error: EditorError): Either[UpdateError, Unit] = transaction.reject(error)
  def track(point: Point): DraftBookmark                    = transaction.track(point)

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
    * A Handler, dessen Verhalten von the Herkunft abhaengt -- History is the Case, for the it
    * gebaut was --, braucht sie; raten can he sie not.
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
