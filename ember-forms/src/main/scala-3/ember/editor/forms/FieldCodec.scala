package ember.editor.forms

import ember.editor.core.*

/** The format a form field speaks.
  *
  * ==Why the application has to choose one==
  *
  * §16: "Nicht jeder Custom Node ist verlustfrei in Markdown darstellbar. Die Anwendung waehlt
  * daher ausdruecklich `MarkdownField(profile)` oder `JsonDocumentField(schema)`."
  *
  * There is no default and no automatic fallback. A field that quietly switched formats when a
  * document outgrew the first one would produce a form value whose meaning depends on the content
  * -- and a server has no way to tell which it got.
  *
  * ==Why encoding can fail==
  *
  * Because the format boundary is real. A Markdown field cannot carry an underlined run, and §16
  * asks for that to be decided '''before''' the commit rather than reported afterwards: "Ein
  * `ToggleUnderline` in einem Strict-CommonMark-Feld kann daher keinen kanonischen Zustand
  * erzeugen, dessen Formwert veraltet bleibt."
  *
  * [[EditorField]] is what makes that happen -- it is a [[StateField]] whose reducer runs this
  * codec against the finished candidate and rejects the transaction when it refuses.
  */
trait FieldCodec:

  /** Shown in diagnostics. */
  def name: String

  /** The submit value for a document, or why this document has none.
    *
    * Pure and synchronous, because a [[StateField]] reducer is: the core runs it for candidates
    * that are about to be discarded, and it must not touch anything outside.
    */
  def encode(document: Document): Either[EditorError, String]

  /** A document from a submitted string.
    *
    * Takes the schema and the root id rather than capturing them, and that is not a detail: a
    * document built against a *different* `Schema` instance is a foreign document, and
    * `Transaction.restore` refuses it. The only schema that can be right is the session's, and only
    * the caller has it.
    */
  def decode(source: String, schema: Schema, rootId: NodeId): Either[EditorError, Document]

  /** What an empty field holds. Not `""` for every format: an empty JSON field is not an empty
    * string, it is an empty document's payload -- and a server that receives `""` cannot tell an
    * empty document from a missing field.
    */
  def emptyValue(schema: Schema, rootId: NodeId): String

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
