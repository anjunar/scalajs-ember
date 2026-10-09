package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import TableTestKit.*

/** The shape a table keeps, whoever changes it (X01). */
final class TableStructureSpec extends AnyFlatSpec with Matchers {

  private def edit(session: EditorSession)(body: Transaction => Unit): Unit =
    session.update(body) match
      case Right(_)    => ()
      case Left(error) => fail(error.render)

  "A table" should "stay a rectangle when a row grows" in {
    val (session, _) = open()

    edit(session) { tx =>
      tx.insert(
        NodeId("r0"),
        2,
        TableCellNode(NodeId("c02"), Vector(NodeId("c02-p")), header = true),
        Vector(
          ParagraphNode(NodeId("c02-p"), Vector(NodeId("c02-t"))),
          TextNode(NodeId("c02-t"), "C")
        )
      ): Unit
    }

    table(session) shouldBe "*A|*B|*C / 1|2|"
    session.document.node(NodeId("t")).collect { case value: TableNode =>
      value.alignments.length
    } shouldBe
      Some(3)
  }

  it should "give an emptied cell a paragraph again" in {
    val (session, _) = open()

    edit(session)(_.remove(NodeId("c00-p")): Unit)

    val cell =
      session.document.node(NodeId("c00")).collect { case value: TableCellNode => value }.get
    cell.children should have length 1
    session.document.node(cell.children.head) shouldBe a[Some[?]]
    session.document.node(cell.children.head).get shouldBe a[ParagraphNode]
  }

  it should "put inline content that lands directly in a cell into a paragraph" in {
    val (session, _) = open()

    edit(session)(_.insert(NodeId("c11"), 1, TextNode(NodeId("loose"), "x")): Unit)

    val children = session.document.childrenOf(NodeId("c11")).flatMap(session.document.node)
    children.forall(_.isInstanceOf[ParagraphNode]) shouldBe true
    table(session) shouldBe "*A|*B / 1|2¶x"
  }

  it should "wrap a block moved into it in a row and a cell" in {
    val (session, _) = open()

    edit(session)(_.move(NodeId("p0"), NodeId("t"), 2): Unit)

    table(session) shouldBe "*A|*B / 1|2 / Vorher|"
  }

  it should "lose a row whose cells are all gone, and disappear with its last row" in {
    val (session, _) = open()

    edit(session) { tx =>
      tx.remove(NodeId("c10")): Unit
      tx.remove(NodeId("c11")): Unit
    }
    table(session) shouldBe "*A|*B"

    edit(session)(tx => tx.remove(NodeId("r0")): Unit)
    blocks(session) shouldBe "Vorher · Nachher"
  }

  "The header row" should "follow the table's flag" in {
    val (session, _) = open()
    caret(session, "c10-t", 0)

    exec(session, TableCommands.ToggleHeaderRow) shouldBe CommandResult.Handled
    table(session) shouldBe "A|B / 1|2"

    exec(session, TableCommands.ToggleHeaderRow)
    table(session) shouldBe "*A|*B / 1|2"
  }

  it should "move with the first row" in {
    val (session, _) = open()

    edit(session)(_.move(NodeId("r1"), NodeId("t"), 0): Unit)

    table(session) shouldBe "*1|*2 / A|B"
  }

  "A column's alignment" should "reach every cell of the column" in {
    val (session, _) = open()
    caret(session, "c11-t", 0)

    exec(session, TableCommands.SetColumnAlignment, ColumnAlignment.Center)

    val alignments = Vector("c01", "c11").flatMap(id =>
      session.document.node(NodeId(id)).collect { case cell: TableCellNode => cell.alignment }
    )
    alignments shouldBe Vector(ColumnAlignment.Center, ColumnAlignment.Center)
    session.document.node(NodeId("c00")).collect { case cell: TableCellNode =>
      cell.alignment
    } shouldBe
      Some(ColumnAlignment.Default)
  }

  "A ragged table from an import" should "be accepted as it is" in {
    // Validation is lenient on purpose: pasted HTML is ragged, and the normalisation squares it the
    // moment it is edited.
    val generator = NodeIdGenerator.sequential("g")
    val resolved  = ExtensionResolver
      .resolve(Vector(RichText(generator), TableExtension(generator)))
      .getOrElse(fail("extensions"))

    Document.build(
      resolved.schema,
      root,
      Vector(
        RootNode(root, Vector(NodeId("t"))),
        TableNode(NodeId("t"), Vector(NodeId("r0"), NodeId("r1"))),
        TableRowNode(NodeId("r0"), Vector(NodeId("a"), NodeId("b"))),
        TableRowNode(NodeId("r1"), Vector(NodeId("c"))),
        TableCellNode(NodeId("a"), Vector.empty),
        TableCellNode(NodeId("b"), Vector.empty),
        TableCellNode(NodeId("c"), Vector.empty)
      )
    ) shouldBe a[Right[?, ?]]
  }
}
