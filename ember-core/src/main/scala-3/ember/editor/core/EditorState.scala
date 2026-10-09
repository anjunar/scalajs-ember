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
