package ember.editor.history

import ember.editor.core.*

/** Deterministic undo and redo through shared snapshots (§14).
  *
  * The stack belongs to this session's History instance. Undo and redo stage an immutable
  * transition in the transaction, keyed to its starting revision; the commit listener publishes it
  * only after validation and reducers succeed. A rejected transaction cannot consume a step. The
  * staging field uses Recompute, so snapshots never capture the history recursively.
  *
  * Restore publishes document and selection in one new revision (§9). A command can restore and
  * then edit within the same transaction: the additional edit becomes a new history entry, while a
  * pure restore is never recorded again.
  */
final class History(
    config: HistoryConfig = HistoryConfig.default,
    clock: HistoryClock = HistoryClock.system
) extends Extension:

  val id: ExtensionId = ExtensionId("ember.history")

  private var session: EditorSession   = null
  private var current: HistoryState    = HistoryState.empty
  private var group: Option[OpenGroup] = None
  private var unrecordedChanges        = Set.empty[NodeId]

  // Commands stage their stack transition in the draft. This field is not captured
  // in history snapshots, and expires on the next transaction. A rejected draft
  // therefore has no effect on the external history, even if a later reducer throws.
  private final case class StagedRestore(
      revision: Revision,
      target: HistorySnapshot,
      next: HistoryState
  )
  private object PendingRestore extends StateField[Option[StagedRestore]]:
    val name    = "ember.history.pending-restore"
    val initial = Option.empty[StagedRestore]
    override def reduce(
        value: Option[StagedRestore],
        candidate: CommitCandidate
    ): Either[EditorError, Option[StagedRestore]] =
      Right(value.filter(_.revision == candidate.previous.revision))

  /** Eine ausdruecklich geoeffnete Gruppe -- Composition, Drag, ein mehrstufiger Dialog. */
  private final case class OpenGroup(
      before: HistorySnapshot,
      label: Option[String],
      changedNodes: Set[NodeId] = Set.empty
  )

  override def contribute: ExtensionContributions =
    ExtensionContributions(
      fields = Vector(PendingRestore),
      commands = Vector(
        CommandRegistration(HistoryCommands.Undo)((scope, _) => run(scope, undone)),
        CommandRegistration(HistoryCommands.Redo)((scope, _) => run(scope, redone))
      )
    )

  override def install(installed: EditorSession): Subscription =
    if session != null then
      throw EditorContractViolation(
        "Diese History ist bereits installiert. Eine History gehoert genau einer Sitzung (§14)."
      )
    session = installed
    val commits = installed.onCommit(record)
    Subscription(() => {
      commits.dispose()
      reset()
      session = null
    })

  // -----------------------------------------------------------------------------------------
  // Lesen
  // -----------------------------------------------------------------------------------------

  def canUndo: Boolean = current.canUndo
  def canRedo: Boolean = current.canRedo

  /** Der Stand der Stapel. Fuer Anzeige, Tests und Diagnose. */
  def state: HistoryState = current

  /** Das geschaetzte Budget, das die Eintraege belegen. Keine Heapmessung, siehe
    * [[HistoryEntry.estimate]].
    */
  def estimatedBytes: Int = current.estimatedBytes

  // -----------------------------------------------------------------------------------------
  // Aendern
  // -----------------------------------------------------------------------------------------

  /** Nimmt die neueste Stufe zurueck. `false`, wenn es keine gab. */
  def undo(): Either[UpdateError, Boolean] = step(undone)

  def redo(): Either[UpdateError, Boolean] = step(redone)

  /** Wirft beide Stapel weg. Nach einem Import, einem Dokumentwechsel, einem Reset. */
  def reset(): Unit =
    current = HistoryState.empty
    group = None
    unrecordedChanges = Set.empty

  /** Beginnt eine ausdrueckliche Gruppe.
    *
    * §14 beschreibt sie an der `CompositionSession`: "Alle vorlaeufigen Aenderungen einer
    * CompositionSession verschmelzen zu genau einem Eintrag mit dem Zustand vor Composition-Beginn;
    * abgebrochene Composition ohne Inhaltsaenderung erzeugt keinen Eintrag." Beides faellt hier
    * zusammen: der Stand bei [[beginGroup]] wird gemerkt, und wenn bis [[endGroup]] nichts
    * passiert, entsteht nichts.
    *
    * Composition selbst gibt es noch nicht -- sie ist P23. Was es gibt, ist der Vertrag, den sie
    * benutzen wird, und er ist ohne Browser pruefbar.
    *
    * '''Nicht enthalten:''' das Zurueckstellen fremder Transaktionen waehrend der Gruppe. §14
    * verlangt es, aber es ist eine Eigenschaft der Sitzung und nicht der History -- und es betrifft
    * nur Composition, also P23.
    */
  def beginGroup(label: Option[String] = None): Unit =
    requireInstalled()
    if group.isEmpty then
      group = Some(
        OpenGroup(snapshotOf(session.state), label)
      )

  /** Schliesst die Gruppe. Hat sich nichts geaendert, bleibt die History unveraendert. */
  def endGroup(): Unit =
    group.foreach { open =>
      group = None
      if !(open.before.document eq session.document) then
        val kind = EditKind.Structural
        current = current.push(
          HistoryEntry(
            before = open.before,
            after = snapshotOf(session.state),
            kind = kind,
            marks = MarkSet.empty,
            at = clock.now(),
            label = open.label,
            estimatedBytes =
              HistoryEntry.estimate(open.before.document, session.document, open.changedNodes),
            changedNodes = Some(open.changedNodes)
          ),
          config.limits
        )
      current = current.closed
      unrecordedChanges = Set.empty
    }

  // -----------------------------------------------------------------------------------------
  // Aufzeichnen
  // -----------------------------------------------------------------------------------------

  private def record(commit: Commit): Unit =
    // Explicit groups snapshot their entire interval, including edits tagged Ignore.
    group =
      group.map(open => open.copy(changedNodes = open.changedNodes ++ commit.changes.changedNodes))
    val staged =
      commit.current.fields(PendingRestore).filter(_.revision == commit.previous.revision)
    if staged.isDefined then
      val transition = staged.get
      unrecordedChanges = Set.empty
      current = transition.next
      // If the same transaction edits the restored document, retain that edit as
      // a new structural entry, rather than silently treating it as part of Undo.
      if !(transition.target.document eq commit.current.document) then
        current = current.push(
          HistoryEntry(
            transition.target,
            snapshotOf(commit.current),
            EditKind.Structural,
            MarkSet.empty,
            clock.now(),
            commit.meta.label,
            // This delta starts before the restore, not at the restored snapshot. A
            // restored node removed again can cancel out of it; this nonlocal path diffs fully.
            HistoryEntry.estimate(transition.target.document, commit.current.document)
          ),
          config.limits
        )
    else if commit.meta.origin == Origin.History then rememberUnrecorded(commit)
    else if commit.meta.history.contains(HistoryPolicy.Ignore) then rememberUnrecorded(commit)
    else if commit.meta.origin == Origin.Import && config.resetOnImport then reset()
    else if group.isDefined then ()
    else if !commit.documentChanged then
      // §14: "Selection-only erzeugt keine zusaetzliche Undo-Stufe, erhaelt aber die passende
      // Restore-Selection fuer die naechste Bearbeitung." Beides steckt in einer Zeile: die
      // Gruppe wird geschlossen, und die naechste Stufe nimmt `commit.previous.selection` der
      // dann folgenden Aenderung -- also genau die Auswahl, die jetzt gilt.
      current = current.closed
      unrecordedChanges = Set.empty
    else append(commit)

  private def append(commit: Commit): Unit =
    val kind  = HistoryGrouping.classify(commit)
    val marks = HistoryGrouping.marksOf(kind, commit.current.document)
    val at    = clock.now()
    // A later merge spans ignored commits too: its before snapshot predates them.
    // Keep their candidate IDs without changing the ignored commit's undo state.
    val changed = commit.changes.changedNodes ++ unrecordedChanges
    unrecordedChanges = Set.empty

    val entry = HistoryEntry(
      before = snapshotOf(commit.previous),
      after = snapshotOf(commit.current),
      kind = kind,
      marks = marks,
      at = at,
      label = commit.meta.label,
      estimatedBytes = HistoryEntry
        .estimate(commit.previous.document, commit.current.document, commit.changes.changedNodes),
      changedNodes = Some(changed)
    )

    current =
      if mergesIntoOpenEntry(commit, kind, marks, at) then current.merge(entry, config.limits)
      else current.push(entry, config.limits)

  private def rememberUnrecorded(commit: Commit): Unit =
    if current.open then unrecordedChanges ++= commit.changes.changedNodes

  /** A published state as a snapshot, including the fields that asked to come along (§14). */
  private def snapshotOf(state: EditorState): HistorySnapshot =
    HistorySnapshot(state.document, state.selection, state.fields.captureForHistory)

  private def mergesIntoOpenEntry(
      commit: Commit,
      kind: EditKind,
      marks: MarkSet,
      at: Long
  ): Boolean =
    commit.meta.history match
      case Some(HistoryPolicy.Push)   => false
      case Some(HistoryPolicy.Merge)  => current.openEntry.isDefined
      case Some(HistoryPolicy.Ignore) => false // schon oben abgefangen
      case None                       =>
        current.openEntry.exists(
          HistoryGrouping.mergeable(_, kind, marks, at, config.limits)
        )

  // -----------------------------------------------------------------------------------------
  // Wiederherstellen
  // -----------------------------------------------------------------------------------------

  /** Die Zielstufe eines Undo: der Stand '''davor'''. */
  private def undone: HistoryState => Option[(HistorySnapshot, HistoryState)] =
    _.undone.map((entry, next) => (entry.before, next))

  private def redone: HistoryState => Option[(HistorySnapshot, HistoryState)] =
    _.redone.map((entry, next) => (entry.after, next))

  private def step(
      take: HistoryState => Option[(HistorySnapshot, HistoryState)]
  ): Either[UpdateError, Boolean] =
    requireInstalled()
    take(current) match
      case None                 => Right(false)
      case Some((target, next)) =>
        session
          .update(TransactionMeta.history) { transaction =>
            transaction.restore(target.document, target.selection): Unit
            target.fields.foreach(_.applyTo(transaction))
            transaction.setField(
              PendingRestore,
              Some(StagedRestore(session.state.revision, target, next))
            )
          }
          .map(_ => true)

  /** Der Weg aus §12: `editor.register(Undo) { (tx, _) => history.undo(tx) }`.
    *
    * Innerhalb eines laufenden Entwurfs -- deshalb kein eigener Commit und kein `Either`, sondern
    * ein [[CommandResult]]. Ein leerer Stapel ist kein Fehler, sondern Nichtzustaendigkeit:
    * [[CommandResult.Pass]], damit ein Fallback-Handler noch drankommt.
    */
  def undo(scope: TransformScope): CommandResult = run(scope, undone)

  def redo(scope: TransformScope): CommandResult = run(scope, redone)

  private def run(
      scope: TransformScope,
      take: HistoryState => Option[(HistorySnapshot, HistoryState)]
  ): CommandResult =
    val staged = scope.field(PendingRestore).filter(_.revision == session.state.revision)
    take(staged.map(_.next).getOrElse(current)) match
      case None                 => CommandResult.Pass
      case Some((target, next)) =>
        scope.restore(target.document, target.selection) match
          case Right(_) =>
            target.fields.foreach(_.applyTo(scope))
            scope.setField(
              PendingRestore,
              Some(StagedRestore(session.state.revision, target, next))
            )
            CommandResult.Handled
          case Left(_) =>
            // Der Entwurf hat den Fehler eingerastet; die Transaktion scheitert ohnehin. Der
            // Stapel bleibt, wie er war -- verbraucht ist eine Stufe erst, wenn sie ankommt.
            CommandResult.Handled

  private def requireInstalled(): Unit =
    if session == null then
      throw EditorContractViolation(
        "Diese History ist noch nicht installiert. Sie braucht eine Sitzung (§13)."
      )
