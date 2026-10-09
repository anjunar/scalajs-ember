package ember.editor.core

import scala.collection.immutable.TreeMap

/** Normalisierte Menge von Markierungen eines Textlaufs.
  *
  * Hoechstens eine Mark je [[MarkId]]. Die Reihenfolge ist immer nach `markId` sortiert, nie die
  * Einfuegereihenfolge -- daran haengt mehr als Kosmetik:
  *
  *   - '''Gleichheit.''' `{Strong, Emphasis}` und `{Emphasis, Strong}` muessen derselbe Wert sein.
  *     §8.2 laesst benachbarte Textlaeufe genau dann wieder zusammenwachsen, wenn ihre
  *     normalisierten Marks gleich sind; eine ordnungsabhaengige Gleichheit wuerde wiederholtes
  *     Formatieren und Entformatieren zu wachsender Fragmentierung fuehren lassen.
  *   - '''Determinismus.''' JSON- und Markdown-Export muessen bei gleichem Dokument byteweise
  *     gleich sein, sonst sind Roundtrip-Fixtures wertlos.
  *
  * Widersprueche und gegenseitiger Ausschluss -- etwa dass InlineCode andere Marks verdraengt --
  * bestimmt das Profil des jeweiligen Moduls, nicht der Kern (§8.2). Hier gilt beim Hinzufuegen
  * derselben `markId` schlicht: der neue Wert ersetzt den alten.
  */
final class MarkSet private (private val byId: TreeMap[MarkId, TextMark]):

  /** Die Marks in stabiler Ordnung nach [[MarkId]]. */
  def marks: Vector[TextMark] = byId.valuesIterator.toVector

  def markIds: Vector[MarkId] = byId.keysIterator.toVector

  def isEmpty: Boolean  = byId.isEmpty
  def nonEmpty: Boolean = byId.nonEmpty
  def size: Int         = byId.size

  def contains(markId: MarkId): Boolean = byId.contains(markId)

  def get(markId: MarkId): Option[TextMark] = byId.get(markId)

  /** Fuegt hinzu oder ersetzt die Mark gleicher [[MarkId]]. */
  def +(mark: TextMark): MarkSet = new MarkSet(byId.updated(mark.markId, mark))

  /** Entfernt die Mark dieser Art, falls vorhanden. Sonst unveraendert. */
  def -(markId: MarkId): MarkSet =
    if byId.contains(markId) then new MarkSet(byId.removed(markId)) else this

  /** Vereinigung; bei gleicher [[MarkId]] gewinnt `other`. */
  def union(other: MarkSet): MarkSet =
    if other.isEmpty then this
    else if isEmpty then other
    else new MarkSet(byId ++ other.byId)

  override def equals(other: Any): Boolean = other match
    case that: MarkSet => byId == that.byId
    case _             => false

  override def hashCode(): Int = byId.hashCode()

  override def toString: String =
    if isEmpty then "MarkSet()"
    else byId.keysIterator.map(_.value).mkString("MarkSet(", ", ", ")")

object MarkSet:

  val empty: MarkSet = new MarkSet(TreeMap.empty(using Ordering[MarkId]))

  /** Baut eine Menge; bei doppelter [[MarkId]] gewinnt der spaetere Eintrag. */
  def of(marks: TextMark*): MarkSet = from(marks)

  def from(marks: IterableOnce[TextMark]): MarkSet =
    marks.iterator.foldLeft(empty)(_ + _)
