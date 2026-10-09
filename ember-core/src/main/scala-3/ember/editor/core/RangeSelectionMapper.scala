package ember.editor.core

/** Eingebauter Mapper fuer [[RangeSelection]]. */
object RangeSelectionMapper extends SelectionMapper[RangeSelection]:

  def project(selection: Selection): Option[RangeSelection] = selection match
    case range: RangeSelection => Some(range)
    case _                     => None

  /** §11: "Anchor und Focus werden unabhaengig gemappt."
    *
    * Unabhaengig heisst wirklich unabhaengig -- es gibt keine gemeinsame Korrektur, die beide ans
    * selbe Ende zoege. Faellt einer der beiden auf eine im neuen Dokument nicht darstellbare
    * Position, gibt es keine Auswahl mehr; eine halb gueltige waere schlimmer als keine.
    */
  def map(
      selection: RangeSelection,
      mapping: PositionMapping,
      after: DocumentRead
  ): Option[Selection] =
    val mapped =
      RangeSelection(mapping.map(selection.anchor).point, mapping.map(selection.focus).point)
    Option.when(validate(mapped, after).isEmpty)(mapped)

  def validate(selection: RangeSelection, document: DocumentRead): Vector[Violation] =
    Vector(selection.anchor, selection.focus).flatMap(_.validateIn(document))
