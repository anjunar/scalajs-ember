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
