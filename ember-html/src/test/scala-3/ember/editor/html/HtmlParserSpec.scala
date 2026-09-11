package ember.editor.html

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The tokenizer and the fragment parser (P24, §19.1).
  *
  * ==What a passing suite here does and does not claim==
  *
  * It claims that the shapes clipboards produce are read the way §19.1's recovery rules say. It
  * does '''not''' claim HTML5 tree construction -- §19.1 declines that in so many words, and a test
  * that pretended otherwise would be the beginning of an obligation nobody signed.
  *
  * The security half is `HtmlSecuritySpec`; the mapping to document nodes is `HtmlImportSpec`.
  */
final class HtmlParserSpec extends AnyFlatSpec with Matchers {

  private def parse(html: String): Vector[HtmlFragment] =
    HtmlFragmentParser.parse(html) match
      case Right(result) => result.fragments
      case Left(error)   => fail(error.render)

  private def losses(html: String): Vector[String] =
    HtmlFragmentParser.parse(html) match
      case Right(result) => result.diagnostics.map(_.render)
      case Left(error)   => fail(error.render)

  /** A fragment as `p[text]`, so a test reads like the markup it came from. */
  private def shape(fragment: HtmlFragment): String = fragment match
    case HtmlFragment.Text(value)                        => s""""$value""""
    case HtmlFragment.Element(tag, attributes, children) =>
      val shown = attributes.map(a => s"@${a.name}=${a.value}").mkString(" ")
      val head  = if shown.isEmpty then tag else s"$tag($shown)"
      if children.isEmpty then head else s"$head[${children.map(shape).mkString(",")}]"

  private def shapes(html: String): String = parse(html).map(shape).mkString(",")

  // ---------------------------------------------------------------------------------------
  // Tokenisierung
  // ---------------------------------------------------------------------------------------

  "The tokenizer" should "read text, tags and attributes" in {
    shapes("""<p class="x" id="a">Hallo</p>""") shouldBe """p(@id=a)["Hallo"]"""
  }

  it should "lower-case tag and attribute names" in {
    // Word writes `<P CLASS=MsoNormal>`, and a rule table that had to know about case would be a
    // rule table with two of every entry.
    shapes("""<P ID="a">x</P>""") shouldBe """p(@id=a)["x"]"""
  }

  it should "read unquoted and single-quoted values" in {
    shapes("""<a href=http://x.test title='y'>z</a>""") shouldBe
      """a(@href=http://x.test @title=y)["z"]"""
  }

  it should "keep the first of two attributes with the same name" in {
    // A Word speciality, and the browsers agree on keeping the first.
    shapes("""<p id="a" id="b">x</p>""") shouldBe """p(@id=a)["x"]"""
  }

  it should "treat a lone angle bracket as text" in {
    // Pasted prose is full of these. A parser that failed on `a < b` would fail on arithmetic.
    shapes("a < b") shouldBe """"a < b""""
    shapes("2 </ 3") shouldBe """"2 </ 3""""
  }

  it should "keep text in front of a close tag in order" in {
    shapes("<p>a</p>") shouldBe """p["a"]"""
  }

  // ---------------------------------------------------------------------------------------
  // Entities
  // ---------------------------------------------------------------------------------------

  "Entities" should "be decoded in text and in attributes" in {
    shapes("<p>a&amp;b&nbsp;c</p>") shouldBe "p[\"a&b c\"]"
    shapes("""<a title="a&amp;b">x</a>""") shouldBe """a(@title=a&b)["x"]"""
  }

  it should "decode numeric references, decimal and hexadecimal" in {
    shapes("<p>&#65;&#x42;&#x1F600;</p>") shouldBe "p[\"AB😀\"]"
  }

  it should "replace what no code point may be" in {
    // The specification's rule, not a rejection: pasted HTML is full of these, and a paste that
    // fails on one is a paste that fails.
    shapes("<p>&#0;</p>") shouldBe "p[\"�\"]"
    shapes("<p>&#xD800;</p>") shouldBe "p[\"�\"]"
  }

  it should "leave a bare ampersand alone" in {
    shapes("<p>Tom & Jerry</p>") shouldBe """p["Tom & Jerry"]"""
    shapes("<p>&notaname;</p>") shouldBe """p["&notaname;"]"""
  }

  it should "not scan across half a paragraph for a semicolon" in {
    // The bound exists so that two bare ampersands far apart do not swallow what is between them.
    shapes("<p>a & b, c & d, and a good deal more text besides;</p>") shouldBe
      """p["a & b, c & d, and a good deal more text besides;"]"""
  }

  // ---------------------------------------------------------------------------------------
  // Void-Elemente
  // ---------------------------------------------------------------------------------------

