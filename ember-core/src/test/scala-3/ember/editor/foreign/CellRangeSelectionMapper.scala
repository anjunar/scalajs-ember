package ember.editor.foreign

import ember.editor.core.*
object CellRangeSelectionMapper extends SelectionMapper[CellRangeSelection]:

  def project(selection: Selection): Option[CellRangeSelection] = selection match
    case cells: CellRangeSelection => Some(cells)
    case _                         => None

  /** Ueberlebt nur, solange beide Ecken existieren. */
  def map(
      selection: CellRangeSelection,
      mapping: PositionMapping,
      after: DocumentRead
  ): Option[Selection] =
    Option.when(after.contains(selection.from) && after.contains(selection.to))(selection)

  def validate(selection: CellRangeSelection, document: DocumentRead): Vector[Violation] =
    Vector(selection.from, selection.to)
      .filterNot(document.contains)
      .map(node =>
        Violation.NodeRejected(node, "Zellecke fehlt im Dokument.", DiagnosticPath.node(node.value))
      )
