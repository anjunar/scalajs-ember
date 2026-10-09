package ember.editor.standard

import ember.editor.code.CodeExtension
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.{ImageExtension, MediaUrlPolicy}
import ember.editor.json.*
import ember.editor.link.{LinkExtension, LinkUrlPolicy}
import ember.editor.list.ListExtension
import ember.editor.markdown.*
import ember.editor.richtext.*
import ember.editor.table.*
import ember.editor.ui.*
import ui.core.render.SsrCursor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tables as HTML, JSON and Markdown, and out of pasted HTML (X01). */
final class TableFormatSpec extends AnyFlatSpec with Matchers {

  private val generator = NodeIdGenerator.sequential("g")
  private val resolved  = ExtensionResolver
    .resolve(
      Vector(
        RichText(generator),
        ListExtension(generator),
        LinkExtension(generator),
        CodeExtension(generator),
        ImageExtension(generator, MediaUrlPolicy.default),
        TableExtension(generator)
      )
    )
    .getOrElse(fail("extensions"))

  private val root = NodeId("root")

  private val json     = StandardJsonSupport.everything() ++ TableSupport.json
  private val markdown = MarkdownSupports.everything() ++ TableSupport.markdownRules
  private val imports  = StandardHtmlImport
    .everything(LinkUrlPolicy.default, MediaUrlPolicy.default)
    .withRules(TableSupport.htmlImport*)

  private def cell(
      id: String,
      text: String,
      header: Boolean = false,
      alignment: ColumnAlignment = ColumnAlignment.Default
  ) =
    Vector(
      TableCellNode(NodeId(id), Vector(NodeId(s"$id-p")), header, alignment),
      ParagraphNode(NodeId(s"$id-p"), Vector(NodeId(s"$id-t"))),
      TextNode(NodeId(s"$id-t"), text)
    )

  /** `| A | B |` over `| 1 | 2 |`, the second column centred. */
  private val document: Document = Document.unsafe(
    resolved.schema,
    root,
    Vector(
      RootNode(root, Vector(NodeId("t"))),
      TableNode(
        NodeId("t"),
        Vector(NodeId("r0"), NodeId("r1")),
        header = true,
        Vector(ColumnAlignment.Default, ColumnAlignment.Center)
      ),
      TableRowNode(NodeId("r0"), Vector(NodeId("a"), NodeId("b"))),
      TableRowNode(NodeId("r1"), Vector(NodeId("c"), NodeId("d")))
    ) ++ cell("a", "A", header = true) ++ cell("b", "B", header = true, ColumnAlignment.Center) ++
      cell("c", "1") ++ cell("d", "2", alignment = ColumnAlignment.Center)
  )

  private def shape(document: Document): String =
    document.inDocumentOrder.collectFirst { case table: TableNode => table } match
      case None        => "-"
      case Some(table) =>
        val grid = Tables.grid(document, table.id).get
        val rows = grid.cells.map(_.map { value =>
          val text = document
            .subtreeOf(value.id)
            .flatMap(document.node)
            .collect { case run: TextNode => run.text }
            .mkString
          (if value.header then "*" else "") + text + (value.alignment match
            case ColumnAlignment.Default => ""
            case other                   => s"(${other.toString.head.toLower})")
        }.mkString("|"))
        rows.mkString(" / ")

  // ---------------------------------------------------------------------------------------
  // HTML
  // ---------------------------------------------------------------------------------------

  "A table" should "render as table, tbody, tr, th and td" in {
    val html = DocumentView.renderToHtml(document, TableSupport.views)
    val tags = html.replaceAll("<!--.*?-->", "")

    tags.replace("<span>", "").replace("</span>", "") shouldBe
      "<article><table><tbody><tr><th><p>A</p></th><th align=\"center\"><p>B</p></th></tr>" +
      "<tr><td><p>1</p></td><td align=\"center\"><p>2</p></td></tr></tbody></table></article>"
  }

  it should "carry node ids in the editor profile and project through a session" in {
    val session =
      EditorSession.create(document, resolved, resolved.sessionConfig()).getOrElse(fail("session"))
    val cursor = new SsrCursor()
    DocumentView.mount(session, cursor, TableSupport.views, RenderProfile.Editor)

    cursor.collectHtml() should include("data-ember-node=\"b\"")

    session.update(_.select(RangeSelection.caret(Point.textBefore(NodeId("d-t"), 1))): Unit)
    session.dispatch(TableCommands.InsertRow, RowPosition.Below)

    cursor.collectHtml().split("<tr").length - 1 shouldBe 3
  }

  // ---------------------------------------------------------------------------------------
  // JSON
  // ---------------------------------------------------------------------------------------

