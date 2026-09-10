package ember.editor.history

import ember.editor.core.*

/** Ein Dokumentstand samt der Auswahl, die dazu gehoert.
  *
  * §14: Eintraege tragen "strukturell geteilte Document-Snapshots und Selection vor/nach der
  * Aenderung". Strukturell geteilt heisst: zwei Snapshots, zwischen denen ein Zeichen liegt,
  * unterscheiden sich in genau einem Knoten und teilen alle uebrigen -- das ist keine Zusage
  * dieses Moduls, sondern eine Eigenschaft von [[Document]] (§8.2).
  *
  * ==Warum ein paar Felder doch mitkommen==
  *
  * §14: "StateFields deklarieren einen eigenen Restore-/Mapping-Vertrag." Die allermeisten
  * folgen aus dem Dokument und werden mit ihm wieder richtig; `TypingMarks` (P12) tut es nicht,
  * und §11 verbietet ausdruecklich, die fuer die naechste Eingabe wirksamen Marks nach einem
  * Undo aus der Darstellung zu erraten. Aufgenommen wird deshalb genau das, was ein Feld ueber
  * [[HistoryRestorePolicy]] anmeldet -- typisiert als [[FieldValue]], nicht als `Any`.
  *
  * '''Was hier nicht steht:''' ViewState, DOM, Uploads und die History selbst (§14). Deshalb
  * steht die History auch nicht in [[EditorState]] -- sonst enthielte jeder Snapshot alle
  * vorherigen.
  */
final case class HistorySnapshot(
    document: Document,
    selection: Option[Selection],
    fields: Vector[FieldValue[?]] = Vector.empty
)

/** Eine Undo-Stufe: der Stand davor, der Stand danach.
  *
  * @param kind
  *   was zuletzt in dieser Gruppe passiert ist. Entscheidet zusammen mit [[at]] und [[marks]],
  *   ob die naechste Aenderung noch dazugehoert.
  * @param marks
  *   die Markierungen des betroffenen Textlaufs. §14 laesst zusammenhaengendes Tippen nur "mit
  *   gleicher Mark-Konfiguration" verschmelzen -- wer mitten im Wort fett einschaltet, hat
  *   zwei Absichten gehabt und soll sie einzeln zuruecknehmen koennen.
  * @param estimatedBytes
  *   siehe [[HistoryEntry.estimate]]. Eine Schaetzung, keine Heapmessung.
  */
final case class HistoryEntry(
    before: HistorySnapshot,
    after: HistorySnapshot,
    kind: EditKind,
    marks: MarkSet,
    at: Long,
    label: Option[String],
    estimatedBytes: Int
)

object HistoryEntry:

  /** Was dieser Eintrag ungefaehr festhaelt.
    *
    * ==Warum das nur eine Schaetzung sein kann==
    *
    * §14 nennt es ausdruecklich ein "geschaetztes Retained-Byte-Budget" und warnt davor, es als
    * Heapgroesse darzustellen. Der Grund ist das Structural Sharing: `before` und `after` teilen
    * fast alle Knoten, und `after` des einen Eintrags ist regelmaessig dasselbe Objekt wie
    * `before` des naechsten. Die Summe der Dokumentgroessen waere deshalb um Groessenordnungen
    * zu hoch.
    *
    * Gezaehlt wird stattdessen, was ein Eintrag '''zusaetzlich''' festhaelt: die Knoten, in
    * denen sich die beiden Staende unterscheiden. Auch das ist nur eine Naeherung -- die JVM
    * bzw. die JavaScript-Engine misst anders --, aber es waechst mit dem, womit der
    * Speicherbedarf tatsaechlich waechst.
    */
  def estimate(before: Document, after: Document): Int =
    val ids = before.ids.toSet ++ after.ids.toSet
    ids.foldLeft(0) { (total, id) =>
      (before.node(id), after.node(id)) match
        case (Some(left), Some(right)) if left == right => total
        case (left, right) => total + cost(left) + cost(right)
    }

  /** Grob, und bewusst grob: ein Objektkopf, plus das, was wirklich waechst. */
  private def cost(node: Option[EditorNode]): Int = node match
    case None                    => 0
    case Some(text: TextNode)    => 64 + 2 * text.text.length
    case Some(element: ElementNode) => 64 + 8 * element.children.length
    case Some(_)                 => 64

/** Die Staende einer History. Ein Wert -- damit die Regeln ohne Sitzung pruefbar sind.
  *
  * @param open
  *   ob die neueste Stufe noch verschmelzen darf. Ein Auswahlsprung schliesst sie (§14), ohne
  *   sie zu entfernen.
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
      case None => push(entry, limits)
      case Some(previous) =>
        val merged = previous.copy(
          after = entry.after,
          kind = entry.kind,
          marks = entry.marks,
          at = entry.at,
          estimatedBytes =
            HistoryEntry.estimate(previous.before.document, entry.after.document)
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
    * Zuerst die Anzahl, dann das Budget, und beide von vorn -- die aelteste Stufe ist die, die
    * am wenigsten fehlt.
    *
    * '''Die neueste Stufe bleibt immer.''' Ein einzelner Eintrag, der das Budget allein
    * sprengt, kaeme sonst nie in die History, und der Benutzer koennte ausgerechnet seine
    * letzte Aktion nicht zuruecknehmen. §14 nennt den Fall und verweist ihn woandershin: "ein
    * riesiger einzelner Import ist separat zu behandeln" -- und ein Import setzt die History
    * ohnehin zurueck.
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

/** Was zuletzt passiert ist -- so genau, wie es sich aus dem Commit ablesen laesst.
  *
  * §14 verlangt, dass Gruppengrenzen "explizit testbar" sind. Sie aus Textdifferenzen zu erraten
  * waere das Gegenteil; hier steht deshalb, welche Form eine Aenderung hatte, abgeleitet aus dem
  * [[ChangeSet]] und der Auswahl davor -- nicht aus einem Vergleich zweier Texte.
  */
enum EditKind:

  /** Text eingefuegt: `[from, to)` ist neu. */
  case Insert(node: NodeId, from: Int, to: Int)

  /** Rueckwaerts geloescht: `[at, at + deleted)` ist weg, der Caret steht jetzt auf `at`. */
  case DeleteBackward(node: NodeId, at: Int, deleted: Int)

  /** Vorwaerts geloescht: `[at, at + deleted)` ist weg, der Caret stand und steht auf `at`. */
  case DeleteForward(node: NodeId, at: Int, deleted: Int)

  /** Alles andere: Struktur, Formatierung, Bereichsersetzung, mehrere Knoten.
    *
    * §14 zaehlt sie als Grenzen auf, und deshalb sind sie hier '''ein''' Fall: keine zwei
    * Strukturaenderungen verschmelzen, und es gibt nichts, was sie unterscheiden muesste.
    */
  case Structural
