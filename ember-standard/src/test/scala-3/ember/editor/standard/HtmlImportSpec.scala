package ember.editor.standard

import ember.editor.code.{CodeBlockNode, CodeExtension}
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.{ImageExtension, ImageNode, MediaUrlPolicy}
import ember.editor.link.{LinkExtension, LinkNode, LinkUrlPolicy}
import ember.editor.list.{ListExtension, ListItemNode, ListKind, ListNode}
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** HTML into a document (P24, §19.1).
  *
  * ==What is being tested here and not in `ember-html`==
  *
  * The parser suite proves that a fragment is read the way the recovery rules say. This one proves
  * that the fragment becomes a '''document''' -- which is a different problem, because HTML has no
  * rule that text must sit in a block and a document has exactly that rule. The repair is the
  * interesting part, and the repair only exists once a profile supplies a paragraph.
  */
final class HtmlImportSpec extends AnyFlatSpec with Matchers {

  private val links = LinkUrlPolicy.default
  private val media = MediaUrlPolicy.default

  private val resolved = ExtensionResolver
    .resolve(
      Vector(
        RichText(NodeIdGenerator.sequential("x")),
        ListExtension(NodeIdGenerator.sequential("l")),
        LinkExtension(NodeIdGenerator.sequential("k")),
        CodeExtension(NodeIdGenerator.sequential("c")),
        ImageExtension(NodeIdGenerator.sequential("i"), media)
      )
    )
    .getOrElse(throw new AssertionError("Extensions nicht aufloesbar"))

  private val support = StandardHtmlImport.everything(links, media)

  private def imported(html: String): ImportedHtml =
    HtmlImport.imported(
      html,
      resolved.schema,
      support,
      NodeIdGenerator.sequential("n"),
      NodeId("root")
    ) match
      case Right(result) => result
      case Left(error)   => fail(error.render)

  /** The document as `p["a",strong["b"]]`, so that a test reads like the HTML it came from. */
  private def outline(html: String): String =
    val result = imported(html)
    result.document
      .childrenOf(result.document.rootId)
      .map(shape(result.document, _))
      .mkString(",")

  private def shape(document: DocumentRead, id: NodeId): String =
    document.node(id) match
      case Some(run: TextNode) =>
        val marks = run.marks.markIds.map(_.value.split('.').last.takeWhile(_ != '/')).sorted
        if marks.isEmpty then s""""${run.text}""""
        else s"""${marks.mkString("+")}("${run.text}")"""
      case Some(node: ParagraphNode) => s"p[${children(document, node.children)}]"
      case Some(node: HeadingNode) => s"h${node.level.level}[${children(document, node.children)}]"
      case Some(node: QuoteNode)   => s"quote[${children(document, node.children)}]"
      case Some(node: ListNode)    =>
        val kind = if node.kind == ListKind.Ordered then "ol" else "ul"
        s"$kind${if node.start != 1 then s"@${node.start}" else ""}[${children(document, node.children)}]"
      case Some(node: ListItemNode) => s"li[${children(document, node.children)}]"
      case Some(node: LinkNode)     => s"a(${node.url.value})[${children(document, node.children)}]"
      case Some(node: CodeBlockNode) =>
        s"code(${node.info.render})[${children(document, node.children)}]"
      case Some(node: ImageNode)      => s"img(${node.source.src.value},${node.alt})"
      case Some(_: BreakNode)         => "br"
      case Some(_: ThematicBreakNode) => "hr"
      case Some(other)                => other.getClass.getSimpleName
      case None                       => "?"

  private def children(document: DocumentRead, ids: Vector[NodeId]): String =
    ids.map(shape(document, _)).mkString(",")

  private def losses(html: String): Vector[String] = imported(html).diagnostics.map(_.render)

  // ---------------------------------------------------------------------------------------
  // Der Reparaturteil
  // ---------------------------------------------------------------------------------------

  "Loose text" should "get a paragraph of its own" in {
    // A document has a rule HTML does not, and this is where it is applied.
    outline("Hallo") shouldBe """p["Hallo"]"""
  }

  it should "not be merged across a block boundary" in {
    outline("a<p>b</p>c") shouldBe """p["a"],p["b"],p["c"]"""
  }

  "Whitespace between blocks" should "not become a paragraph" in {
    // The indentation of the page the fragment came from is not content.
    outline("<p>a</p>\n  <p>b</p>") shouldBe """p["a"],p["b"]"""
  }

  "Whitespace inside a paragraph" should "collapse the way HTML says" in {
    outline("<p>a\n   b</p>") shouldBe """p["a b"]"""
  }

  "An inline element at the top level" should "join the paragraph around it" in {
    outline("a <b>b</b> c") shouldBe """p["a ",strong("b")," c"]"""
  }

  // ---------------------------------------------------------------------------------------
  // Bloecke
  // ---------------------------------------------------------------------------------------

  "Blocks" should "become the nodes they mean" in {
    outline("<h2>T</h2><p>a</p><blockquote><p>b</p></blockquote><hr>") shouldBe
      """h2["T"],p["a"],quote[p["b"]],p[hr]"""
  }

  "A div" should "be a paragraph when it holds prose" in {
    // Word and every web page wrap prose in divs. Dissolving them all would run two paragraphs
    // into one.
    outline("<div>a</div><div>b</div>") shouldBe """p["a"],p["b"]"""
  }

  it should "dissolve when it holds blocks" in {
    outline("<div><p>a</p><p>b</p></div>") shouldBe """p["a"],p["b"]"""
  }

