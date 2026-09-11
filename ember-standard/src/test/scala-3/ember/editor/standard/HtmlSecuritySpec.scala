package ember.editor.standard

import ember.editor.code.CodeExtension
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.{ImageExtension, ImageNode, MediaUrlPolicy}
import ember.editor.link.{LinkExtension, LinkNode, LinkUrlPolicy}
import ember.editor.list.ListExtension
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** What the safe fragment profile refuses (P24, §19.1).
  *
  * ==Why this is a suite of its own==
  *
  * Because the other two ask "does it read the fragment correctly" and this one asks "can it be
  * made to do something it should not". Those are different questions and they fail differently: a
  * parsing bug produces wrong text, a sanitising bug produces a working attack, and only the second
  * one is worth a suite whose every case is an attempt.
  *
  * §19.1 gives the list, and every test below is one line of it: "script/style/aktive Embeds werden
  * verworfen, Eventattribute und beliebige CSS-Strings nicht uebernommen, unbekannte harmlose
  * Wrapper werden mit erhaltenem Text aufgeloest. URLs werden nach
  * Entities-/Whitespace-Normalisierung durch die jeweilige Link-/Media-Policy geprueft."
  */
final class HtmlSecuritySpec extends AnyFlatSpec with Matchers {

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

  /** Every attribute that survived, as `name=value`. */
  private def attributes(html: String): Vector[String] =
    HtmlFragmentParser.parse(html) match
      case Right(result) => result.fragments.flatMap(collect)
      case Left(error)   => fail(error.render)

  private def collect(fragment: HtmlFragment): Vector[String] = fragment match
    case HtmlFragment.Text(_)                          => Vector.empty
    case HtmlFragment.Element(_, attributes, children) =>
      attributes.map(a => s"${a.name}=${a.value}") ++ children.flatMap(collect)

  private def text(html: String): String =
    val result = imported(html)
    result.document.inDocumentOrder.collect { case run: TextNode => run.text }.mkString

  private def links(html: String): Vector[String] =
    imported(html).document.inDocumentOrder.collect { case link: LinkNode =>
      link.url.value
    }.toVector

  private def images(html: String): Vector[String] =
    imported(html).document.inDocumentOrder.collect { case image: ImageNode =>
      image.source.src.value
    }.toVector

  private def losses(html: String): String =
    imported(html).diagnostics.map(_.render).mkString(" | ")

  // ---------------------------------------------------------------------------------------
  // Script, Style, aktive Embeds
  // ---------------------------------------------------------------------------------------

  "A script" should "leave nothing, not even its text" in {
    // Its content is not prose. Keeping it as text would paste a program into a paragraph, and
    // pasting it onward would be a way to smuggle one.
    text("""<p>a</p><script>alert(1)</script><p>b</p>""") shouldBe "ab"
    losses("""<script>alert(1)</script>""") should include("<script>")
  }

  it should "not escape through a broken close tag" in {
    text("""<p>a</p><script>alert(1)<p>b</p>""") shouldBe "a"
  }

  it should "not escape through case" in {
    text("""<p>a</p><SCRIPT>alert(1)</SCRIPT><p>b</p>""") shouldBe "ab"
  }

  it should "not escape through a nested close tag in a string" in {
    // The oldest trick: the tokenizer must stop at the first `</script`, and everything before it
    // is text belonging to the script -- including what looks like markup.
    text("""<script>var x = "</p>";</script><p>b</p>""") shouldBe "b"
  }

  "A style block" should "go the same way" in {
    text("""<style>p { background: url(http://x.test/track) }</style><p>a</p>""") shouldBe "a"
  }

  "Active embeds" should "be refused" in {
    Vector("iframe", "object", "embed", "applet", "frame").foreach { tag =>
      text(s"<p>a</p><$tag src='http://x.test'>inner</$tag>") shouldBe "a"
    }
  }

  "A form" should "not bring its controls" in {
    // Not an attack by itself, but a pasted form is a paste of someone else's endpoint.
    text(
      """<form action="http://x.test"><input value="x"><button>go</button></form><p>a</p>"""
    ) shouldBe
      "a"
  }

  // ---------------------------------------------------------------------------------------
  // Attribute
  // ---------------------------------------------------------------------------------------

  "Event attributes" should "never survive" in {
    // By prefix, not by list: a list would be a list to keep up to date, and the day it falls
    // behind is the day an `onfocusin` gets through.
    attributes("""<p onclick="x()" onmouseover="y()" ONERROR="z()">a</p>""") shouldBe empty
    losses("""<p onclick="x()">a</p>""") should include("Eventattribut")
  }

  "A style attribute" should "never survive" in {
    // §19.1 rules out arbitrary CSS as a document format, and the reason is not taste: `style`
    // carries `position`, `url(...)` and everything else the cascade can do.
    attributes("""<p style="position:fixed;top:0">a</p>""") shouldBe empty
    losses("""<p style="x">a</p>""") should include("freies CSS")
  }

  "An editor attribute" should "not come in from outside" in {
    // §19.1: "browserseitige Wrapper/Editor-Attribute werden beim Austausch entfernt." A paste
    // carrying node ids would import another document's identity into this one.
    attributes("""<p data-ember-node="root">a</p>""") shouldBe empty
    losses("""<p data-ember-node="root">a</p>""") should include("Editor-Attribut")
  }

