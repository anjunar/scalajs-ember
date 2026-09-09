package ember.editor.core

/** Ein vollstaendiger, unveraenderlicher Sitzungszustand.
  *
  * ==Zwei Revisionen==
  *
  * §9 verlangt, dass Dokument- und Sitzungsrevision getrennt werden koennen, "damit reine
  * Selection-Aenderungen keine Persistenz ausloesen". Genau dafuer stehen hier zwei Zahlen:
  *
  *   - [[revision]] steigt bei jeder veroeffentlichten Aenderung, auch bei einer reinen Auswahl-
  *     oder Feldaenderung.
  *   - [[documentRevision]] steigt nur, wenn sich das Dokument tatsaechlich geaendert hat. Wer
  *     speichert, vergleicht diese Zahl -- ein bewegter Cursor loest dann keinen Schreibvorgang
  *     aus.
  *
  * Beide steigen auch bei Undo: der wiederhergestellte Inhalt ist ein neuer Stand, kein
  * Zurueckdrehen der Uhr (§9).
  *
  * ==Snapshots==
  *
  * Ausserhalb jeder Update-Closure lesbar, und ein gelesener Zustand bleibt genau der gelesene. Ein
  * alter Knoten liest niemals selbsttaetig neue Daten nach -- Lexicals `getLatest`/`getWritable`
  * wird ausdruecklich nicht uebernommen (§3.2).
  *
  * SSR rendert das [[document]] ohne [[selection]], ohne History und ohne Fokus (§9).
  */
final case class EditorState(
    document: Document,
    selection: Option[Selection],
    revision: Revision,
    documentRevision: Revision,
    fields: StateFields
)

object EditorState:

  def initial(document: Document, fields: Seq[StateField[?]] = Seq.empty): EditorState =
    EditorState(
      document = document,
      selection = None,
      revision = Revision.initial,
      documentRevision = Revision.initial,
      fields = StateFields.initial(fields)
    )

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

/** Eine veroeffentlichte Aenderung.
  *
  * ==Was ein Commit nicht bedeutet==
  *
  * '''Nicht''', dass irgendetwas gerendert wurde. §5 trennt beides ausdruecklich: der Kern
  * veroeffentlicht genau einen unveraenderlichen Stand, die Projektion meldet ihren Abschluss
  * getrennt. Ein Commit-Konsument darf [[current]] lesen, aber nicht daraus schliessen, dass eine
  * View schon fertig ist -- dafuer gibt es ab P09 den eigenen `afterProjection`-Vertrag.
  */
final case class Commit(
    previous: EditorState,
    current: EditorState,
    changes: ChangeSet,
    mapping: PositionMapping,
    meta: TransactionMeta
):

  /** Nichts hat sich geaendert. Erzeugt keine Benachrichtigung und keine History-Stufe (§10). */
  def isNoOp: Boolean = current.revision == previous.revision

  def documentChanged: Boolean = current.documentRevision != previous.documentRevision

  def selectionChanged: Boolean = current.selection != previous.selection

  /** Die Abbildung mit ihren beiden Staenden, wie [[Bookmark.resolve]] sie erwartet. */
  def revisionMapping: RevisionMapping =
    RevisionMapping(previous.revision, current.revision, mapping)
