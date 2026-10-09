package ember.editor.core

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
