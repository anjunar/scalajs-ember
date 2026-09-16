package ember.editor.table

import ember.editor.core.*
import ember.editor.history.HistoryCommands
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import TableTestKit.*

/** Editing in and around tables (X01). */
final class TableEditingSpec extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------------------
  // Inserting
  // ---------------------------------------------------------------------------------------

  "Inserting a table" should "put it after the block and a line behind it" in {
    val (session, _) = open(withTable = false)
    caret(session, "p0-t", 6)

    exec(session, TableCommands.InsertTable, TableSize(2, 3)) shouldBe CommandResult.Handled

    blocks(session) shouldBe "Vorher · [table] · "
    table(session) shouldBe "*|*|* / ||"
    caretText(session) should startWith("g")
  }

  it should "take the place of an empty paragraph and keep it as the line after" in {
    val (session, _) = open(withTable = false)
    caret(session, "p0-t", 6)
    exec(session, RichText.InsertParagraph)

    exec(session, TableCommands.InsertTable, TableSize(1, 1))

    blocks(session) shouldBe "Vorher · [table] · "
  }

  it should "be one undo step" in {
    val (session, _) = open(withTable = false)
    caret(session, "p0-t", 6)
    exec(session, TableCommands.InsertTable, TableSize(2, 2))

    exec(session, HistoryCommands.Undo)

    blocks(session) shouldBe "Vorher"
  }

  // ---------------------------------------------------------------------------------------
  // Text in cells
  // ---------------------------------------------------------------------------------------

  "Typing in a cell" should "stay in the cell" in {
    val (session, _) = open()
    caret(session, "c10-t", 1)

    exec(session, RichText.InsertText, "x")

    table(session) shouldBe "*A|*B / 1x|2"
  }

  "Enter in a cell" should "make a second paragraph in the same cell" in {
    val (session, _) = open()
    caret(session, "c10-t", 1)

    exec(session, RichText.InsertParagraph)
    exec(session, RichText.InsertText, "y")

    table(session) shouldBe "*A|*B / 1¶y|2"
  }

  "Backspace at the start of a cell" should "not pull the cell into its neighbour" in {
    val (session, _) = open()
    caret(session, "c11-t", 0)

    exec(session, RichText.DeleteBackward)

    table(session) shouldBe "*A|*B / 1|2"
    caretText(session) shouldBe "c11@0"
  }

  "Delete at the end of a cell" should "not pull the next cell in" in {
    val (session, _) = open()
    caret(session, "c10-t", 1)

    exec(session, RichText.DeleteForward)

    table(session) shouldBe "*A|*B / 1|2"
  }

  "Backspace at the start of the line after a table" should "not move the line into the last cell" in {
    val (session, _) = open()
    caret(session, "p1-t", 0)

    exec(session, RichText.DeleteBackward)

    table(session) shouldBe "*A|*B / 1|2"
    blocks(session) shouldBe "Vorher · [table] · Nachher"
  }

  "Delete in an empty line before a table" should "remove the line" in {
    val (session, _) = open()
    range(session, "p0-t" -> 0, "p0-t" -> 6)
    exec(session, RichText.DeleteBackward)

    exec(session, RichText.DeleteForward)

    blocks(session) shouldBe "[table] · Nachher"
  }

  // ---------------------------------------------------------------------------------------
  // Ranges
  // ---------------------------------------------------------------------------------------

  "A range from one cell into another" should "clear the cells it covers and keep the table" in {
    val (session, _) = open()
    range(session, "c00-t" -> 0, "c11-t" -> 1)

    exec(session, RichText.DeleteBackward)

    table(session) shouldBe "*|* / |"
    caretText(session) shouldBe "c00@0"
  }

  "A range from a paragraph into a cell" should "trim both ends and clear only what lies between" in {
    val (session, _) = open()
    range(session, "p0-t" -> 3, "c01-t" -> 1)

    exec(session, RichText.DeleteBackward)

    blocks(session) shouldBe "Vor · [table] · Nachher"
    table(session) shouldBe "*|* / 1|2"
  }

  "A range around a whole table" should "delete the table" in {
    val (session, _) = open()
    range(session, "p0-t" -> 0, "p1-t" -> 7)

    exec(session, RichText.DeleteBackward)

    blocks(session) shouldBe ""
  }

  "Typing over a range across cells" should "clear the cells and type into the first" in {
    val (session, _) = open()
    range(session, "c01-t" -> 0, "c10-t" -> 1)

    exec(session, RichText.InsertText, "z")

    table(session) shouldBe "*A|*z / |2"
  }

  // ---------------------------------------------------------------------------------------
  // Cell selection
  // ---------------------------------------------------------------------------------------

  "A cell selection" should "clear its cells on Delete" in {
    val (session, _) = open()
    caret(session, "c00-t", 0)

    exec(session, TableCommands.SelectCells, NodeId("c01") -> NodeId("c11")) shouldBe
      CommandResult.Handled
    caretText(session) shouldBe "cells(c01..c11)"

    exec(session, RichText.DeleteForward)

    table(session) shouldBe "*A|* / 1|"
    caretText(session) shouldBe "c01@0"
  }

  it should "be replaced by what is typed" in {
    val (session, _) = open()
    exec(session, TableCommands.SelectCells, NodeId("c00") -> NodeId("c10"))

    exec(session, RichText.InsertText, "q")

    table(session) shouldBe "*q|*B / |2"
  }

  it should "collapse to a caret" in {
    val (session, _) = open()
    exec(session, TableCommands.SelectCells, NodeId("c00") -> NodeId("c11"))

    exec(session, TableCommands.CollapseCellSelection) shouldBe CommandResult.Handled

    caretText(session) shouldBe "c11@1"
  }

  it should "refuse cells of two different places" in {
    val (session, _) = open()

    exec(
      session,
      TableCommands.SelectCells,
      NodeId("c00") -> NodeId("p0")
    ) shouldBe CommandResult.Pass
  }

  // ---------------------------------------------------------------------------------------
  // Rows and columns
  // ---------------------------------------------------------------------------------------

  "Inserting a row" should "add one below and put the caret into it" in {
    val (session, _) = open()
    caret(session, "c11-t", 0)

    exec(session, TableCommands.InsertRow, RowPosition.Below)

    table(session) shouldBe "*A|*B / 1|2 / |"
    Tables
      .contextAt(session.document, session.selection)
      .map(context => (context.row, context.column)) shouldBe
      Some((2, 1))
  }

  it should "add one above, and the first row stays the header" in {
    val (session, _) = open()
    caret(session, "c00-t", 0)

    exec(session, TableCommands.InsertRow, RowPosition.Above)

    table(session) shouldBe "*|* / A|B / 1|2"
  }

  "Inserting a column" should "add a cell to every row" in {
    val (session, _) = open()
    caret(session, "c00-t", 0)

    exec(session, TableCommands.InsertColumn, ColumnPosition.After)

    table(session) shouldBe "*A|*|*B / 1||2"
  }

  "Deleting a row" should "remove it and keep the caret in the table" in {
    val (session, _) = open()
    caret(session, "c11-t", 0)

    exec(session, TableCommands.DeleteRow)

    table(session) shouldBe "*A|*B"
    caretText(session) shouldBe "c01@0"
  }

  "Deleting a column" should "remove it from every row" in {
    val (session, _) = open()
    caret(session, "c01-t", 0)

    exec(session, TableCommands.DeleteColumn)

    table(session) shouldBe "*A / 1"
  }

  "Deleting every row" should "delete the table" in {
    val (session, _) = open()
    exec(session, TableCommands.SelectCells, NodeId("c00") -> NodeId("c10"))

    exec(session, TableCommands.DeleteRow)

    blocks(session) shouldBe "Vorher · Nachher"
    caretText(session) shouldBe "-@0"
  }

  "Deleting a table" should "leave a paragraph when nothing else is left" in {
    val (session, _) = open(withTable = false)
    caret(session, "p0-t", 0)
    exec(session, TableCommands.InsertTable, TableSize(1, 1))
    val document = session.document
    session.update { tx =>
      tx.remove(NodeId("p0")): Unit
      document.childrenOf(document.rootId).drop(2).foreach(id => tx.remove(id): Unit)
      document.inDocumentOrder.collectFirst { case cell: TableCellNode => cell.id }.foreach {
        cell =>
          tx.select(RangeSelection.caret(Tables.startOf(tx.document, cell))): Unit
      }
    }

    exec(session, TableCommands.DeleteTable)

    blocks(session) shouldBe ""
    session.document.childrenOf(session.document.rootId) should have length 1
  }

  "Undoing a row insertion" should "restore the table exactly" in {
    val (session, _) = open()
    caret(session, "c10-t", 0)
    val before = session.document

    exec(session, TableCommands.InsertRow, RowPosition.Below)
    exec(session, HistoryCommands.Undo)

    session.document.inDocumentOrder.toVector shouldBe before.inDocumentOrder.toVector
  }

  // ---------------------------------------------------------------------------------------
  // Navigation
  // ---------------------------------------------------------------------------------------

  "Tab" should "move to the end of the next cell" in {
    val (session, _) = open()
    caret(session, "c00-t", 0)

    exec(session, TableCommands.MoveToCell, CellDirection.Next)

    caretText(session) shouldBe "c01@1"
  }

  it should "add a row after the last cell" in {
    val (session, _) = open()
    caret(session, "c11-t", 1)

    exec(session, TableCommands.MoveToCell, CellDirection.Next)

    table(session) shouldBe "*A|*B / 1|2 / |"
    Tables
      .contextAt(session.document, session.selection)
      .map(context => (context.row, context.column)) shouldBe
      Some((2, 0))
  }

  it should "pass outside a table, so that lists and code keep Tab" in {
    val (session, _) = open()
    caret(session, "p0-t", 0)

    exec(session, TableCommands.MoveToCell, CellDirection.Next) shouldBe CommandResult.Pass
  }

  "Shift+Tab" should "move back, and stay in the first cell" in {
    val (session, _) = open()
    caret(session, "c10-t", 0)

    exec(session, TableCommands.MoveToCell, CellDirection.Previous)
    caretText(session) shouldBe "c01@1"

    caret(session, "c00-t", 0)
    exec(session, TableCommands.MoveToCell, CellDirection.Previous)
    caretText(session) shouldBe "c00@0"
  }
}
