package ember.editor.core

/** Eine primitive Dokumentaenderung.
  *
  * Bewusst wenige, bewusst klein. Fachliche Operationen wie `insertParagraph` oder `toggleMark`
  * bauen die Feature-Module darauf auf (§10); der Kern kennt sie nicht. Jede Operation ist atomar:
  * sie gelingt vollstaendig oder aendert nichts.
  *
  * Geschlossenes ADT -- Operationen sind eine strukturelle Kategorie (§8.1). Ein Feature-Modul
  * erweitert den Editor durch neue Node-Arten und Commands, nicht durch neue Primitive.
  */
enum Operation:

  /** Fuegt einen Teilbaum als `index`-tes Kind von `parent` ein.
    *
    * `descendants` enthaelt alle Nachfahren von `node` in beliebiger Reihenfolge. Ein Teilbaum
    * statt eines einzelnen Knotens, weil sonst jeder Zwischenstand ungueltig waere: ein Knoten mit
    * Kindreferenzen auf noch nicht eingefuegte Knoten verletzt die Invarianten, und §10 verlangt,
    * dass keine Zwischenzustaende sichtbar werden.
    */
  case Insert(parent: NodeId, index: Int, node: EditorNode, descendants: Vector[EditorNode])

  /** Entfernt einen Knoten samt Teilbaum. */
  case Remove(nodeId: NodeId)

  /** Verschiebt einen Knoten samt Teilbaum.
    *
    * `index` ist die endgueltige Position '''nach''' der Herausnahme aus dem bisherigen Parent --
    * dieselbe Festlegung wie bei `Runtime.move` in ui-core. Beim Umsortieren innerhalb desselben
    * Parents ist das der Unterschied zwischen richtig und um eins daneben.
    */
  case Move(nodeId: NodeId, newParent: NodeId, index: Int)

  /** Ersetzt den Inhalt eines Knotens unter Beibehaltung von Identitaet und Kindern.
    *
    * Das ist die "updated"-Operation: Marks aendern, Ueberschriftenebene wechseln, Metadaten
    * setzen. Strukturaenderungen laufen ausdruecklich nicht hierueber -- die Kindliste muss gleich
    * bleiben, sonst waeren die bisherigen Kinder Waisen. Dafuer gibt es
    * [[Insert]]/[[Remove]]/[[Move]].
    */
  case Replace(nodeId: NodeId, replacement: EditorNode)

  /** Aendert Text an Ort und Stelle, in UTF-16-Koordinaten. */
  case SpliceText(nodeId: NodeId, start: Int, deleteCount: Int, inserted: String)

  /** Teilt einen Textlauf. Links behaelt die ID, rechts bekommt `newId` (§8.3). */
  case SplitText(nodeId: NodeId, at: Int, newId: NodeId)

  /** Fuehrt zwei benachbarte Textlaeufe zusammen. Links behaelt die ID. */
  case MergeText(left: NodeId, right: NodeId)

object Operation:

  /** Fuegt ein Blatt ohne Nachfahren ein. */
  def insertLeaf(parent: NodeId, index: Int, node: EditorNode): Operation =
    Operation.Insert(parent, index, node, Vector.empty)
