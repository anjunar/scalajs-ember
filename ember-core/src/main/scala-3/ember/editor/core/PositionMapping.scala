package ember.editor.core

/** Das Ergebnis, einen Punkt durch eine Aenderung hindurch abzubilden. */
enum MappedPoint:

  /** Der Punkt zeigt weiter auf denselben Inhalt. */
  case Preserved(point: Point)

  /** Der Inhalt ist verschwunden; `point` ist die erhaltene Grenze, auf die er zurueckfaellt.
    *
    * Die Unterscheidung ist kein Luxus. Ein Caret darf auf die Grenze zurueckfallen -- der Cursor
    * muss irgendwo stehen. Ein Upload-Bookmark darf das '''nicht''': waere das Zielbild geloescht
    * worden, wuerde eine Einfuegung an der Grenze das Ergebnis an einer beliebigen anderen Stelle
    * einsetzen (§11, §20). Deshalb liefert [[Bookmark.resolve]] hier `Expired`, waehrend die
    * Selection den Ersatzpunkt uebernimmt.
    */
  case Displaced(point: Point)

object MappedPoint:

  extension (mapped: MappedPoint)

    def point: Point = mapped match
      case MappedPoint.Preserved(point) => point
      case MappedPoint.Displaced(point) => point

    def isPreserved: Boolean = mapped match
      case MappedPoint.Preserved(_) => true
      case MappedPoint.Displaced(_) => false

    /** Bildet weiter ab und behaelt einmal verlorenen Inhalt als verloren.
      *
      * Verschiebung ist ansteckend: was in Schritt eins verschwunden ist, kann in Schritt zwei
      * nicht wieder auftauchen. Ohne diese Regel wuerde eine Kette aus Loeschen und Einfuegen einen
      * Bookmark faelschlich als gueltig melden.
      */
    def flatMap(next: Point => MappedPoint): MappedPoint = mapped match
      case MappedPoint.Preserved(point) => next(point)
      case MappedPoint.Displaced(point) =>
        MappedPoint.Displaced(next(point).point)

/** Bildet Punkte eines Dokumentstands auf den nachfolgenden Stand ab.
  *
  * Jede Operation liefert genau eine solche Abbildung, und sie sind komponierbar (§11). Damit
  * laesst sich ein Punkt ueber eine beliebige Folge von Aenderungen nachfuehren, ohne ihn nach
  * jedem Schritt neu suchen zu muessen -- Selection, Bookmarks und spaeter die History haengen
  * daran.
  *
  * `removedNodes` gehoert dazu, weil eine reine Punktabbildung fuer [[NodeSelection]] nicht reicht:
  * dort muessen geloeschte IDs verschwinden, verschobene aber erhalten bleiben, und das laesst sich
  * an einem abgebildeten Punkt nicht ablesen.
  */
final class PositionMapping private (
    private val step: Point => MappedPoint,
    val removedNodes: Set[NodeId]
):

  def map(point: Point): MappedPoint = step(point)

  /** Erst diese Abbildung, dann `next`. Reihenfolge ist fachlich relevant (P03, Risiken). */
  def andThen(next: PositionMapping): PositionMapping =
    if this.isIdentity then next
    else if next.isIdentity then this
    else
      new PositionMapping(
        point => map(point).flatMap(next.map),
        removedNodes ++ next.removedNodes
      )

  private def isIdentity: Boolean = this eq PositionMapping.identity

object PositionMapping:

  /** Aendert nichts. Neutrales Element der Komposition. */
  val identity: PositionMapping =
    new PositionMapping(MappedPoint.Preserved.apply, Set.empty)

  def of(removedNodes: Set[NodeId] = Set.empty)(step: Point => MappedPoint): PositionMapping =
    new PositionMapping(step, removedNodes)

  /** Verkettet in Reihenfolge: das erste Element wird zuerst angewandt. */
  def composeAll(mappings: Seq[PositionMapping]): PositionMapping =
    mappings.foldLeft(identity)(_ andThen _)

  // -----------------------------------------------------------------------------------------
  // Bausteine. Die Regeln folgen der Tabelle in Architektur §11.
  // -----------------------------------------------------------------------------------------

  /** Einfuegen von `length` Zeichen an `at` in `node`.
    *
    * §11: "Punkte davor bleiben; Punkte danach wandern um die UTF-16-Laenge; genau `at` entscheidet
    * die Affinitaet."
    */
  private[core] def insertText(node: NodeId, at: Int, length: Int)(point: Point): Point =
    point match
      case Point.Text(owner, offset, affinity) if owner == node =>
        if offset < at then point
        else if offset > at then Point.Text(node, offset + length, affinity)
        else
          affinity match
            case Affinity.Before => point
            case Affinity.After  => Point.Text(node, at + length, affinity)
      case _ => point

  /** Loeschen von `[from, until)` in `node`.
    *
    * §11: "Punkte im geloeschten Bereich fallen auf `from`. Punkte dahinter verlieren
    * `until - from`."
    */
  private[core] def deleteText(node: NodeId, from: Int, until: Int)(point: Point): MappedPoint =
    point match
      case Point.Text(owner, offset, affinity) if owner == node =>
        if offset <= from then MappedPoint.Preserved(point)
        else if offset >= until then
          MappedPoint.Preserved(Point.Text(node, offset - (until - from), affinity))
        else MappedPoint.Displaced(Point.Text(node, from, affinity))
      case _ => MappedPoint.Preserved(point)

  /** Einfuegen eines Kindes an Position `at` unter `parent`. */
  private[core] def insertChild(parent: NodeId, at: Int)(point: Point): Point =
    point match
      case Point.Children(owner, offset, affinity) if owner == parent =>
        if offset < at then point
        else if offset > at then Point.Children(parent, offset + 1, affinity)
        else
          affinity match
            case Affinity.Before => point
            case Affinity.After  => Point.Children(parent, at + 1, affinity)
      case _ => point

  /** Entfernen des Kindes an Position `at` unter `parent`.
    *
    * Bildet nur die Verschiebung der Geschwister ab. Punkte '''innerhalb''' des entfernten
    * Teilbaums behandelt die Operation selbst -- sie kennt dessen Umfang, diese Regel nicht.
    */
  private[core] def removeChild(parent: NodeId, at: Int)(point: Point): Point =
    point match
      case Point.Children(owner, offset, affinity) if owner == parent && offset > at =>
        Point.Children(parent, offset - 1, affinity)
      case _ => point
