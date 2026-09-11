package ember.editor.markdown

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The inline parser, case by case (P18).
  *
  * The same division of labour as between [[MarkdownBlockSpec]] and
  * [[CommonMarkConformanceSpec]]: the conformance suite says '''how much''', this one says
  * '''what''', and it asserts the tree rather than rendered HTML -- because the tree is what
  * the document adapter consumes.
  */
final class MarkdownInlineSpec extends AnyFlatSpec with Matchers {

  /** The inline content of the first block, as a bracketed line. */
  private def inlines(source: String): String =
    val document = Markdown.parseSyntax(source) match
      case Right(result) => result.document
      case Left(error)   => fail(s"abgewiesen: ${error.render}")

    document.children.headOption match
      case Some(MarkdownBlock.Paragraph(_, _, content)) => show(content)
      case Some(MarkdownBlock.Heading(_, _, _, _, content)) => show(content)
      case other => fail(s"kein Absatz: $other")

  private def show(content: Vector[MarkdownInline]): String =
    content.map {
      case MarkdownInline.Text(_, _, value)         => value
      case MarkdownInline.Code(_, _, literal)       => s"code($literal)"
      case MarkdownInline.SoftBreak(_, _)           => "~"
      case MarkdownInline.HardBreak(_, _)           => "|"
      case MarkdownInline.HtmlInline(_, _, literal) => s"html($literal)"
      case MarkdownInline.Emphasis(_, _, kids)      => s"em(${show(kids)})"
      case MarkdownInline.Strong(_, _, kids)        => s"strong(${show(kids)})"
      case MarkdownInline.Link(_, _, target, title, kids) =>
        s"link($target${title.map(t => s",$t").getOrElse("")}){${show(kids)}}"
      case MarkdownInline.Image(_, _, target, title, kids) =>
        s"img($target${title.map(t => s",$t").getOrElse("")}){${show(kids)}}"
    }.mkString

  // ---------------------------------------------------------------------------------------
  // Emphasis
  // ---------------------------------------------------------------------------------------

  "Emphasis" should "come from a single delimiter" in {
    inlines("*eins* und _zwei_\n") shouldBe "em(eins) und em(zwei)"
  }

  it should "be strong with two" in {
    inlines("**eins** und __zwei__\n") shouldBe "strong(eins) und strong(zwei)"
  }

  it should "nest" in {
    inlines("***alles***\n") shouldBe "em(strong(alles))"
  }

  it should "leave a word with underscores alone" in {
    // Der Grund, warum `_` strengere Flanking-Regeln hat als `*`: ohne sie waere jeder
    // Bezeichner in Schlangenschreibweise halb kursiv.
    inlines("snake_case_name\n") shouldBe "snake_case_name"
  }

  it should "still work with asterisks inside a word" in {
    inlines("foo*bar*baz\n") shouldBe "fooem(bar)baz"
  }

  it should "not open on a delimiter followed by whitespace" in {
    inlines("a * b * c\n") shouldBe "a * b * c"
  }

  it should "follow the rule of three" in {
    // §18.2 verlangt Delimiterregeln statt globaler Ersetzungen. Das hier ist der Fall, an dem
    // eine Regex-Loesung sichtbar falsch liegt.
    inlines("*foo**bar**baz*\n") shouldBe "em(foostrong(bar)baz)"
  }

  it should "leave an unmatched delimiter as text" in {
    inlines("*nicht geschlossen\n") shouldBe "*nicht geschlossen"
  }

  // ---------------------------------------------------------------------------------------
  // Code-Spans
  // ---------------------------------------------------------------------------------------

  "A code span" should "close on a run of the same length" in {
    inlines("`code`\n") shouldBe "code(code)"
  }

  it should "let a longer fence hold a backtick" in {
    // §18.2: "Backtick-Laenge und Whitespace-Regeln beachten."
    inlines("`` foo ` bar ``\n") shouldBe "code(foo ` bar)"
  }

  it should "strip exactly one space at each end" in {
    inlines("` `` `\n") shouldBe "code(``)"
    inlines("`  ``  `\n") shouldBe "code( `` )"
  }

  it should "keep a space when only one side has it" in {
    inlines("` a`\n") shouldBe "code( a)"
  }

  it should "be text when nothing closes it" in {
    inlines("`nicht geschlossen\n") shouldBe "`nicht geschlossen"
  }

  it should "beat emphasis" in {
    // Vorrang, wie ihn die Spezifikation festlegt -- ein `*` im Code ist Code.
    inlines("`*kein Kursiv*`\n") shouldBe "code(*kein Kursiv*)"
  }

  // ---------------------------------------------------------------------------------------
  // Links
  // ---------------------------------------------------------------------------------------

  "A link" should "carry destination and title" in {
    inlines("""[Text](/ziel "Titel")""" + "\n") shouldBe "link(/ziel,Titel){Text}"
  }

  it should "work without a title" in {
    inlines("[Text](/ziel)\n") shouldBe "link(/ziel){Text}"
  }

