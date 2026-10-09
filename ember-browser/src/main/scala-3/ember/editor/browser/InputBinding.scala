package ember.editor.browser

import ember.editor.core.*

/** Maps browser intents to dispatches on a session.
  *
  * A partial function, and that is the whole design: where it is not defined, this editor has no
  * answer for that intent, and the controller leaves the event native instead of swallowing it. An
  * editor without lists is not an editor that breaks on `insertUnorderedList`; it is one that lets
  * the browser do whatever it would have done and imports the result.
  */
type InputBinding =
  PartialFunction[InputIntent, EditorSession => Either[UpdateError, DispatchOutcome]]
