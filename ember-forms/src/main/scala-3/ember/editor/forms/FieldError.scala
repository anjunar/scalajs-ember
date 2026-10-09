package ember.editor.forms

import ember.editor.core.*

/** A failure of the format boundary. */
sealed trait FieldError extends EditorError

object FieldError:

  /** The document cannot be written in this field's format.
    *
    * Carries the diagnostics rather than a message, because §16 asks for a lossy conversion to be
    * an '''explicit''' choice with a diagnosis, not a yes-or-no.
    */
  final case class NotRepresentable(codec: String, reasons: Vector[String]) extends FieldError:
    def message: String =
      reasons.mkString(
        s"Das Dokument laesst sich nicht als `$codec` darstellen: ",
        "; ",
        ". Ein Feld mit `LossPolicy.AllowLossy` nimmt es unter Diagnose an."
      )

  /** The submitted string is not a document in this field's format. */
  final case class NotDecodable(codec: String, reason: String) extends FieldError:
    def message: String = s"Der Quelltext ist kein gueltiges `$codec`: $reason"

  /** A draft was written against a document revision that no longer exists.
    *
    * §16: "Wechsel/Submit importiert den Draft gegen seine Baseline atomar." When the document
    * moved underneath, importing anyway would silently discard whatever moved it.
    */
  final case class StaleDraft(baseline: Long, current: Long) extends FieldError:
    def message: String =
      s"Der Entwurf wurde gegen Revision $baseline geschrieben, das Dokument steht bei $current. " +
        "Der sichtbare Text bleibt erhalten; ein Verwerfen ist eine ausdrueckliche Formaktion."

  /** An independent change arrived while a source draft was open.
    *
    * §16 allows two answers and the application picks one through [[IntentPolicy]]: defer the
    * change until the draft lands, or refuse it now. This is the refusal.
    */
  final case class SourceBusy(what: String) extends FieldError:
    def message: String =
      s"`$what` ist abgewiesen, solange der Quelltext bearbeitet wird. " +
        "Mit `IntentPolicy.Defer` wird die Aenderung stattdessen zurueckgestellt."