  "JSON" should "round-trip a table completely" in {
    val text =
      DocumentJson.encodeToString(document, json).fold(errors => fail(errors.toString), identity)
    val decoded = DocumentJson
      .decodeString(text, resolved.schema, json)
      .fold(errors => fail(errors.toString), _.document)

    decoded.inDocumentOrder.toVector shouldBe document.inDocumentOrder.toVector
  }

  it should "refuse an unknown alignment" in {
    val text = DocumentJson
      .encodeToString(document, json)
      .fold(errors => fail(errors.toString), identity)
      .replace("\"center\"", "\"diagonal\"")

    DocumentJson.decodeString(text, resolved.schema, json) shouldBe a[Left[?, ?]]
  }

  // ---------------------------------------------------------------------------------------
  // Markdown
  // ---------------------------------------------------------------------------------------

  "Markdown" should "write a GFM table" in {
    MarkdownCodec.encode(document, markdown).map(_.source) shouldBe
      Right("| A | B |\n| --- | :---: |\n| 1 | 2 |\n")
  }

  it should "read it back into the same table" in {
    val source  = MarkdownCodec.encode(document, markdown).toOption.get.source
    val decoded = MarkdownCodec
      .decode(
        source,
        resolved.schema,
        markdown,
        NodeIdGenerator.sequential("m"),
        root,
        MarkdownProfile.commonMarkSafeWithTables
      )
      .fold(error => fail(error.message), _.document)

    shape(decoded) shouldBe shape(document)
    shape(decoded) shouldBe "*A|*B(c) / 1|2(c)"
  }

  it should "report a cell with two paragraphs as a loss" in {
    val session =
      EditorSession.create(document, resolved, resolved.sessionConfig()).getOrElse(fail("session"))
    session.update(_.select(RangeSelection.caret(Point.textBefore(NodeId("c-t"), 1))): Unit)
    session.dispatch(RichText.InsertParagraph)

    MarkdownCodec.encode(session.document, markdown) shouldBe a[Left[?, ?]]
    MarkdownCodec
      .encode(session.document, markdown, LossPolicy.AllowLossy)
      .map(_.losses.length) shouldBe Right(1)
  }

  it should "stay a paragraph under the plain CommonMark profile" in {
    MarkdownCodec
      .decode("| A |\n| - |", resolved.schema, markdown, NodeIdGenerator.sequential("m"), root)
      .fold(error => fail(error.message), result => shape(result.document)) shouldBe "-"
  }

  // ---------------------------------------------------------------------------------------
  // HTML import
  // ---------------------------------------------------------------------------------------

  "Pasted HTML" should "become a table with its header and alignment" in {
    val result = HtmlImport
      .imported(
        "<table><thead><tr><th>A</th><th align=\"right\">B</th></tr></thead>" +
          "<tbody><tr><td>1</td><td align=\"right\"><b>2</b></td></tr></tbody></table>",
        resolved.schema,
        imports,
        NodeIdGenerator.sequential("h"),
        root
      )
      .fold(error => fail(error.render), identity)

    shape(result.document) shouldBe "*A|*B(r) / 1|2(r)"
  }

  it should "keep a caption's absence honest" in {
    val result = HtmlImport
      .imported(
        "<table><caption>Titel</caption><tr><td>x</td></tr></table>",
        resolved.schema,
        imports,
        NodeIdGenerator.sequential("h"),
        root
      )
      .fold(error => fail(error.render), identity)

    shape(result.document) shouldBe "x"
    result.diagnostics.map(_.render).mkString should include("caption")
  }

  it should "be squared up once it enters a session" in {
    val pasted = HtmlImport
      .imported(
        "<table><tr><td>a</td><td>b</td></tr><tr><td>c</td></tr></table>",
        resolved.schema,
        imports,
        NodeIdGenerator.sequential("h"),
        root
      )
      .fold(error => fail(error.render), _.document)

    val session = EditorSession
      .create(
        RichText.emptyDocument(resolved.schema, generator).toOption.get,
        resolved,
        resolved.sessionConfig()
      )
      .getOrElse(fail("session"))
    session.update { tx =>
      tx.restore(pasted, None): Unit
    }

    // A restore is a document replacement: every node is new to the session, so the rules run.
    shape(session.document) should (equal("a|b / c|") or equal("a|b / c"))
  }

  "The standard bundles" should "not contain tables unless asked" in {
    // X01: "normale Editoren ziehen das Modul nicht herein".
    ImageSupport.everything.entries.exists(_.nodeType == TableNode) shouldBe false
    TableSupport.everything.entries.exists(_.nodeType == TableNode) shouldBe true
  }
}
