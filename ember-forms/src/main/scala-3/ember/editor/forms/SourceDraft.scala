package ember.editor.forms

import ember.editor.core.*

/** What the user is typing in the textarea, and what it was written against.
  *
  * ==A draft is not a second document==
  *
  * §16 says it twice, and both halves matter: "Der Draft ist ein nicht bestaetigter Eingabewert,
  * keine zweite kanonische Dokumentrepraesentation." and "Document→Form-Projektion darf diesen
  * Draft nicht ueberschreiben."
  *
  * So this type holds a string, not a document, and nothing derives a document from it until the
  * user asks. The two failure modes it exists to prevent are symmetric:
  *
  *   - the document changing underneath and silently replacing what someone typed, and
  *   - the draft being treated as truth and silently discarding a change it never saw.
  *
  * The baseline is what makes the second one detectable. A draft written against revision 7 and
  * applied at revision 9 is stale, and [[FieldError.StaleDraft]] says so instead of guessing.
  *
  * @param text
  *   what is in the textarea. Unconfirmed input -- it may not parse, and that is not an error until
  *   someone applies it.
  * @param baseline
  *   the document revision the draft was opened against.
  * @param selection
  *   where the caret is in the textarea. Kept because a round trip through the form must not move
  *   it (§16 names it among what the draft owns).
  */
final case class SourceDraft(
    text: String,
    baseline: Revision,
    selection: Option[SourceSelection] = None
):

  /** Whether the document has moved since this draft was opened. */
  def isStaleAt(state: EditorState): Boolean = state.documentRevision != baseline

/** A range in the textarea, in UTF-16 units -- the same counting as everywhere else (§11). */
final case class SourceSelection(start: Int, end: Int):
  def isCollapsed: Boolean = start == end

/** Which of §16's two answers an application gives to a change during source editing.
  *
  * §16: "Unabhaengige Dokumentaenderungen, einschliesslich Upload-Completion, werden waehrend
  * dieser Bearbeitung als Intent zurueckgestellt '''oder''' mit `SourceBusy` abgewiesen." Both are
  * allowed, so it is a value the application sets rather than a decision this module makes.
  */
enum IntentPolicy:

  /** Queue it. After the draft lands, §16 asks for the queue to be '''re-validated''', not replayed
    * -- the document it was computed against is gone.
    */
  case Defer

  /** Refuse it now, with [[FieldError.SourceBusy]]. Honest for anything a user triggered and would
    * otherwise wait for without feedback.
    */
  case Reject

/** What a submit does while a draft is open.
  *
  * §16: "Wechsel/Submit importiert den Draft gegen seine Baseline atomar."
  */
enum SubmitPolicy:

  /** Import the draft first. A decode or conflict error blocks the submit '''and keeps the visible
    * string''' -- §16 is explicit that the text survives a failed import.
    */
  case ImportDraft

  /** Refuse to submit while anything is unconfirmed. For a form where an implicit import would be a
    * surprise.
    */
  case RefuseWhileDirty

/** What happened to a change that arrived while the source was being edited. */
enum IntentOutcome[+A]:

  case Applied[A](result: A) extends IntentOutcome[A]

  /** Queued. It will be offered again after the draft lands, against the document of that moment --
    * which is why it is a '''thunk''' and not a computed value.
    */
  case Deferred extends IntentOutcome[Nothing]

  case Refused(error: FieldError) extends IntentOutcome[Nothing]

  def toOption: Option[A] = this match
    case Applied(result) => Some(result)
    case _               => None
