package ember.editor.core

/** Ein Textbereich zwischen zwei Punkten.
  *
  * `anchor` ist der zuerst gesetzte Punkt, `focus` der bewegliche. Die beiden werden '''niemals'''
  * zugunsten sortierter Endpunkte vertauscht (§11): eine rueckwaerts gezogene Auswahl bleibt
  * rueckwaerts, sonst springt der Cursor beim naechsten Tastendruck ans falsche Ende. Welcher Punkt
  * im Dokument frueher steht, beantwortet [[direction]] anhand der tatsaechlichen Baumordnung --
  * nie anhand eines lexikographischen ID-Vergleichs.
  */
final case class RangeSelection(anchor: Point, focus: Point) extends Selection:

  /** Ein Caret ist eine kollabierte Range, kein eigener Typ. */
  def isCollapsed: Boolean = anchor == focus

  def direction(document: DocumentRead): SelectionDirection =
    math.signum(document.comparePoints(anchor, focus)) match
      case 0          => SelectionDirection.Collapsed
      case n if n < 0 => SelectionDirection.Forward
      case _          => SelectionDirection.Backward

  /** Die Endpunkte in Dokumentordnung, ohne `anchor`/`focus` zu vertauschen. */
  def ordered(document: DocumentRead): (Point, Point) =
    if document.comparePoints(anchor, focus) <= 0 then (anchor, focus) else (focus, anchor)

object RangeSelection:
  def caret(point: Point): RangeSelection = RangeSelection(point, point)
