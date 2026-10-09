package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** Whether an export may drop what Markdown cannot express (§18.2). */
enum LossPolicy:

  /** Refuses an export that would lose information. The default -- a silent loss is the one failure
    * mode a user cannot see.
    */
  case Strict

  /** Exports anyway, and reports what went. Has to be chosen deliberately. */
  case AllowLossy
