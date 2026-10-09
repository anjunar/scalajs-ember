package ember.editor.table

import ember.editor.core.*

/** The registered mapper for [[TableSelection]] (§11: "Jede Selection-Art benoetigt einen
  * registrierten Mapper/Validator").
  *
  * ==What survives a change==
  *
  * Cells keep their identity through every structural edit a table has -- inserting a row moves
  * nothing, deleting a column removes cells but never renames the others. So a selection whose two
  * corners still exist in the same table is still the same selection. When a corner is gone, the
  * selection falls back to a caret in the corner that is left, and only when both are gone is there
  * nothing to fall back to.
  */
object TableSelectionMapper extends SelectionMapper[TableSelection]:

  def project(selection: Selection): Option[TableSelection] = selection match
    case value: TableSelection => Some(value)
    case _                     => None

  def map(
      selection: TableSelection,
      mapping: PositionMapping,
      after: DocumentRead
  ): Option[Selection] =
    if validate(selection, after).isEmpty then Some(selection)
    else
      Vector(selection.focus, selection.anchor)
        .find(cell => after.contains(cell) && Tables.tableOfCell(after, cell).isDefined)
        .map(cell => RangeSelection.caret(Tables.startOf(after, cell)))

  def validate(selection: TableSelection, document: DocumentRead): Vector[Violation] =
    val tableProblem =
      Option.when(!document.node(selection.table).exists(_.isInstanceOf[TableNode]))(
        Violation.NodeRejected(
          selection.table,
          "The selected table does not exist.",
          DiagnosticPath.node(selection.table.value)
        )
      )
    val cellProblems = Vector(selection.anchor, selection.focus).distinct.flatMap { cell =>
      Option.when(Tables.tableOfCell(document, cell) != Some(selection.table))(
        Violation.NodeRejected(
          cell,
          "A selected cell is not a cell of the selected table.",
          DiagnosticPath.node(cell.value)
        )
      )
    }
    tableProblem.toVector ++ cellProblems
