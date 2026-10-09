package ember.editor.forms

import ember.editor.core.*

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
