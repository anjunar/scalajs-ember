package ember.editor.core

/** A field together with a value of its own type.
  *
  * The typed way to carry "this field had this value" across a module boundary. The history holds a
  * `Vector[FieldValue[?]]` in each snapshot; without this pair it would hold `Any` and cast on the
  * way out -- which is the public `Map[String, Any]` that §8.1 rules out, only with extra steps.
  */
final class FieldValue[A] private (val field: StateField[A], val value: A):

  /** Writes the value back into a draft. */
  def applyTo(transaction: Transaction): Unit = transaction.setField(field, value)

  /** The same, from inside a command handler. */
  def applyTo(scope: TransformScope): Unit = scope.setField(field, value)

  override def toString: String = s"${field.name}=$value"

object FieldValue:
  def of[A](field: StateField[A], value: A): FieldValue[A] = new FieldValue(field, value)
