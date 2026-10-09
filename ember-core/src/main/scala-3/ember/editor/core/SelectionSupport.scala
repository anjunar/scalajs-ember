package ember.editor.core

/** Registry der bekannten Auswahlarten.
  *
  * Getrennt von [[Schema]], weil sie unabhaengig waechst: ein Modul kann Node-Arten beitragen, ohne
  * eine neue Auswahlart zu brauchen, und X01 braucht beides.
  */
final class SelectionSupport private (val mappers: Vector[SelectionMapper[?]]):

  def mapperFor(selection: Selection): Option[SelectionMapper[?]] =
    mappers.find(_.project(selection).isDefined)

  /** Fuehrt eine Auswahl beliebiger Art nach.
    *
    * Eine Auswahl ohne registrierten Mapper geht verloren statt unveraendert weitergereicht zu
    * werden. Unveraendert weiterreichen hiesse, sie zeige noch auf denselben Inhalt -- und genau
    * das kann niemand behaupten, der die Art nicht kennt.
    */
  def map(selection: Selection, mapping: PositionMapping, after: DocumentRead): Option[Selection] =
    mapperFor(selection).flatMap(mapWith(_, selection, mapping, after))

  def validate(selection: Selection, document: DocumentRead): Vector[Violation] =
    mapperFor(selection) match
      case Some(mapper) => validateWith(mapper, selection, document)
      case None         =>
        Vector(
          Violation.NodeRejected(
            document.rootId,
            s"Keine registrierte Auswahlart fuer ${selection.getClass.getSimpleName}.",
            DiagnosticPath.Root
          )
        )

  def extendedWith(additional: SelectionMapper[?]*): SelectionSupport =
    new SelectionSupport(mappers ++ additional)

  private def mapWith[S <: Selection](
      mapper: SelectionMapper[S],
      selection: Selection,
      mapping: PositionMapping,
      after: DocumentRead
  ): Option[Selection] =
    mapper.project(selection).flatMap(mapper.map(_, mapping, after))

  private def validateWith[S <: Selection](
      mapper: SelectionMapper[S],
      selection: Selection,
      document: DocumentRead
  ): Vector[Violation] =
    mapper.project(selection).fold(Vector.empty)(mapper.validate(_, document))

object SelectionSupport:

  def of(mappers: SelectionMapper[?]*): SelectionSupport = new SelectionSupport(mappers.toVector)

  /** Range und Node sind eingebaut (§11). */
  val core: SelectionSupport = of(RangeSelectionMapper, NodeSelectionMapper)
