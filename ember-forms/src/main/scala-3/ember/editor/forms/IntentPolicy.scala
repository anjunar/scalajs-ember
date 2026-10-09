package ember.editor.forms

import ember.editor.core.*

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
