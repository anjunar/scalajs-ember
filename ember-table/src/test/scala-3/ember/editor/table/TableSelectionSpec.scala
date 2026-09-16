package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import TableTestKit.*

/** The rectangle, its mapper and its validator (X01, §11). */
final class TableSelectionSpec extends AnyFlatSpec with Matchers {

  "A cell selection" should "be the rectangle its corners span, row by row" in {
    val (session, _) = open()
    caret(session, "c11-t", 0)
    exec(session, TableCommands.InsertColumn, ColumnPosition.After)

    val selection = TableSelection(NodeId("t"), NodeId("c11"), NodeId("c00"))

    selection.cells(session.document) shouldBe Vector(
      NodeId("c00"),
      NodeId("c01"),
      NodeId("c10"),
      NodeId("c11")
    )
  }

  it should "survive a text edit in one of its cells" in {
    val (session, _) = open()
    exec(session, TableCommands.SelectCells, NodeId("c00") -> NodeId("c11"))

    session.update(_.spliceText(NodeId("c10-t"), 0, 0, "x"): Unit)

    session.selection shouldBe Some(TableSelection(NodeId("t"), NodeId("c00"), NodeId("c11")))
  }

  it should "grow with a row inserted inside it" in {
    val (session, _) = open()
    exec(session, TableCommands.SelectCells, NodeId("c00") -> NodeId("c11"))

    session.update { tx =>
      val ids          = NodeIdGenerator.sequential("x").nextBatch(7, tx.document.contains)
      val (row, below) =
        Tables.emptyRow(ids, TableNode(NodeId("t"), Vector.empty), 2, header = false)
      tx.insert(NodeId("t"), 1, row, below): Unit
    }

    session.selection.collect { case cells: TableSelection =>
      cells.cells(session.document).length
    } shouldBe
      Some(6)
  }

  it should "fall back to a caret in the corner that is left" in {
    val (session, _) = open()
    exec(session, TableCommands.SelectCells, NodeId("c00") -> NodeId("c11"))

    session.update(_.remove(NodeId("r1")): Unit)

    caretText(session) shouldBe "c00@0"
  }

  it should "disappear with its table" in {
    val (session, _) = open()
    exec(session, TableCommands.SelectCells, NodeId("c00") -> NodeId("c11"))

    session.update(_.remove(NodeId("t")): Unit)

    session.selection.collect { case cells: TableSelection => cells } shouldBe None
  }

  "The validator" should "refuse a cell of another table and a node that is no cell" in {
    val (session, _) = open()

    TableSelectionMapper.validate(
      TableSelection(NodeId("t"), NodeId("c00"), NodeId("p0")),
      session.document
    ) should
      have length 1
    TableSelectionMapper.validate(
      TableSelection(NodeId("p0"), NodeId("c00"), NodeId("c01")),
      session.document
    ) should
      not be empty
    TableSelectionMapper.validate(
      TableSelection(NodeId("t"), NodeId("c00"), NodeId("c11")),
      session.document
    ) shouldBe
      empty
  }

  "A selection without its table module" should "be refused by the core" in {
    // X01: "Kein Core-Spezialfall fuer TableSelection" -- a session without the extension has no
    // mapper, and the core says so rather than guessing.
    val generator = NodeIdGenerator.sequential("g")
    val resolved  =
      ExtensionResolver.resolve(Vector(RichText(generator))).getOrElse(fail("extensions"))
    val session = EditorSession
      .create(
        RichText.emptyDocument(resolved.schema, generator).getOrElse(fail("document")),
        resolved,
        resolved.sessionConfig()
      )
      .getOrElse(fail("session"))

    session.update(_.select(TableSelection(NodeId("t"), NodeId("a"), NodeId("b"))): Unit) shouldBe
      a[Left[?, ?]]
  }
}
