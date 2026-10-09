package ember.editor.history

import ember.editor.core.*

/** Eine Undo-Stufe: der Stand davor, der Stand danach.
  *
  * @param kind
  *   was zuletzt in dieser Gruppe passiert ist. Entscheidet zusammen mit [[at]] und [[marks]], ob
  *   die naechste Aenderung noch dazugehoert.
  * @param marks
  *   die Markierungen des betroffenen Textlaufs. §14 laesst zusammenhaengendes Tippen nur "mit
  *   gleicher Mark-Konfiguration" verschmelzen -- wer mitten im Wort fett einschaltet, hat zwei
  *   Absichten gehabt und soll sie einzeln zuruecknehmen koennen.
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
    estimatedBytes: Int,
    // None supports caller-created entries and restore-plus-edit paths without a usable delta.
    changedNodes: Option[Set[NodeId]] = None
)

object HistoryEntry:

  /** Was dieser Eintrag ungefaehr festhaelt.
    *
    * ==Warum das nur eine Schaetzung sein kann==
    *
    * §14 nennt es ausdruecklich ein "geschaetztes Retained-Byte-Budget" und warnt davor, es als
    * Heapgroesse darzustellen. Der Grund ist das Structural Sharing: `before` und `after` teilen
    * fast alle Knoten, und `after` des einen Eintrags ist regelmaessig dasselbe Objekt wie `before`
    * des naechsten. Die Summe der Dokumentgroessen waere deshalb um Groessenordnungen zu hoch.
    *
    * Gezaehlt wird stattdessen, was ein Eintrag '''zusaetzlich''' festhaelt: die Knoten, in denen
    * sich die beiden Staende unterscheiden. Auch das ist nur eine Naeherung -- die JVM bzw. die
    * JavaScript-Engine misst anders --, aber es waechst mit dem, womit der Speicherbedarf
    * tatsaechlich waechst.
    */
  def estimate(before: Document, after: Document): Int =
    estimate(before, after, before.ids.toSet ++ after.ids.toSet)

  /** Commit deltas bound the work independently of the document's total node count. */
  def estimate(before: Document, after: Document, ids: Set[NodeId]): Int =
    ids.foldLeft(0) { (total, id) =>
      (before.node(id), after.node(id)) match
        case (Some(left), Some(right)) if left == right => total
        case (left, right)                              => total + cost(left) + cost(right)
    }

  /** Grob, und bewusst grob: ein Objektkopf, plus das, was wirklich waechst. */
  private def cost(node: Option[EditorNode]): Int = node match
    case None                       => 0
    case Some(text: TextNode)       => 64 + 2 * text.text.length
    case Some(element: ElementNode) => 64 + 8 * element.children.length
    case Some(_)                    => 64