  "A class" should "survive only when it names a code language" in {
    attributes("""<code class="language-scala">a</code>""") shouldBe Vector("class=language-scala")
    attributes("""<p class="MsoNormal">a</p>""") shouldBe empty
  }

  "An unknown attribute" should "be dropped without a message" in {
    // Reporting every `cellpadding` from a Word table would bury the losses that matter.
    attributes("""<p cellpadding="3" bgcolor="red">a</p>""") shouldBe empty
    losses("""<p cellpadding="3">a</p>""") shouldBe ""
  }

  // ---------------------------------------------------------------------------------------
  // Adressen
  // ---------------------------------------------------------------------------------------

  "A javascript link" should "lose its target and keep its text" in {
    // The single most common attack in pasted HTML, and the single most common over-reaction is
    // to drop the text with it.
    text("""<p><a href="javascript:alert(1)">Klick</a></p>""") shouldBe "Klick"
    links("""<p><a href="javascript:alert(1)">Klick</a></p>""") shouldBe empty
    losses("""<p><a href="javascript:alert(1)">x</a></p>""") should include("Adresse abgelehnt")
  }

  it should "not get through with whitespace inside the scheme" in {
    // `java\tscript:` is the oldest way past a scheme check, and it is why §19.1 puts
    // normalisation before the policy rather than after it.
    links("""<p><a href="java&#9;script:alert(1)">x</a></p>""") shouldBe empty
    links("<p><a href=\"java\nscript:alert(1)\">x</a></p>") shouldBe empty
    links("""<p><a href=" javascript:alert(1)">x</a></p>""") shouldBe empty
  }

  it should "not get through as an entity" in {
    links("""<p><a href="&#106;avascript:alert(1)">x</a></p>""") shouldBe empty
    links("""<p><a href="&#x6A;avascript:alert(1)">x</a></p>""") shouldBe empty
  }

  it should "not get through by case" in {
    links("""<p><a href="JaVaScRiPt:alert(1)">x</a></p>""") shouldBe empty
  }

  "A data URL" should "be refused" in {
    // `data:text/html` is a document of its own, and the media policy refuses it for the same
    // reason §20 gives: it is not a durable media reference.
    links("""<p><a href="data:text/html,<script>alert(1)</script>">x</a></p>""") shouldBe empty
    images("""<p><img src="data:text/html,x" alt="y"></p>""") shouldBe empty
  }

  "An ordinary link" should "still work" in {
    // The counter-check without which every test above would also pass on an importer that
    // refused everything.
    links("""<p><a href="https://example.test/a">x</a></p>""") shouldBe
      Vector("https://example.test/a")
    links("""<p><a href="/relativ">x</a></p>""") shouldBe Vector("/relativ")
  }

  "An unsafe image" should "keep its alt text" in {
    // §19.1: "Unsichere Bilder werden mit Diagnose als Alt-Text erhalten." A picture that cannot
    // be shown is still something the author wrote about.
    text("""<p><img src="javascript:x" alt="Ein Diagramm"></p>""") shouldBe "Ein Diagramm"
    images("""<p><img src="javascript:x" alt="Ein Diagramm"></p>""") shouldBe empty
    losses("""<p><img src="javascript:x" alt="y"></p>""") should include("Adresse abgelehnt")
  }

  it should "leave nothing behind when it has none" in {
    text("""<p>a<img src="javascript:x"></p>""") shouldBe "a"
  }

  "A safe image" should "still work" in {
    images("""<p><img src="/a.png" alt="x"></p>""") shouldBe Vector("/a.png")
  }

  // ---------------------------------------------------------------------------------------
  // Grenzen
  // ---------------------------------------------------------------------------------------

  "A fragment" should "not be allowed to be arbitrarily large" in {
    // A paste is the most convenient way to hand an editor a megabyte of markup, deliberately or
    // by accident.
    val policy = HtmlImportPolicy.default.copy(limits = HtmlLimits(maxNodes = 5))
    val many   = (1 to 50).map(index => s"<p>$index</p>").mkString

    HtmlImport.imported(
      many,
      resolved.schema,
      support,
      NodeIdGenerator.sequential("n"),
      NodeId("root"),
      policy
    ) should matchPattern { case Left(HtmlImportError.Parse(_)) => }
  }

  "A deeply nested fragment" should "not be allowed to exhaust the stack" in {
    val policy = HtmlImportPolicy.default.copy(limits = HtmlLimits(maxDepth = 20))
    val deep   = "<div>" * 200 + "x" + "</div>" * 200

    HtmlImport.imported(
      deep,
      resolved.schema,
      support,
      NodeIdGenerator.sequential("n"),
      NodeId("root"),
      policy
    ) should matchPattern { case Left(HtmlImportError.Parse(_)) => }
  }

  // ---------------------------------------------------------------------------------------
  // Die Zusicherung, die alles traegt
  // ---------------------------------------------------------------------------------------

  "The import" should "produce a document, never markup" in {
    // §19.1's acceptance: "Keine Einfuegung rohen HTMLs in den lebenden DOM." The structural
    // guarantee behind it is that the result is a `Document` -- nothing here can hand a caller a
    // string of HTML to insert, because nothing here produces one.
    val result = imported("""<p onclick="x()"><script>y()</script>Text</p>""")

    result.document.inDocumentOrder.collect { case run: TextNode => run.text }.mkString shouldBe
      "Text"
  }
}
