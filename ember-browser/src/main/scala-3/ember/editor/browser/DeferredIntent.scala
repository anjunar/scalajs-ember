package ember.editor.browser

import ember.editor.core.*

/** A change that waited for a composition to finish.
  *
  * ==Why a thunk and not a value==
  *
  * §15.3: "Es werden keine bereits berechneten Drafts spaeter angewendet." A queued change is
  * '''re-validated''' against the document that exists when it runs, not replayed against the one
  * it was written for. An upload that completed during a composition has to insert its picture
  * where the bookmark now points -- and to fail if that place is gone, rather than to insert it
  * somewhere else.
  *
  * The same reasoning as §16's deferred form intents, and deliberately the same shape.
  *
  * @param bookmark
  *   where the change wanted to happen, if it cared. Mapped forward when the queue is released; an
  *   expired one is reported, never guessed at.
  */
final case class DeferredIntent(
    label: String,
    bookmark: Option[Bookmark],
    run: (EditorSession, Option[Point]) => Either[UpdateError, Unit]
)