  it should "take a destination in pointy brackets" in {
    inlines("[Text](</mit leerzeichen>)\n") shouldBe "link(/mit%20leerzeichen){Text}"
  }

  it should "hold markup in its label" in {
    inlines("[*kursiv*](/ziel)\n") shouldBe "link(/ziel){em(kursiv)}"
  }

  it should "not contain another link" in {
    // Ein `<a>` in einem `<a>` gibt es in HTML nicht, und die Regel greift beim Schliessen des
    // inneren, nicht beim Oeffnen des aeusseren.
    inlines("[aussen [innen](/i) rest](/a)\n") shouldBe "[aussen link(/i){innen} rest](/a)"
  }

  it should "resolve a reference" in {
    inlines("[Text][ziel]\n\n[ziel]: /aufgeloest \"T\"\n") shouldBe "link(/aufgeloest,T){Text}"
  }

  it should "resolve a collapsed reference" in {
    inlines("[ziel][]\n\n[ziel]: /aufgeloest\n") shouldBe "link(/aufgeloest){ziel}"
  }

  it should "resolve a shortcut reference" in {
    inlines("[ziel]\n\n[ziel]: /aufgeloest\n") shouldBe "link(/aufgeloest){ziel}"
  }

  it should "match a label regardless of case and inner spacing" in {
    // §18.2 laesst Referenzlabels kanonisieren, und CommonMark sagt wie: trimmen, inneren
    // Leerraum zusammenziehen, Fall falten.
    inlines("[ZIEL  Zwei]\n\n[ziel zwei]: /gefunden\n") shouldBe "link(/gefunden){ZIEL  Zwei}"
  }

  it should "stay text when the reference is undefined" in {
    inlines("[Text][fehlt]\n") shouldBe "[Text][fehlt]"
  }

  it should "keep the first of two definitions for the same label" in {
    inlines("[a]\n\n[a]: /erste\n[a]: /zweite\n") shouldBe "link(/erste){a}"
  }

  it should "percent-encode what a URL may not carry literally" in {
    inlines("[t](/pfad?a=1&b=<zwei>)\n") shouldBe "link(/pfad?a=1&b=%3Czwei%3E){t}"
  }

  it should "resolve an entity in its destination" in {
    inlines("[t](/b&auml;r)\n") shouldBe "link(/b%C3%A4r){t}"
  }

  // ---------------------------------------------------------------------------------------
  // Autolinks
  // ---------------------------------------------------------------------------------------

  "An autolink" should "become a link with its own text" in {
    inlines("<https://example.com/x>\n") shouldBe "link(https://example.com/x){https://example.com/x}"
  }

  it should "prefix an email with mailto" in {
    inlines("<jemand@example.com>\n") shouldBe "link(mailto:jemand@example.com){jemand@example.com}"
  }

  it should "not be one with a space inside" in {
    // Und auch kein Inline-HTML: `<https://…>` ist kein Tagname. Was weder das eine noch das
    // andere ist, bleibt Text -- das `<` wird Zeichen fuer Zeichen durchgereicht.
    inlines("<https://example.com/mit leerzeichen>\n") shouldBe
      "<https://example.com/mit leerzeichen>"
  }

  // ---------------------------------------------------------------------------------------
  // Bilder
  // ---------------------------------------------------------------------------------------

  "An image" should "carry destination, alt and title" in {
    inlines("""![Alt](/bild.png "Titel")""" + "\n") shouldBe "img(/bild.png,Titel){Alt}"
  }

  it should "keep its alt text as inline children" in {
    // CommonMark modelliert den Alt-Text als Kinder, weil er selbst Markup enthalten darf.
    // Was ein Attribut daraus macht, entscheidet der Adapter -- hier bleibt die Struktur.
    inlines("![*kursiv* alt](/b.png)\n") shouldBe "img(/b.png){em(kursiv) alt}"
  }

  it should "flatten to plain text on request" in {
    val document = Markdown.parseSyntax("![*a* b](/x)\n").map(_.document).getOrElse(fail("nope"))

    document.children.head match
      case MarkdownBlock.Paragraph(_, _, Vector(image: MarkdownInline.Image)) =>
        MarkdownInline.plainText(image.children) shouldBe "a b"
      case other => fail(s"kein Bild: $other")
  }

  it should "allow an empty alt text" in {
    inlines("![](/b.png)\n") shouldBe "img(/b.png){}"
  }

  // ---------------------------------------------------------------------------------------
  // Escapes und Entities
  // ---------------------------------------------------------------------------------------

  "A backslash escape" should "make a delimiter literal" in {
    inlines("\\*kein Kursiv\\*\n") shouldBe "*kein Kursiv*"
  }

  it should "not escape a letter" in {
    inlines("\\a\n") shouldBe "\\a"
  }

  it should "be a hard break before a newline" in {
    inlines("eins\\\nzwei\n") shouldBe "eins|zwei"
  }

  "A numeric character reference" should "be decoded, decimal and hexadecimal" in {
    inlines("&#35; &#x41;\n") shouldBe "# A"
  }

