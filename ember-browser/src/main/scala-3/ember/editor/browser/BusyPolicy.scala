package ember.editor.browser

import ember.editor.core.*

/** What happens to a document change that arrives while a composition is running.
  *
  * §15.3 offers two answers and leaves the choice to the application, because both are defensible
  * and neither is free: refusing tells the caller now, deferring finishes the work later.
  */
enum BusyPolicy:

  /** Say no. The caller sees [[CompositionBusy]] and decides for itself. */
  case Reject

  /** Put it in the queue and run it when the composition ends. */
  case Defer
