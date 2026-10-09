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
