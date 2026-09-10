package ember.editor.core

/** Warum eine Transaktion nicht veroeffentlicht wurde.
  *
  * Alle Faelle haben dieselbe Wirkung: der Sitzungszustand bleibt unveraendert. Es gibt keinen
  * halben Commit und nichts zurueckzurollen -- der bisherige Zustand wurde nie angefasst.
  */
sealed trait UpdateError extends EditorError

object UpdateError:

  /** Eine primitive Operation war nicht anwendbar. */
  final case class OperationFailed(error: OperationError) extends UpdateError:
    def message: String               = error.message
    override def path: DiagnosticPath = error.path

  /** Die Auswahl liesse sich im Ergebnis nicht darstellen. */
  final case class InvalidSelection(violations: Vector[Violation]) extends UpdateError:
    def message: String =
      violations.map(_.render).mkString("Ungueltige Auswahl: ", "; ", "")
    override def path: DiagnosticPath = violations.headOption.fold(DiagnosticPath.Root)(_.path)

  /** Eine synchrone Regel hat den Kandidaten abgelehnt. */
  final case class RuleRejected(rule: String, error: EditorError) extends UpdateError:
    def message: String               = s"Regel `$rule`: ${error.message}"
    override def path: DiagnosticPath = error.path

  /** Ein Feld-Reducer hat den Kandidaten abgelehnt (§10, Schritt 5). */
  final case class FieldRejected(field: String, error: EditorError) extends UpdateError:
    def message: String               = s"Feld `$field`: ${error.message}"
    override def path: DiagnosticPath = error.path

  /** Ein `update` innerhalb eines laufenden `update`.
    *
    * §10 schliesst das aus. Wer aus einem Listener heraus aendern will, benutzt `enqueueUpdate` --
    * damit geht der Aenderungsbedarf nicht verloren, ohne dass verdeckte Reentranz entsteht.
    */
  case object NestedUpdate extends UpdateError:
    def message: String =
      "Verschachteltes update. Aus einem Listener heraus enqueueUpdate verwenden."

  /** Die Sitzung wurde bereits entsorgt. */
  case object SessionDisposed extends UpdateError:
    def message: String = "Die Sitzung ist entsorgt."

  /** Die Normalisierung kam nicht zur Ruhe.
    *
    * §10: Das Arbeitsbudget ist ausdruecklich '''kein stilles Abschneiden''' der Normalisierung.
    * Ein halb normalisiertes Dokument zu veroeffentlichen waere schlimmer als gar keines -- die
    * Transaktion scheitert, und die Meldung nennt die beteiligten Transforms.
    */
  final case class TransformBudgetExhausted(rounds: Int, transforms: Vector[String])
      extends UpdateError:
    def message: String =
      s"Die Transforms kamen nach $rounds Runden nicht zur Ruhe. Beteiligt: " +
        transforms.mkString(", ") + "."
