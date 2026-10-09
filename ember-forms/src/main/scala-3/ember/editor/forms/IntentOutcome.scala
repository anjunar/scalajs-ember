package ember.editor.forms

import ember.editor.core.*

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
