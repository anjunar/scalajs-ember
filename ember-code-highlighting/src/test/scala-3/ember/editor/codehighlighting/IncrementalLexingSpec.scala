package ember.editor.codehighlighting

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

/** Relexing reuses what it may and nothing more (X02), and the service around it.
  *
  * The claim is exact: after any edit, the incremental result is '''the same''' as lexing the new
  * text from scratch -- tokens and line states. The generated edits below throw the characters that
  * change state at every grammar: quotes, comment openers, braces, `${`, backticks, line breaks.
  */
final class IncrementalLexingSpec extends AnyFlatSpec with Matchers {

  private val samples = Vector(
    "scala"      -> "object A:\n  /* c */ val s = s\"x ${ y } z\"\n  def f = \"\"\"a\nb\"\"\"\n",
    "javascript" -> "const t = `a ${ {b: 1} } c`\n// x\nlet r = /a/g / 2\n",
    "typescript" -> "interface P { x: number }\n/* multi\nline */ type Q = P\n",
    "json"       -> "{\n  \"a\": [1, 2, {\"b\": \"c\\n\"}],\n  \"d\": null\n}\n",
    "css"        -> "/* a */\n.b > c:hover { color: #fff; }\n@media x { d { margin: 0 } }\n",
    "html"  -> "<p class=\"a\">b &amp; c</p>\n<script>\nlet x = '</p>'\n</script>\n<!--\nx -->\n",
    "shell" -> "# c\nexport A=\"$B ${C}\"\nif [ -f x ]; then echo $(ls); fi\n",
    "markdown" -> "# T\n```js\nx\n```\n*a* `b` [c](d)\n- e\n"
  )

  private val alphabet = "ab1 \n\"'`/*{}$#<>!-_()[]:;=\\~&.".toVector

  private def grammar(language: String): Grammar =
    HighlightLanguages.standard.grammarFor(language).getOrElse(fail(language))

  private def edit(text: String, random: Random): String =
    val at = random.nextInt(text.length + 1)
    if random.nextBoolean() && text.nonEmpty then
      val end = math.min(text.length, at + random.nextInt(4) + 1)
      text.substring(0, math.min(at, text.length)) + text.substring(end)
    else
      val inserted =
        (0 until random.nextInt(3) + 1).map(_ => alphabet(random.nextInt(alphabet.length))).mkString
      text.substring(0, at) + inserted + text.substring(at)

  "Relexing" should "give exactly what a full lex gives, after every edit" in {
    val random = new Random(20260915)

    samples.foreach { (language, sample) =>
      var lexed = LexedText.lex(grammar(language), sample)
      (1 to 300).foreach { round =>
        val next = edit(lexed.text, random)
        lexed = lexed.update(next)
        val full = LexedText.lex(grammar(language), next)

        withClue(s"$language, round $round, text ${next.replace("\n", "\\n")}: ") {
          lexed.tokens shouldBe full.tokens
          lexed.lines.map(_.exit) shouldBe full.lines.map(_.exit)
        }
      }
    }
  }

  it should "produce ordered tokens inside the text that never cross a line break" in {
    val random = new Random(7)

    samples.foreach { (language, _) =>
      (1 to 50).foreach { _ =>
        val text =
          (0 until random.nextInt(200)).map(_ => alphabet(random.nextInt(alphabet.length))).mkString
        val tokens = LexedText.lex(grammar(language), text).tokens

        tokens.foreach { token =>
          token.start should be >= 0
          token.end should be <= text.length
          token.end should be > token.start
          text.substring(token.start, token.end) should not include "\n"
        }
        tokens.zip(tokens.drop(1)).foreach((left, right) => left.end should be <= right.start)
      }
    }
  }

  it should "lex one line for an edit inside a line" in {
    val text  = "val x = 1\n" * 1000
    val lexed = LexedText.lex(grammar("scala"), text)
    val at    = text.indexOf("1", 500 * 10)

    lexed.update(text.substring(0, at) + "2" + text.substring(at)).relexed shouldBe 1
  }

  it should "lex everything below an opened comment, and only that" in {
    val text   = "val x = 1\n" * 100
    val lexed  = LexedText.lex(grammar("scala"), text)
    val opened = lexed.update("/*" + text.substring(0, 50 * 10) + "*/" + text.substring(50 * 10))

    // The first line through the one that closes it -- lines 0 to 50 -- and not one more: line 51
    // starts outside the comment again, exactly as it did before.
    opened.relexed shouldBe 51
  }

  "The local highlighter" should "skip a block without a language or with an unknown one" in {
    val highlighter = new LocalHighlighter()
    val block       = NodeId("c")

    highlighter.compute(HighlightRequest(block, Revision(1), None, "x")).skipped shouldBe
      Some(HighlightSkip.NoLanguage)
    highlighter.compute(HighlightRequest(block, Revision(1), Some("cobol"), "x")).skipped shouldBe
      Some(HighlightSkip.UnknownLanguage("cobol"))
  }

  it should "leave a block past its limit uncoloured" in {
    val highlighter = new LocalHighlighter(limits = HighlightLimits(maxChars = 10))

    highlighter
      .compute(HighlightRequest(NodeId("c"), Revision(1), Some("scala"), "val x = 12345"))
      .skipped shouldBe Some(HighlightSkip.TooLarge(13, 10))
  }

  it should "leave an overlong line uncoloured and the lines after it correct" in {
    val highlighter = new LocalHighlighter(limits = HighlightLimits(maxLineChars = 20))
    val text        = "val a = 1\n" + ("x" * 50) + "\nval b = 2"
    val result      =
      highlighter.compute(HighlightRequest(NodeId("c"), Revision(1), Some("scala"), text))

    result.tokens.map(token => text.substring(token.start, token.end)).count(_ == "val") shouldBe 2
    result.tokens.exists(found => token(text, found).startsWith("x")) shouldBe false
  }

  it should "relex incrementally per block and start over after forget" in {
    val highlighter = new LocalHighlighter()
    val block       = NodeId("c")
    val text        = "val x = 1\n" * 50

    highlighter.compute(HighlightRequest(block, Revision(1), Some("scala"), text))
    highlighter.compute(HighlightRequest(block, Revision(2), Some("scala"), text + "val y = 2"))
    highlighter.lastRelexed(block) shouldBe Some(1)

    highlighter.forget(block)
    highlighter.lastRelexed(block) shouldBe None
  }

  "Static output" should "keep the text exactly and escape it" in {
    samples.foreach { (language, sample) =>
      val html     = StaticHighlight.html(sample, Some(language))
      val restored = html
        .replaceAll("<[^>]*>", "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

      restored shouldBe sample
      html should include("ember-tok-")
    }
  }

  it should "escape an unknown language without colouring it" in {
    StaticHighlight.html("<b> & c", Some("cobol")) shouldBe "&lt;b&gt; &amp; c"
  }

  private def token(text: String, token: Token): String = text.substring(token.start, token.end)
}
