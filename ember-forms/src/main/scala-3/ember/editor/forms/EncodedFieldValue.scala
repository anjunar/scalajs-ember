package ember.editor.forms

import ember.editor.core.*

/** The submit value of a field, and the revision it belongs to.
  *
  * ==Why the revision travels with the string==
  *
  * §16: "Die anschliessende Formprojektion uebernimmt diesen bereits geprueften Wert derselben
  * Revision." A string on its own cannot say whether it is current, and a form that writes a value
  * computed one commit ago sends a payload the user never saw.
  *
  * Pairing them makes the staleness checkable rather than assumed -- and it is what lets the
  * binding refuse to write a value whose revision does not match the session's.
  */
final case class EncodedFieldValue(revision: Option[Revision], value: String):

  /** Whether this value was computed for exactly this state.
    *
    * `None` is the initial value, and it belongs to no state at all. That distinction is not
    * pedantry: revision zero is a real revision, so an initial value that claimed it would be
    * mistaken for a current one -- and a form would send an empty payload for a document that has
    * content.
    */
  def isFor(state: EditorState): Boolean = revision.contains(state.revision)
