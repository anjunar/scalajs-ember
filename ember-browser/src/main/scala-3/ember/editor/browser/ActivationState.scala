package ember.editor.browser

import ember.editor.core.*

/** What the editor may do after hydration, and why it may not do it yet.
  *
  * §17 step 6: "Nach lokal vollstaendig erfolgreichem Claim '''und''' aeusserem Hydration-Abschluss
  * werden Controller und `contenteditable` aktiviert."
  *
  * Two conditions, not one. A boundary can claim its own subtree successfully while the page around
  * it is still hydrating, and activating then would put a live editor next to markup that is still
  * being adopted.
  */
enum ActivationState:

  /** Nothing has happened yet. */
  case Pending

  /** The claim succeeded and the page finished, but §17 says wait.
    *
    * The two reasons are different and both matter:
    *
    *   - a composition is running, or the field was already focused and one '''might''' be (§17.2,
    *     §17.6);
    *   - the source was edited before the script ran and has not been imported yet (§17.4).
    */
  case Deferred(reason: DeferralReason)

  /** Editable. §17: "Editierbarkeit erst nach erfolgreichem Abschluss." */
  case Active

  /** The claim failed. The fallback outside the boundary is what the user still has. */
  case Failed(problem: String)

  def isEditable: Boolean = this == ActivationState.Active
