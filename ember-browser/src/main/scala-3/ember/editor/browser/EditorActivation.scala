package ember.editor.browser

import ember.editor.core.*

/** What the editor may do after hydration, and why it may not do it yet.
  *
  * §17 step 6: "Nach lokal vollstaendig erfolgreichem Claim '''und''' aeusserem
  * Hydration-Abschluss werden Controller und `contenteditable` aktiviert."
  *
  * Two conditions, not one. A boundary can claim its own subtree successfully while the page
  * around it is still hydrating, and activating then would put a live editor next to markup that
  * is still being adopted.
  */
enum ActivationState:

  /** Nothing has happened yet. */
  case Pending

  /** The claim succeeded and the page finished, but §17 says wait.
    *
    * The two reasons are different and both matter:
    *
    *   - a composition is running, or the field was already focused and one '''might''' be
    *     (§17.2, §17.6);
    *   - the source was edited before the script ran and has not been imported yet (§17.4).
    */
  case Deferred(reason: DeferralReason)

  /** Editable. §17: "Editierbarkeit erst nach erfolgreichem Abschluss." */
  case Active

  /** The claim failed. The fallback outside the boundary is what the user still has. */
  case Failed(problem: String)

  def isEditable: Boolean = this == ActivationState.Active

/** Why activation is waiting. */
enum DeferralReason:

  /** A composition might be running in the source field.
    *
    * §17: "Waehrend bekannter Textarea-Composition oder bei unbekannter Eingabesitzung eines
    * bereits fokussierten Felds wird der Wechsel aufgeschoben."
    */
  case InputSession

  /** The source differs from what the server rendered, and nobody has imported it.
    *
    * §17.4: "Falls Source seit SSR geaendert wurde, bleibt sie zunaechst unangetastet. […]
    * Parsingfehler lassen den Draft editierbar und verhindern Enhancement."
    */
  case UnimportedSource

  /** The page has not finished hydrating. */
  case PageHydrating

  def render: String = this match
    case InputSession     => "eine Eingabesitzung im Quelltextfeld ist offen oder koennte es sein"
    case UnimportedSource => "der Quelltext wurde seit dem Rendern geaendert"
    case PageHydrating    => "die Seite hydriert noch"

/** The activation decision of §17, as a value rather than as a sequence of calls.
  *
  * ==Why a decision function and not a controller==
  *
  * Because every condition §17 names is a property of things the caller already has -- the
  * snapshot, whether the claim succeeded, whether the page finished, whether the source was
  * imported. A controller would own copies of those and could disagree with them.
  *
  * So this is pure, and the object that owns the lifecycle calls it whenever something moves.
  * "Wiederholtes Enhancement" is then idempotent by construction (§17's deviation table asks for
  * exactly that): asking twice with the same inputs gives the same answer, and the caller acts
  * only on a change.
  */
object EditorActivation:

  /** @param claimed
    *   whether the local boundary finished its claim without a mismatch.
    * @param pageHydrated
    *   whether the outer hydration completed. §17 requires both.
    * @param snapshot
    *   what was captured before the first claim, if this field had a source control.
    * @param servedSource
    *   what the server rendered into the textarea. Compared against the snapshot to see whether
    *   the user got there first.
    * @param sourceImported
    *   whether a differing source has since been parsed and projected (§17.4).
    */
  def decide(
      claimed: Boolean,
      pageHydrated: Boolean,
      snapshot: Option[HydrationSnapshot],
      servedSource: String,
      sourceImported: Boolean
  ): ActivationState =
    if !claimed then ActivationState.Failed("Der lokale Claim ist fehlgeschlagen.")
    else if !pageHydrated then ActivationState.Deferred(DeferralReason.PageHydrating)
    else
      snapshot match
        case Some(value) if HydrationSnapshot.unknownInputSession(value) =>
          ActivationState.Deferred(DeferralReason.InputSession)
        case Some(value) if value.differsFrom(servedSource) && !sourceImported =>
          ActivationState.Deferred(DeferralReason.UnimportedSource)
        case _ => ActivationState.Active

  /** Whether a captured selection may be written into the rich view.
    *
    * §17 step 7: "'''Nur wenn''' der Nutzer das Feld tatsaechlich fokussiert hatte, kann eine
    * uebersetzte Selection mit entsprechender Richtung in die Rich-Ansicht uebernommen werden.
    * Ansonsten keine Fokus-/Selection-Schreibaktion."
    *
    * The negative half is the one that costs something to get wrong: an editor that focuses
    * itself on load steals focus from wherever the user actually was.
    */
  def mayRestoreSelection(snapshot: Option[HydrationSnapshot]): Boolean =
    snapshot.exists(_.focused)