  "A break" should "survive inside its paragraph" in {
    outline("<p>a<br>b</p>") shouldBe """p["a",br,"b"]"""
  }

  // ---------------------------------------------------------------------------------------
  // Marks
  // ---------------------------------------------------------------------------------------

  "Marks" should "come in every spelling that occurs" in {
    // And the two spellings become one run: §8.2 wants maximal runs, and an imported document is
    // canonical or nothing will make it so -- the normalisation transform only ever sees changed
    // nodes, and a freshly built document has none.
    outline("<p><b>a</b><strong>b</strong><i>c</i><em>d</em></p>") shouldBe
      """p[strong("ab"),emphasis("cd")]"""
  }

  it should "nest" in {
    outline("<p><b>a<i>b</i></b></p>") shouldBe """p[strong("a"),emphasis+strong("b")]"""
  }

  it should "survive crossed tags" in {
    // §19.1's recovery: `<b><i>x</b>y` closes both. The `y` loses its emphasis, and that is the
    // documented difference to a browser's adoption agency.
    outline("<p><b><i>x</i></b>y</p>") shouldBe """p[emphasis+strong("x"),"y"]"""
  }

  // ---------------------------------------------------------------------------------------
  // Listen
  // ---------------------------------------------------------------------------------------

  "A list" should "keep its kind and its items" in {
    outline("<ul><li>a</li><li>b</li></ul>") shouldBe """ul[li[p["a"]],li[p["b"]]]"""
  }

  it should "keep an ordered list's start" in {
    // Losing it renumbers a pasted list from one.
    outline("""<ol start="5"><li>a</li></ol>""") shouldBe """ol@5[li[p["a"]]]"""
  }

  it should "survive unclosed items" in {
    outline("<ul><li>a<li>b</ul>") shouldBe """ul[li[p["a"]],li[p["b"]]]"""
  }

  // ---------------------------------------------------------------------------------------
  // Links und Bilder
  // ---------------------------------------------------------------------------------------

  "A link" should "keep its target" in {
    outline("""<p><a href="https://example.test/x">y</a></p>""") shouldBe
      """p[a(https://example.test/x)["y"]]"""
  }

  it should "become plain text when it has no target" in {
    outline("""<p><a name="x">y</a></p>""") shouldBe """p["y"]"""
  }

  "An image" should "keep source and alt text" in {
    outline("""<p><img src="/a.png" alt="Ein Bild"></p>""") shouldBe """p[img(/a.png,Ein Bild)]"""
  }

  it should "keep its measurements when it has them" in {
    val result = imported("""<img src="/a.png" alt="x" width="96" height="48">""")
    val image  = result.document.inDocumentOrder.collectFirst { case node: ImageNode => node }

    image.flatMap(_.width).map(_.value) shouldBe Some(96)
    image.flatMap(_.height).map(_.value) shouldBe Some(48)
  }

  // ---------------------------------------------------------------------------------------
  // Code
  // ---------------------------------------------------------------------------------------

  "A code block" should "keep its whitespace" in {
    // The one place where the collapsing rule is wrong, and the rule has to say so itself.
    outline("<pre><code>a\n  b</code></pre>") shouldBe "code()[\"a\n  b\"]"
  }

  it should "take its language from the inner code element" in {
    outline("""<pre><code class="language-scala">x</code></pre>""") shouldBe """code(scala)["x"]"""
  }

  it should "not turn the inner code into an inline mark" in {
    val result = imported("<pre><code>x</code></pre>")
    val runs   = result.document.inDocumentOrder.collect { case run: TextNode => run }

    runs.flatMap(_.marks.markIds) shouldBe empty
  }

  "Inline code" should "still be a mark outside a pre" in {
    outline("<p>a <code>b</code></p>") shouldBe """p["a ",inline-code("b")]"""
  }

  // ---------------------------------------------------------------------------------------
  // Verlust wird gemeldet
  // ---------------------------------------------------------------------------------------

  "An unknown wrapper" should "dissolve with its text kept" in {
    // §19.1: "unbekannte harmlose Wrapper werden mit erhaltenem Text aufgeloest."
    outline("<p><wibble>a</wibble></p>") shouldBe """p["a"]"""
  }

  "A block inside inline content" should "be reported and flattened" in {
    outline("<p><span><p>a</p></span></p>") shouldBe """p["a"]"""
    losses("<p><span><p>a</p></span></p>").mkString should include("Inline-Inhalt")
  }

  "A clean fragment" should "report no loss at all" in {
    // The counter-check: a diagnostic channel that always says something says nothing.
    imported("<p>a</p><h2>b</h2>").lossless shouldBe true
  }

  // ---------------------------------------------------------------------------------------
  // Ein Word-Fragment
  // ---------------------------------------------------------------------------------------

  "A fragment from a word processor" should "arrive as prose" in {
    // The shape Word actually puts on a clipboard: a conditional comment, a style block, classes
    // nobody wants, unclosed paragraphs, `&nbsp;` everywhere.
    val word =
      """<!--StartFragment--><style>p.MsoNormal { margin: 0 }</style>
        |<p class="MsoNormal"><span style="font-family:Calibri">Erster&nbsp;Absatz</span>
        |<p class="MsoNormal"><b><span>Zweiter</span></b> Absatz<!--EndFragment-->""".stripMargin

    // The `&nbsp;` stays a non-breaking space. It is content -- `Character.isWhitespace` says so
    // too -- and collapsing it would change what the author wrote.
    outline(word) shouldBe "p[\"Erster Absatz \"],p[strong(\"Zweiter\"),\" Absatz\"]"
  }
}
