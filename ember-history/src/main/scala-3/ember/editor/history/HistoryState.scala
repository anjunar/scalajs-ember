package ember.editor.history

import ember.editor.core.*

/** Die Staende einer History. Ein Wert -- damit die Regeln ohne Sitzung pruefbar sind.
  *
  * @param open
  *   ob die neueste Stufe noch verschmelzen darf. Ein Auswahlsprung schliesst sie (§14), ohne sie
  *   zu entfernen.
  */
final case class HistoryState(
    undo: Vector[HistoryEntry] = Vector.empty,
    redo: Vector[HistoryEntry] = Vector.empty,
    open: Boolean = false
):

  def canUndo: Boolean = undo.nonEmpty
  def canRedo: Boolean = redo.nonEmpty

  def estimatedBytes: Int = (undo ++ redo).map(_.estimatedBytes).sum

  /** Die Stufe, in die eine naechste Aenderung noch hineinlaufen koennte. */
  def openEntry: Option[HistoryEntry] = if open then undo.lastOption else None

  /** Legt eine neue Stufe an.
    *
    * '''Loescht Redo.''' §14: "Eine neue Dokumentaenderung nach Undo loescht Redo; ein blosser
    * Selection-Wechsel tut dies nicht." Der zweite Halbsatz steht nicht hier, sondern dort, wo
    * ueber das Aufzeichnen entschieden wird -- eine reine Auswahlaenderung kommt gar nicht bis
    * hierher.
    */
  def push(entry: HistoryEntry, limits: HistoryLimits): HistoryState =
    HistoryState(undo :+ entry, Vector.empty, open = true).trimmed(limits)

  /** Fuehrt die neueste Stufe fort. `before` bleibt, wie es war -- darum geht es. */
  def merge(entry: HistoryEntry, limits: HistoryLimits): HistoryState =
    undo.lastOption match
      case None           => push(entry, limits)
      case Some(previous) =>
        val changed = for
          before <- previous.changedNodes
          after  <- entry.changedNodes
        yield before ++ after
        val merged = previous.copy(
          after = entry.after,
          kind = entry.kind,
          marks = entry.marks,
          at = entry.at,
          estimatedBytes = changed.fold(
            HistoryEntry.estimate(previous.before.document, entry.after.document)
          )(HistoryEntry.estimate(previous.before.document, entry.after.document, _)),
          changedNodes = changed
        )
        HistoryState(undo.init :+ merged, Vector.empty, open = true).trimmed(limits)

  /** Beendet die laufende Gruppe, ohne etwas zu entfernen. */
  def closed: HistoryState = if open then copy(open = false) else this

  def cleared: HistoryState = HistoryState()

  /** Nimmt die neueste Stufe zurueck und legt sie auf den Redo-Stapel. */
  def undone: Option[(HistoryEntry, HistoryState)] =
    undo.lastOption.map(entry => (entry, HistoryState(undo.init, redo :+ entry, open = false)))

  def redone: Option[(HistoryEntry, HistoryState)] =
    redo.lastOption.map(entry => (entry, HistoryState(undo :+ entry, redo.init, open = false)))

  /** Haelt Anzahl und Byte-Budget ein.
    *
    * Zuerst die Anzahl, dann das Budget, und beide von vorn -- die aelteste Stufe ist die, die am
    * wenigsten fehlt.
    *
    * '''Die neueste Stufe bleibt immer.''' Ein einzelner Eintrag, der das Budget allein sprengt,
    * kaeme sonst nie in die History, und der Benutzer koennte ausgerechnet seine letzte Aktion
    * nicht zuruecknehmen. §14 nennt den Fall und verweist ihn woandershin: "ein riesiger einzelner
    * Import ist separat zu behandeln" -- und ein Import setzt die History ohnehin zurueck.
    */
  private def trimmed(limits: HistoryLimits): HistoryState =
    var kept = if undo.length > limits.maxEntries then undo.takeRight(limits.maxEntries) else undo

    var bytes = kept.map(_.estimatedBytes).sum + redo.map(_.estimatedBytes).sum
    while kept.length > 1 && bytes > limits.maxRetainedBytes do
      bytes -= kept.head.estimatedBytes
      kept = kept.tail

    if kept eq undo then this else copy(undo = kept)

object HistoryState:
  val empty: HistoryState = HistoryState()
