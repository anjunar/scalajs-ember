package ember.editor.codehighlighting

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import TokenKind.*

/** What each shipped grammar colours (X02).
  *
  * Every test names the piece of text and the kind it must get, so that a failure reads like the
  * code it came from. The interesting tests are the ones about '''state''': a string or comment
  * that is not closed where it should be recolours every line after it, and that is the failure an
  * author notices first.
  */
final class LexerSpec extends AnyFlatSpec with Matchers {

  private def lex(language: String, text: String): Vector[(String, TokenKind)] =
    val grammar = HighlightLanguages.standard
      .grammarFor(language)
      .getOrElse(fail(s"no grammar for $language"))
    LexedText
      .lex(grammar, text)
      .tokens
      .map(token => text.substring(token.start, token.end) -> token.kind)

  private def kinds(language: String, text: String, piece: String): Vector[TokenKind] =
    lex(language, text).collect { case (`piece`, kind) => kind }

  // ---------------------------------------------------------------------------------------
  // Scala
  // ---------------------------------------------------------------------------------------

  "Scala" should "colour definitions, types and interpolations" in {
    val tokens = lex("scala", """def greet(name: String) = s"Hi $name"""")

    tokens should contain("def" -> Keyword)
    tokens should contain("greet" -> Function)
    tokens should contain("String" -> Type)
    tokens should contain("$name" -> Variable)
  }

  it should "not read a keyword out of a longer word" in {
    val tokens = lex("scala", "val valid = 1")

    tokens.filter(_._2 == Keyword) shouldBe Vector("val" -> Keyword)
    tokens should contain("1" -> Number)
  }

  it should "count nested block comments" in {
    // Scala nests them. Ending at the first `*/` would colour ` still */` as code.
    lex("scala", "/* a /* b */ still */ val x") should contain(
      "/* a /* b */ still */" -> Comment
    )
    kinds("scala", "/* a /* b */ still */ val x", "val") shouldBe Vector(Keyword)
  }

  it should "carry a triple-quoted string across lines" in {
    val text   = "val s = \"\"\"a\nval b\"\"\"\nval c"
    val tokens = lex("scala", text)

    tokens should contain("val b\"\"\"" -> String)
    tokens.count(_ == ("val" -> Keyword)) shouldBe 2
  }

  it should "end an unterminated string with its line" in {
    // A single-line string cannot continue, and a highlighter that let it would colour the whole
    // rest of the file as a string while the author types the closing quote.
    kinds("scala", "val a = \"oops\nval b = 1", "val") shouldBe Vector(Keyword, Keyword)
  }

  it should "return to the string after an interpolated block with braces" in {
    val tokens = lex("scala", "s\"${ if (x) { 1 } else 2 } done\"")

    tokens should contain("if" -> Keyword)
    tokens should contain("else" -> Keyword)
    tokens should contain(" done\"" -> String)
  }

  it should "tell annotations, characters and numbers apart" in {
    val tokens = lex("scala", "@main def run = ('a', 0xFF, 1_000L, 3.14)")

    tokens should contain("@main" -> Meta)
    tokens should contain("'a'" -> String)
    tokens should contain("0xFF" -> Number)
    tokens should contain("1_000L" -> Number)
    tokens should contain("3.14" -> Number)
  }

  // ---------------------------------------------------------------------------------------
  // JavaScript and TypeScript
  // ---------------------------------------------------------------------------------------

  "JavaScript" should "tell a regular expression from a division" in {
    val tokens = lex("javascript", "const r = /ab+c/g; const d = a / b / c")

    tokens should contain("/ab+c/g" -> String)
    tokens.count(_ == ("/" -> Operator)) shouldBe 2
  }

  it should "read a regular expression after return" in {
    lex("js", "return /x/.test(s)") should contain("/x/" -> String)
  }

  it should "come back from a template expression that contains braces" in {
    val tokens = lex("js", "`a ${ {x: 1}.x } b`")

    tokens should contain(" b`" -> String)
    tokens should contain("1" -> Number)
  }

  it should "colour declarations" in {
    val tokens = lex("js", "function add(a, b) { return a + b }")

    tokens should contain("function" -> Keyword)
    tokens should contain("add" -> Function)
    tokens should contain("return" -> Keyword)
  }

  it should "not know TypeScript's words" in {
    kinds("javascript", "interface = 1", "interface") shouldBe empty
  }

  "TypeScript" should "colour its declarations and primitive types" in {
    val tokens = lex("ts", "interface Point { x: number }")

    tokens should contain("interface" -> Keyword)
    tokens should contain("Point" -> Type)
    tokens should contain("number" -> Type)
  }

  // ---------------------------------------------------------------------------------------
  // JSON
  // ---------------------------------------------------------------------------------------

  "JSON" should "tell keys from values" in {
    val tokens = lex("json", """{"name": "Ember", "n": -1.5e3, "ok": true, "none": null}""")

    tokens should contain("\"name\"" -> Property)
    tokens should contain("\"Ember\"" -> String)
    tokens should contain("-1.5e3" -> Number)
    tokens should contain("true" -> Constant)
    tokens should contain("null" -> Constant)
    tokens should contain("{" -> Punctuation)
  }

  it should "colour escapes inside strings" in {
    lex("json", """{"a": "x\ny"}""") should contain("\\n" -> Escape)
  }

  // ---------------------------------------------------------------------------------------
  // CSS
  // ---------------------------------------------------------------------------------------

  "CSS" should "tell selectors, properties and values apart" in {
    val text =
      """/* theme
        |   colours */
        |@media screen {
        |  .card > a:hover { color: #ff0; margin: 0 auto !important; }
        |}""".stripMargin
    val tokens = lex("css", text)

    tokens should contain("   colours */" -> Comment)
    tokens should contain("@media" -> Keyword)
    tokens should contain(".card" -> Type)
    tokens should contain("a" -> Tag)
    tokens should contain(":hover" -> Meta)
    tokens should contain("color" -> Property)
    tokens should contain("#ff0" -> Number)
    tokens should contain("auto" -> Constant)
    tokens should contain("!important" -> Keyword)
  }

  it should "end a value at the closing brace" in {
    val tokens = lex("css", "a { color: red } b { margin: 0 }")

    tokens should contain("b" -> Tag)
    tokens should contain("margin" -> Property)
  }

  // ---------------------------------------------------------------------------------------
  // HTML
  // ---------------------------------------------------------------------------------------

  "HTML" should "colour tags, attributes and entities" in {
    val tokens = lex("html", """<!-- note --><p class="lead" hidden>Tom &amp; Jerry</p>""")

    tokens should contain("<!-- note -->" -> Comment)
    tokens should contain("p" -> Tag)
    tokens should contain("class" -> Attribute)
    tokens should contain("\"lead\"" -> String)
    tokens should contain("hidden" -> Attribute)
    tokens should contain("&amp;" -> Escape)
    tokens.map(_._1) should not contain "Tom "
  }

  it should "read a script as JavaScript and a style as CSS" in {
    val text =
      """<script type="module">
        |function go() { return "</p>" }
        |</script>
        |<style>p { color: red }</style>
        |<p>after</p>""".stripMargin
    val tokens = lex("html", text)

    tokens should contain("function" -> Keyword)
    tokens should contain("go" -> Function)
    // Only `</script` ends a script, exactly as in a browser.
    tokens should contain("\"</p>\"" -> String)
    tokens should contain("color" -> Property)
    tokens should contain("red" -> Constant)
    kinds("html", text, "p").count(_ == Tag) shouldBe 3
  }

  it should "leave the script at its closing tag even inside a string" in {
    lex("html", """<script>let s = "</script><b>x</b>""") should contain("b" -> Tag)
  }

  // ---------------------------------------------------------------------------------------
  // Shell
  // ---------------------------------------------------------------------------------------

  "Shell" should "colour commands, variables and options" in {
    val text =
      """# install
        |export PATH="$HOME/bin:$PATH"   # comment
        |if [ -f "$file" ]; then rm -rf ./tmp; fi""".stripMargin
    val tokens = lex("bash", text)

    tokens should contain("# install" -> Comment)
    tokens should contain("export" -> Function)
    tokens should contain("PATH" -> Variable)
    tokens should contain("$HOME" -> Variable)
    tokens should contain("# comment" -> Comment)
    tokens should contain("if" -> Keyword)
    tokens should contain("-f" -> Attribute)
    tokens should contain("-rf" -> Attribute)
    tokens should contain("fi" -> Keyword)
  }

  it should "not start a comment inside a word or a variable" in {
    val tokens = lex("sh", "echo $# a#b")

    tokens should contain("$#" -> Variable)
    tokens.filter(_._2 == Comment) shouldBe empty
  }

  it should "carry a quoted string across lines" in {
    kinds("sh", "echo \"one\nif two\"", "if two\"") shouldBe Vector(String)
  }

  // ---------------------------------------------------------------------------------------
  // Markdown
  // ---------------------------------------------------------------------------------------

  "Markdown" should "colour what a reader of the source sees" in {
    val text =
      """# Title
        |Some *emph*, **strong**, `code` and [a link](https://x.test).
        |- item
        |snake_case_name stays plain""".stripMargin
    val tokens = lex("markdown", text)

    tokens should contain("# Title" -> Heading)
    tokens should contain("*emph*" -> Emphasis)
    tokens should contain("**strong**" -> Strong)
    tokens should contain("`code`" -> Code)
    tokens should contain("[a link](https://x.test)" -> Link)
    tokens should contain("-" -> Operator)
    tokens.filter(_._2 == Emphasis) shouldBe Vector("*emph*" -> Emphasis)
  }

  it should "colour a fence in the language it names" in {
    val text   = "```scala\nval x = \"*not emphasis*\"\n```\n*after*"
    val tokens = lex("md", text)

    tokens should contain("scala" -> Meta)
    tokens should contain("val" -> Keyword)
    tokens should contain("\"*not emphasis*\"" -> String)
    tokens.filter(_._2 == Emphasis) shouldBe Vector("*after*" -> Emphasis)
    tokens.count(_ == ("```" -> Punctuation)) shouldBe 2
  }

  it should "keep an unknown language's fence in one colour" in {
    val text = "```cobol\nMOVE *A* TO B\n```\n*after*"

    lex("md", text) should contain("MOVE *A* TO B" -> Code)
    lex("md", text) should contain("*after*" -> Emphasis)
  }

  it should "close a fence only with a line of at least as many markers" in {
    // CommonMark §4.5: four backticks are not closed by three, and a fence never closes mid-line.
    val text   = "````js\nlet a = 1 ```\n```\nlet b = 2\n````\n*after*"
    val tokens = lex("md", text)

    tokens.count(_ == ("let" -> Keyword)) shouldBe 2
    tokens should contain("*after*" -> Emphasis)
  }

  it should "leave the embedded language when the fence closes inside an open string" in {
    val text = "```js\nlet s = `open\n```\n*after*"

    lex("md", text) should contain("*after*" -> Emphasis)
  }

  // ---------------------------------------------------------------------------------------
  // Registry
  // ---------------------------------------------------------------------------------------

  "The registry" should "find a language by any of its names, in any case" in {
    val languages = HighlightLanguages.standard

    languages.grammarFor("Scala") shouldBe languages.grammarFor("scala")
    languages.grammarFor("TSX") shouldBe languages.grammarFor("typescript")
    languages.grammarFor("cobol") shouldBe None
  }

  "A grammar" should "refuse a group count that does not match its kinds" in {
    an[IllegalArgumentException] should be thrownBy
      Grammar("broken")(state("root")(Rule.groups("(a)(b)", Some(Keyword))))
  }

  it should "refuse a push to a state it does not have" in {
    an[IllegalArgumentException] should be thrownBy
      Grammar("broken")(state("root")(Rule.token("a", Keyword).push("nowhere")))
  }
}