  it should "become the replacement character at zero" in {
    inlines("&#0;\n") shouldBe "\ufffd"
  }

  it should "become the replacement character beyond Unicode" in {
    inlines("&#x110000;\n") shouldBe "\ufffd"
  }

  "A named reference" should "be decoded when the table knows it" in {
    inlines("&auml; &szlig; &amp; &copy;\n") shouldBe "\u00e4 \u00df & \u00a9"
  }

  it should "stay text when it does not" in {
    // §18.1 verlangt, nur die getestete Teilmenge zu bewerben. Ein unbekannter Name bleibt
    // unveraendert stehen -- ein Round-Trip verliert ihn damit nicht.
    inlines("&Dcaron;\n") shouldBe "&Dcaron;"
  }

  it should "be decodable by an application that extends the table" in {
    val profile = MarkdownProfile.commonMarkSafe.copy(
      entities = EntityTable.common.and("Dcaron" -> "\u010e")
    )
    val document = Markdown.parseSyntax("&Dcaron;\n", profile).map(_.document).getOrElse(fail("nope"))

    document.children.head match
      case MarkdownBlock.Paragraph(_, _, content) =>
        MarkdownInline.plainText(content) shouldBe "\u010e"
      case other => fail(s"kein Absatz: $other")
  }

  it should "resolve nothing at all under the numeric-only table" in {
    val profile = MarkdownProfile.commonMarkSafe.copy(entities = EntityTable.numericOnly)
    val document = Markdown.parseSyntax("&amp; &#35;\n", profile).map(_.document).getOrElse(fail("nope"))

    document.children.head match
      case MarkdownBlock.Paragraph(_, _, content) =>
        MarkdownInline.plainText(content) shouldBe "&amp; #"
      case other => fail(s"kein Absatz: $other")
  }

  // ---------------------------------------------------------------------------------------
  // Umbrueche
  // ---------------------------------------------------------------------------------------

  "Two trailing spaces" should "make a hard break" in {
    inlines("eins  \nzwei\n") shouldBe "eins|zwei"
  }

  "One trailing space" should "leave a soft break" in {
    inlines("eins \nzwei\n") shouldBe "eins~zwei"
  }

  "A hard break" should "not survive at the end of a paragraph" in {
    inlines("eins  \n") shouldBe "eins"
  }

  // ---------------------------------------------------------------------------------------
  // Rohes Inline-HTML
  // ---------------------------------------------------------------------------------------

  "A raw inline tag" should "be kept as literal source" in {
    inlines("Text <b>fett</b>\n") shouldBe "Text html(<b>)fetthtml(</b>)"
  }

  it should "be what the safe profile promises to show as text" in {
    MarkdownProfile.commonMarkSafe.rawHtml shouldBe RawHtmlPolicy.AsText
  }

  // ---------------------------------------------------------------------------------------
  // Quellbereiche
  // ---------------------------------------------------------------------------------------

  "An inline span" should "point at the source it came from" in {
    val source = "Davor *kursiv* danach\n"
    val result = Markdown.parseSyntax(source).getOrElse(fail("nicht parsebar"))

    result.document.children.head match
      case MarkdownBlock.Paragraph(_, _, content) =>
        val emphasis = content.collectFirst { case value: MarkdownInline.Emphasis => value }
          .getOrElse(fail("kein em"))
        source.substring(emphasis.span.start, emphasis.span.end) shouldBe "kursiv"
      case other => fail(s"kein Absatz: $other")
  }

  it should "point through a block marker that is not in the content" in {
    // Der Fall, den eine naive Umrechnung verfehlt: der Inhalt des Zitats ist kein Ausschnitt
    // des Quelltexts, weil `> ` beim Blockparsen wegfaellt.
    val source = "> Davor *kursiv* danach\n"
    val result = Markdown.parseSyntax(source).getOrElse(fail("nicht parsebar"))

    val emphasis = result.document.children
      .collectFirst { case quote: MarkdownBlock.BlockQuote => quote }
      .flatMap(_.children.collectFirst { case p: MarkdownBlock.Paragraph => p })
      .flatMap(_.inlines.collectFirst { case value: MarkdownInline.Emphasis => value })
      .getOrElse(fail("kein em"))

    source.substring(emphasis.span.start, emphasis.span.end) shouldBe "kursiv"
  }

  // ---------------------------------------------------------------------------------------
  // Langsame Delimiterfaelle
  // ---------------------------------------------------------------------------------------

  "A pathological run of delimiters" should "stay inside the budget" in {
    // P18s Risikozeile: "langsame Delimiterfaelle". `openersBottom` ist die Antwort darauf --
    // ohne die vierzehn Untergrenzen waere das hier quadratisch.
    val source = "*a " * 2000 + "\n"

    Markdown.parseSyntax(source) should matchPattern { case Right(_) => }
  }

  it should "stay inside it for brackets too" in {
    Markdown.parseSyntax("[a " * 2000 + "\n") should matchPattern { case Right(_) => }
  }
}
