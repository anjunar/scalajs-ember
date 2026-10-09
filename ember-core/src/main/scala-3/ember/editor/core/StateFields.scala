package ember.editor.core

/** Der heterogene Feldspeicher einer Sitzung.
  *
  * Intern eine Map auf `Any`, nach aussen ausschliesslich typisiert erreichbar -- §8.1 schliesst
  * eine oeffentliche `Map[String, Any]` aus, und das gilt hier genauso. Der Typzeuge ist das Feld
  * selbst: wer `apply(field)` aufruft, bekommt zwangslaeufig dessen `A`.
  */
final class StateFields private (private val values: Map[StateField[?], Any]):

  /** Der Wert dieses Feldes, oder sein `initial`, falls die Sitzung es nicht fuehrt. */
  def apply[A](field: StateField[A]): A =
    values.get(field).map(_.asInstanceOf[A]).getOrElse(field.initial)

  def get[A](field: StateField[A]): Option[A] = values.get(field).map(_.asInstanceOf[A])

  def contains(field: StateField[?]): Boolean = values.contains(field)

  def fields: Set[StateField[?]] = values.keySet

  /** Captures the fields that declare [[HistoryRestorePolicy.Restore]].
    *
    * The cast is the same unavoidable one as in [[StateFields.initial]] and just as harmless: the
    * value stored under a field has, by construction, that field's type.
    */
  def captureForHistory: Vector[FieldValue[?]] =
    values.iterator
      .filter((field, _) => field.onHistoryRestore == HistoryRestorePolicy.Restore)
      .map((field, value) => capture(field.asInstanceOf[StateField[Any]], value))
      .toVector

  private def capture[A](field: StateField[A], value: Any): FieldValue[A] =
    FieldValue.of(field, value.asInstanceOf[A])

  private[core] def updated[A](field: StateField[A], value: A): StateFields =
    new StateFields(values.updated(field, value))

  override def equals(other: Any): Boolean = other match
    case that: StateFields => values == that.values
    case _                 => false

  override def hashCode(): Int = values.hashCode()

  override def toString: String =
    values.keys.map(_.name).toVector.sorted.mkString("StateFields(", ", ", ")")

object StateFields:

  val empty: StateFields = new StateFields(Map.empty)

  /** Legt alle Felder mit ihrem Anfangswert an.
    *
    * Der Cast auf `Any` ist hier unvermeidlich und harmlos: `field.initial` hat per Definition den
    * Typ, den `apply` fuer dasselbe Feld wieder herausgibt. Ausserhalb dieser Zeile gibt es keinen
    * untypisierten Zugriff.
    */
  def initial(fields: Seq[StateField[?]]): StateFields =
    new StateFields(fields.map(field => field -> (field.initial: Any)).toMap)
