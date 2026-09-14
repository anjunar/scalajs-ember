package ember.editor.standard

import ember.editor.code.*
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.*
import ember.editor.link.*
import ember.editor.list.*
import ember.editor.markdown.*
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class ReviewFormatProbeSpec extends AnyFlatSpec with Matchers {
  private val root = NodeId("root")
  private val links = LinkUrlPolicy.default
  private val media = MediaUrlPolicy.default
  private val schema = ExtensionResolver.resolve(Vector(
    RichText(NodeIdGenerator.sequential("x")),
    ListExtension(NodeIdGenerator.sequential("l")),
    LinkExtension(NodeIdGenerator.sequential("k")),
    CodeExtension(NodeIdGenerator.sequential("c")),
    ImageExtension(NodeIdGenerator.sequential("i"), media)
  )).toOption.get.schema
  private val markdown = MarkdownSupports.everything(links, media)

  private def html(source: String): Document = HtmlImport.imported(source, schema,
    StandardHtmlImport.everything(links, media), NodeIdGenerator.sequential("n"), root
  ).toOption.get.document
  private def runs(document: Document): Vector[TextNode] =
    document.inDocumentOrder.collect { case text: TextNode => text }.toVector
  private def encode(document: Document): EncodedMarkdown =
    MarkdownCodec.encode(document, markdown).toOption.get
  private def decode(source: String): Document = MarkdownCodec.decode(source, schema, markdown,
    NodeIdGenerator.sequential("m"), root).toOption.get.document
  private def paragraph(parts: (String, MarkSet)*): Document = {
    val texts = parts.zipWithIndex.map { case ((text, marks), i) => TextNode(NodeId(s"t$i"), text, marks) }.toVector
    Document.build(schema, root, Vector[EditorNode](RootNode(root, Vector(NodeId("p"))),
      ParagraphNode(NodeId("p"), texts.map(_.id))) ++ texts).toOption.get
  }

  "HTML import" should "retain an outer strong mark through links" in {
    runs(html("<p><strong><a href='/x'>bold</a></strong></p>")).head.marks shouldBe MarkSet.of(StandardMarks.Strong)
  }
  it should "retain spaces between loose inline siblings" in {
    runs(html("<strong>hello</strong> <em>world</em>")).map(_.text).mkString shouldBe "hello world"
  }
  it should "unwrap span without introducing new paragraphs" in {
    html("Hello <span>world</span>!").childrenOf(root).length shouldBe 1
  }
  it should "keep the horizontal rule through strict markdown export" in {
    val source = encode(html("<p>before</p><hr><p>after</p>")).source
    withClue(source) { decode(source).inDocumentOrder.exists(_.isInstanceOf[ThematicBreakNode]) shouldBe true }
  }
  "Strict markdown export" should "retain marked text including trailing space" in {
    val source = encode(paragraph("hello " -> MarkSet.of(StandardMarks.Strong))).source
    withClue(source) { runs(decode(source)).map(t => t.text -> t.marks) shouldBe Vector("hello " -> MarkSet.of(StandardMarks.Strong)) }
  }
  it should "retain spaces at both ends of inline code" in {
    val source = encode(paragraph(" code " -> MarkSet.of(StandardMarks.InlineCode))).source
    withClue(source) { runs(decode(source)).head.text shouldBe " code " }
  }
  it should "retain all adjacent inline code runs" in {
    val source = encode(paragraph("a" -> MarkSet.of(StandardMarks.InlineCode), "b" -> MarkSet.of(StandardMarks.InlineCode))).source
    withClue(source) { runs(decode(source)).map(_.text).mkString shouldBe "ab" }
  }
  "Markdown parse limits" should "bound inline nesting as well as block nesting" in {
    val source = "![" * 20 + "x" + "](x)" * 20
    val limited = MarkdownProfile.commonMarkSafe.copy(limits = ParseLimits(maxDepth = 5))
    Markdown.parseSyntax(source, limited).isLeft shouldBe true
  }
}
