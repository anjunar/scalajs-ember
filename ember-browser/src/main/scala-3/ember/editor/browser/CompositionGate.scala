package ember.editor.browser

import ember.editor.core.*

/** The pre-commit rule that enforces it.
  *
  * ==Why a `PreCommitRule` and not a check in the controller==
  *
  * Because §10's step 5 is exactly the moment §15.3 names -- "vor Commit" -- and because a check in
  * the controller would only see what goes through the controller. A feature module's command, a
  * timer, an arriving upload: all of those reach the session directly, and all of them are
  * "unabhaengige Dokumenttransaktionen".
  *
  * ==What stays allowed==
  *
  * §15.3: "Erlaubt bleiben zugehoerige native Composition-Updates sowie reine Selection-/View-/
  * Effect-Aenderungen." So the rule looks at two things: whether the document changed at all, and
  * whether the transaction carries the composition's own tag.
  */
object CompositionGate:

  /** The tag a composition's own transactions carry.
    *
    * Named in the core (§14's typed metadata) because the rich-text profile reads the same word to
    * defer its merges (§8.2), and two modules that never meet cannot agree on a string each spells
    * for itself.
    */
  val Tag: String = TransactionMeta.CompositionTag

  /** Meta for a transaction that belongs to the running composition. */
  def meta(label: String): TransactionMeta =
    TransactionMeta(origin = Origin.User, label = Some(label), tags = Set(Tag))

  /** @param running
    *   the id of the composition in progress, if there is one. Read at check time, because a rule
    *   outlives any single composition.
    */
  def rule(running: () => Option[Long]): PreCommitRule =
    PreCommitRule("browser.composition-busy") { candidate =>
      running().flatMap { session =>
        if !candidate.documentChanged then None
        else if candidate.meta.tags.contains(Tag) then None
        else Some(CompositionBusy(session, DiagnosticPath.Root))
      }
    }
