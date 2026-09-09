package ember.editor.core

/** Was mit dem Wert eines Feldes geschieht, wenn sich das Dokument aendert.
  *
  * §9 verlangt, dass Felder das '''ausdruecklich''' erklaeren. Ein Feld, das schweigt, beantwortet
  * die Frage trotzdem -- nur unsichtbar, und dann meist falsch.
  */
enum DocumentChangePolicy:

  /** Der Wert ueberdauert Dokumentaenderungen unveraendert. */
  case Keep

  /** Der Wert faellt auf `initial` zurueck, sobald sich das Dokument aendert.
    *
    * Richtig fuer alles, was sich auf eine konkrete Stelle bezieht und nach einer Aenderung nicht
    * mehr stimmen kann -- etwa ein am Caret gemerkter Formatierungswunsch nach einem Cursorsprung.
    */
  case Reset

/** Ein typisierter Platz im Sitzungszustand.
  *
  * ==Was hier hineingehoert==
  *
  * Werte, die zur Transaktion gehoeren und einen Commit ueberdauern sollen: der Formatierungswunsch
  * am leeren Caret (`TypingMarks`, P12), ein abgeleiteter Formularwert (`EncodedFieldValue`, P19b),
  * ein Zaehler fuer ein Zeichenlimit.
  *
  * ==Was nicht==
  *
  * §9 ist da deutlich: Upload-Jobs, DOM-Knoten und Handler sind keine StateFields. Sie sind nicht
  * rein, ueberleben keine Serialisierung und muessen aufgeraeumt werden -- ein Zustandsfeld kann
  * das alles nicht leisten.
  *
  * ==Warum ein Reducer und kein Setter==
  *
  * Ein Feld ist keine frei beschreibbare Zelle. [[reduce]] laeuft im Commit gegen den fertig
  * normalisierten Kandidaten und darf mit typisiertem Fehler '''ablehnen''' (§10, Schritt 5). Genau
  * daran haengt P19b: ein `ToggleUnderline` in einem Strict-CommonMark-Feld muss vor dem Commit
  * scheitern, nicht danach einen veralteten Formularwert hinterlassen.
  *
  * Die Identitaet eines Feldes ist das Objekt selbst, nicht sein Name. [[name]] dient
  * ausschliesslich der Diagnose.
  *
  * @tparam A
  *   der Werttyp
  */
trait StateField[A]:

  /** Nur fuer Fehlermeldungen. Kein Nachschlagschluessel. */
  def name: String

  def initial: A

  def onDocumentChange: DocumentChangePolicy = DocumentChangePolicy.Keep

  /** Reiner Reducer, ausgefuehrt vor der Veroeffentlichung des Commits.
    *
    * Bekommt den Wert, wie er nach [[onDocumentChange]] und etwaigen ausdruecklichen Zuweisungen
    * der Transaktion dasteht. Darf lesen, rechnen und ablehnen -- aber nichts ausserhalb beruehren:
    * keine Netzwerkzugriffe, kein DOM, keine Seiteneffekte. Der Kern ruft ihn auch fuer verworfene
    * Kandidaten auf.
    */
  def reduce(current: A, candidate: CommitCandidate): Either[EditorError, A] = Right(current)

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