  "A void element" should "never take children, however it is written" in {
    shapes("<p>a<br>b</p>") shouldBe """p["a",br,"b"]"""
    shapes("<p>a<br/>b</p>") shouldBe """p["a",br,"b"]"""
    shapes("<p>a<br />b</p>") shouldBe """p["a",br,"b"]"""
    shapes("<p>a<br></br>b</p>") shouldBe """p["a",br,"b"]"""
  }

  it should "keep its attributes" in {
    shapes("""<img src="x.png" alt="y">""") shouldBe "img(@src=x.png @alt=y)"
  }

  // ---------------------------------------------------------------------------------------
  // Fehlformungen (§19.1s Recovery-Regeln)
  // ---------------------------------------------------------------------------------------

  "Crossed tags" should "close through to the one that matches" in {
    // A browser reopens the `<i>` afterwards. This does not, and says so.
    shapes("<b><i>x</b>y") shouldBe """b[i["x"]],"y""""
  }

  "A stray close tag" should "be ignored, not pasted" in {
    // The shape of every fragment copied from the middle of a page.
    shapes("</div><p>x</p>") shouldBe """p["x"]"""
  }

  "An unclosed element" should "be closed at the end" in {
    shapes("<p>x") shouldBe """p["x"]"""
    shapes("<ul><li>a<li>b") shouldBe """ul[li["a"],li["b"]]"""
  }

  "A paragraph" should "be closed by the next block" in {
    // Word emits unclosed `<p>` constantly, and without this rule a whole document nests inside
    // its first paragraph.
    shapes("<p>a<p>b") shouldBe """p["a"],p["b"]"""
    shapes("<p>a<h2>b</h2>") shouldBe """p["a"],h2["b"]"""
    shapes("<p>a<ul><li>b</li></ul>") shouldBe """p["a"],ul[li["b"]]"""
  }

  "List items" should "close each other" in {
    shapes("<ul><li>a<li>b</li></ul>") shouldBe """ul[li["a"],li["b"]]"""
  }

  // ---------------------------------------------------------------------------------------
  // Rohtext
  // ---------------------------------------------------------------------------------------

  "A script" should "not have its content read as markup" in {
    // The `<` inside must not start a tag, or the rest of the document becomes script content.
    losses("<p>a</p><script>if (a < b) { x(); }</script><p>b</p>") should contain(
      "Element verworfen: <script>"
    )
    shapes("<p>a</p><script>if (a < b) { x(); }</script><p>b</p>") shouldBe """p["a"],p["b"]"""
  }

  it should "be dropped even when it is never closed" in {
    shapes("<p>a</p><script>x()") shouldBe """p["a"]"""
  }

  "A style block" should "go the same way" in {
    shapes("<style>p { color: red }</style><p>a</p>") shouldBe """p["a"]"""
  }

  // ---------------------------------------------------------------------------------------
  // Kommentare und Doctype
  // ---------------------------------------------------------------------------------------

  "Comments and doctypes" should "leave nothing behind" in {
    shapes("<!doctype html><!-- note --><p>a</p>") shouldBe """p["a"]"""
  }

  it should "not swallow the document when unterminated" in {
    shapes("<p>a</p><!-- open") shouldBe """p["a"]"""
  }

  // ---------------------------------------------------------------------------------------
  // Grenzen
  // ---------------------------------------------------------------------------------------

  "A fragment" should "be refused when it is longer than the limit" in {
    val policy = HtmlImportPolicy.default.copy(limits = HtmlLimits(maxSourceChars = 10))

    HtmlFragmentParser.parse("<p>0123456789</p>", policy) should matchPattern {
      case Left(HtmlParseError.LimitExceeded("maxSourceChars", 10, _)) =>
    }
  }

  it should "be refused when it nests deeper than the limit" in {
    val policy = HtmlImportPolicy.default.copy(limits = HtmlLimits(maxDepth = 3))

    HtmlFragmentParser.parse("<div><div><div><div>x</div></div></div></div>", policy) should
      matchPattern { case Left(HtmlParseError.LimitExceeded("maxDepth", 3, _)) => }
  }

  it should "be refused when it holds more nodes than the limit" in {
    val policy = HtmlImportPolicy.default.copy(limits = HtmlLimits(maxNodes = 3))

    HtmlFragmentParser.parse("<p>a</p><p>b</p><p>c</p><p>d</p>", policy) should
      matchPattern { case Left(HtmlParseError.LimitExceeded("maxNodes", 3, _)) => }
  }

  it should "take a fragment that stays inside them" in {
    val policy = HtmlImportPolicy.default.copy(limits = HtmlLimits(maxNodes = 10, maxDepth = 5))

    HtmlFragmentParser.parse("<p>a</p>", policy).map(_.fragments.length) shouldBe Right(1)
  }
}
