package ember.editor.core

/** Der fertig gerechnete, aber noch nicht veroeffentlichte Stand einer Transaktion.
  *
  * Was [[PreCommitRule]]n und [[StateField.reduce]] zu sehen bekommen (§10, Schritt 5). Sie pruefen
  * gegen den '''normalisierten Kandidaten''', nicht gegen einen Zwischenstand -- eine Regel, die
  * auf halbem Weg urteilt, urteilt ueber etwas, das es nie geben wird.
  *
  * Es gibt hier bewusst keine Revision: der Kandidat hat noch keine. Sie entsteht erst bei der
  * Veroeffentlichung, und zwar nur, wenn ueberhaupt etwas veroeffentlicht wird.
  */
final case class CommitCandidate(
    previous: EditorState,
    document: Document,
    selection: Option[Selection],
    changes: ChangeSet,
    mapping: PositionMapping,
    fields: StateFields,
    meta: TransactionMeta
):

  def documentChanged: Boolean = changes.nonEmpty

  def selectionChanged: Boolean = selection != previous.selection
