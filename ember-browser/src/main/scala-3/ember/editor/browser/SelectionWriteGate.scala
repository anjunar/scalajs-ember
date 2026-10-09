package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** The rule for whether a selection may be written, as a function of what the caller knows.
  *
  * Separated from the port because it is the part worth testing without a browser: the port around
  * it is DOM plumbing, and this is the policy §11 and §22 state.
  */
object SelectionWriteGate:

  /** `None` means: go ahead. */
  def decide(
      projected: Revision,
      state: Revision,
      focusWithin: Boolean,
      intent: WriteIntent,
      capability: SelectionCapability
  ): Option[SkipReason] =
    if !capability.isSupported then Some(SkipReason.Unsupported)
    // §11: "nur nach passender Projection-Revision schreiben". Not "at least as new" -- a
    // projection ahead of the state would be just as wrong, and cannot happen.
    else if projected != state then Some(SkipReason.StaleProjection)
    else if !focusWithin && intent == WriteIntent.FollowFocus then Some(SkipReason.NotFocused)
    else None
