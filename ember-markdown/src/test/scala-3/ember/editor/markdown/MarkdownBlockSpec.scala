package ember.editor.markdown

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The block parser, case by case (P17).
  *
  * ==Why this suite exists next to the conformance suite==
  *
  * [[CommonMarkConformanceSpec]] measures a number. It says how much of the specification the parser
  * reproduces, and it catches a regression anywhere -- but when it fails it says "336 instead
  * of 337", and finding out what broke means reading a diff of the whole suite.
  *
  * This one names the cases P17 asks for and asserts the '''structure''' rather than rendered
  * HTML. When it fails it says which construct, and it says so about the tree the rest of the
  * editor will actually consume.
  */
final class MarkdownBlockSpec extends AnyFlatSpec with Matchers {

  private def parse(source: String): MarkdownDocument =
    Markdown.parseSyntax(source) match
      case Right(result) => result.document
      case Left(error)   => fail(s"abgewiesen: ${error.render}")

  /** The tree as one indented line per block -- the shape, without the noise of ids and spans. */
  private def outline(source: String): String =
    def describe(block: MarkdownBlock): String = block match
      case _: MarkdownDocument                        => "document"
      case MarkdownBlock.Paragraph(_, _, inlines) => s"""paragraph "${flatten(inlines)}""""
      case MarkdownBlock.Heading(_, _, level, style, inlines) =>
        s"""h$level ${style.toString.toLowerCase} "${flatten(inlines)}""""
      case MarkdownBlock.CodeBlock(_, _, literal, None) => s"""code indented "${escape(literal)}""""
      case MarkdownBlock.CodeBlock(_, _, literal, Some(fence)) =>
        s"""code ${fence.char}${fence.length} info="${fence.info}" "${escape(literal)}""""
      case MarkdownBlock.HtmlBlock(_, _, literal) => s"""html "${escape(literal)}""""
      case MarkdownBlock.ThematicBreak(_, _)      => "break"
      case MarkdownBlock.BlockQuote(_, _, _)      => "quote"
      case MarkdownBlock.MarkdownList(_, _, kind, tight, _) =>
        val shape = kind match
          case ListKind.Bullet(marker)             => s"bullet '$marker'"
          case ListKind.Ordered(start, delimiter)  => s"ordered $start'$delimiter'"
        s"list $shape ${if tight then "tight" else "loose"}"
      case MarkdownBlock.ListItem(_, _, _) => "item"

    def walk(block: MarkdownBlock, depth: Int): Vector[String] =
      s"${"  " * depth}${describe(block)}" +: block.children.flatMap(walk(_, depth + 1))

    walk(parse(source), 0).mkString("\n")

  private def escape(text: String): String = text.replace("\n", "\\n").replace("\t", "\\t")

  /** Inline content as one line. Structure gets brackets; a soft break stays a `\n`.
    *
    * This suite is about '''blocks''', so inline structure is shown only far enough to see that
    * it is there. `MarkdownInlineSpec` is where it gets looked at properly.
    */
  private def flatten(inlines: Vector[MarkdownInline]): String =
    escape(inlines.map {
      case MarkdownInline.Text(_, _, value)  => value
      case MarkdownInline.Code(_, _, value)  => s"`$value`"
      case MarkdownInline.SoftBreak(_, _)    => "\n"
      case MarkdownInline.HardBreak(_, _)    => "\\\\n"
      case MarkdownInline.HtmlInline(_, _, v) => v
      case MarkdownInline.Emphasis(_, _, kids) => s"<em>${flatten(kids)}</em>"
      case MarkdownInline.Strong(_, _, kids)   => s"<strong>${flatten(kids)}</strong>"
      case MarkdownInline.Link(_, _, target, _, kids)  => s"<a:$target>${flatten(kids)}</a>"
      case MarkdownInline.Image(_, _, target, _, kids) => s"<img:$target>${flatten(kids)}</img>"
    }.mkString)

  private def blocks(source: String): Vector[MarkdownBlock] = parse(source).children

  // ---------------------------------------------------------------------------------------
  // Absaetze
  // ---------------------------------------------------------------------------------------

  "A paragraph" should "keep its soft line breaks" in {
    // §18.2: "Paragraphen, Soft-/Hardbreaks -- Unterschied erhalten."
    parse("eins\nzwei\n").children.head match
      case MarkdownBlock.Paragraph(_, span, inlines) =>
        span shouldBe SourceSpan(0, 9)
        inlines should matchPattern {
          case Vector(
                MarkdownInline.Text(_, _, "eins"),
                MarkdownInline.SoftBreak(_, _),
                MarkdownInline.Text(_, _, "zwei")
              ) =>
        }
      case other => fail(s"kein Absatz: $other")
  }

  it should "be separated from the next by a blank line" in {
    outline("eins\n\nzwei\n") shouldBe
      """document
        |  paragraph "eins"
        |  paragraph "zwei"""".stripMargin
  }

  it should "lose the indentation of each of its lines" in {
    outline("  eins\n   zwei\n") shouldBe
      """document
        |  paragraph "eins\nzwei"""".stripMargin
  }

  it should "disappear when it held nothing but link reference definitions" in {
    // Eine Definition ist kein Block, sondern das Praefix eines Absatzes. Bleibt danach nichts
    // uebrig, gibt es auch keinen Absatz -- ein leerer waere ein Knoten, den der Quelltext
    // nicht meint.
    outline("[foo]: /url\n") shouldBe "document"
  }

  it should "keep what stands after the definition" in {
    outline("[foo]: /url\nText\n") shouldBe
      """document
        |  paragraph "Text"""".stripMargin
  }

  it should "keep the definition under a profile that resolves no inlines" in {
    val document = Markdown
      .parseSyntax("[foo]: /url\n", MarkdownProfile.blocksOnly)
      .map(_.document)
      .getOrElse(fail("nicht parsebar"))

    document.children.head should matchPattern {
      case MarkdownBlock.Paragraph(_, _, Vector(MarkdownInline.Text(_, _, "[foo]: /url"))) =>
    }
  }

  // ---------------------------------------------------------------------------------------
  // Ueberschriften
  // ---------------------------------------------------------------------------------------

  "An ATX heading" should "carry its level" in {
    outline("# eins\n### drei\n###### sechs\n") shouldBe
      """document
        |  h1 atx "eins"
        |  h3 atx "drei"
        |  h6 atx "sechs"""".stripMargin
  }

  it should "drop a closing sequence" in {
    outline("# Titel ###\n") shouldBe
      """document
        |  h1 atx "Titel"""".stripMargin
  }

  it should "not be one with seven hashes" in {
    outline("####### zu viel\n") shouldBe
      """document
        |  paragraph "####### zu viel"""".stripMargin
  }

  it should "not be one without a space after the hashes" in {
    outline("#kein Heading\n") shouldBe
      """document
        |  paragraph "#kein Heading"""".stripMargin
  }

  it should "be empty when there is nothing after the hashes" in {
    outline("#\n") shouldBe
      """document
        |  h1 atx """"".stripMargin
  }

  "A Setext heading" should "take the whole paragraph above it" in {
    outline("eins\nzwei\n===\n") shouldBe
      """document
        |  h1 setext "eins\nzwei"""".stripMargin
  }

  it should "be level two under dashes" in {
    outline("Titel\n---\n") shouldBe
      """document
        |  h2 setext "Titel"""".stripMargin
  }

  it should "be a thematic break with no paragraph above it" in {
    // Dieselbe Zeile, zwei Bedeutungen -- und genau deshalb steht der Setext-Versuch in der
    // Startreihenfolge '''vor''' dem Trenner.
    outline("---\n") shouldBe
      """document
        |  break""".stripMargin
  }

  // ---------------------------------------------------------------------------------------
  // Zitate
  // ---------------------------------------------------------------------------------------

  "A block quote" should "hold several blocks" in {
    outline("> eins\n>\n> zwei\n") shouldBe
      """document
        |  quote
        |    paragraph "eins"
        |    paragraph "zwei"""".stripMargin
  }

  it should "nest" in {
    outline("> > tief\n") shouldBe
      """document
        |  quote
        |    quote
        |      paragraph "tief"""".stripMargin
  }

  it should "continue lazily" in {
    // Die Regel, an der ein selbstgebauter Parser scheitert: Zeile zwei hat kein `>` und
    // gehoert trotzdem in das Zitat, weil ein Absatz darin offen ist.
    outline("> eins\nzwei\n") shouldBe
      """document
        |  quote
        |    paragraph "eins\nzwei"""".stripMargin
  }

  it should "end where the lazy continuation cannot apply" in {
    // Eine Leerzeile schliesst den Absatz; danach gibt es nichts mehr, was faul fortsetzen
    // koennte, und `zwei` steht ausserhalb.
    outline("> eins\n\nzwei\n") shouldBe
      """document
        |  quote
        |    paragraph "eins"
        |  paragraph "zwei"""".stripMargin
  }

  // ---------------------------------------------------------------------------------------
  // Listen
  // ---------------------------------------------------------------------------------------

  "A list" should "be tight without blank lines between its items" in {
    outline("- eins\n- zwei\n") shouldBe
      """document
        |  list bullet '-' tight
        |    item
        |      paragraph "eins"
        |    item
        |      paragraph "zwei"""".stripMargin
  }

  it should "be loose with one" in {
    // §18.2 verlangt, den Unterschied zu erhalten. Er ist sichtbar: eine weite Liste rendert
    // ihre Punkte mit `<p>`, eine enge ohne.
    outline("- eins\n\n- zwei\n") shouldBe
      """document
        |  list bullet '-' loose
        |    item
        |      paragraph "eins"
        |    item
        |      paragraph "zwei"""".stripMargin
  }

  it should "be loose when one item holds two blocks apart" in {
    outline("- eins\n\n  zwei\n") shouldBe
      """document
        |  list bullet '-' loose
        |    item
        |      paragraph "eins"
        |      paragraph "zwei"""".stripMargin
  }

  it should "start a new one when the marker changes" in {
    outline("- eins\n* zwei\n") shouldBe
      """document
        |  list bullet '-' tight
        |    item
        |      paragraph "eins"
        |  list bullet '*' tight
        |    item
        |      paragraph "zwei"""".stripMargin
  }

  it should "keep the start number of an ordered list" in {
    outline("5. fuenf\n6. sechs\n") shouldBe
      """document
        |  list ordered 5'.' tight
        |    item
        |      paragraph "fuenf"
        |    item
        |      paragraph "sechs"""".stripMargin
  }

  it should "not interrupt a paragraph unless it starts at one" in {
    outline("Ein Satz\n2024. war ein Jahr\n") shouldBe
      """document
        |  paragraph "Ein Satz\n2024. war ein Jahr"""".stripMargin
  }

  it should "nest" in {
    outline("- aussen\n  - innen\n") shouldBe
      """document
        |  list bullet '-' tight
        |    item
        |      paragraph "aussen"
        |      list bullet '-' tight
        |        item
        |          paragraph "innen"""".stripMargin
  }

  "An item with many spaces after its marker" should "hold a code block" in {
    // Die Padding-Arithmetik in einer Zeile: ab fuenf Leerzeichen zaehlt genau eines zum
    // Marker, der Rest ist Einrueckung '''im''' Punkt -- und vier davon sind Code.
    outline("-     Code\n") shouldBe
      """document
        |  list bullet '-' tight
        |    item
        |      code indented "Code\n"""".stripMargin
  }

  "An empty item" should "be an item" in {
    outline("*\n") shouldBe
      """document
        |  list bullet '*' tight
        |    item""".stripMargin
  }

  // ---------------------------------------------------------------------------------------
  // Code
  // ---------------------------------------------------------------------------------------

  "A fenced code block" should "keep its content verbatim, blank lines included" in {
    // §18.2: "Inhalt einschliesslich innerer Leerzeilen erhalten." Das ist der Vertrag, auf
    // dem P15s Codeblock aufsetzt.
    outline("```\neins\n\nzwei\n```\n") shouldBe
      """document
        |  code `3 info="" "eins\n\nzwei\n"""".stripMargin
  }

  it should "carry its info string" in {
    outline("``` scala  \nval x = 1\n```\n") shouldBe
      """document
        |  code `3 info="scala" "val x = 1\n"""".stripMargin
  }

  it should "unescape a backslash in the info string" in {
    // Der einzige Ort, an dem der Blockparser entescapt -- der Info-String wird beim
    // Abschliessen des Zauns entschieden, nicht beim Inline-Parsen.
    parse("```a\\+b\ncode\n```\n").children.head match
      case MarkdownBlock.CodeBlock(_, _, _, Some(fence)) => fence.info shouldBe "a+b"
      case other                                         => fail(s"kein Zaun: $other")
  }

  it should "run to the end of the input when it is never closed" in {
    outline("```\noffen\n") shouldBe
      """document
        |  code `3 info="" "offen\n"""".stripMargin
  }

  it should "need a closing fence at least as long as its opening one" in {
    outline("````\n```\nimmer noch drin\n````\n") shouldBe
      """document
        |  code `4 info="" "```\nimmer noch drin\n"""".stripMargin
  }

  it should "be closable with tildes only by tildes" in {
    outline("~~~\n```\n~~~\n") shouldBe
      """document
        |  code ~3 info="" "```\n"""".stripMargin
  }

  "An indented code block" should "drop four spaces from each line" in {
    outline("    eins\n    zwei\n") shouldBe
      """document
        |  code indented "eins\nzwei\n"""".stripMargin
  }

  it should "keep inner blank lines but not trailing ones" in {
    outline("    eins\n\n    zwei\n\n") shouldBe
      """document
        |  code indented "eins\n\nzwei\n"""".stripMargin
  }

  it should "not interrupt a paragraph" in {
    outline("Ein Satz\n    kein Code\n") shouldBe
      """document
        |  paragraph "Ein Satz\nkein Code"""".stripMargin
  }

  // ---------------------------------------------------------------------------------------
  // Trenner, Einrueckung, Zeilenenden
  // ---------------------------------------------------------------------------------------

  "A thematic break" should "be its own block, not a run of characters" in {
    outline("***\n___\n- - -\n") shouldBe
      """document
        |  break
        |  break
        |  break""".stripMargin
  }

  it should "not be one at four spaces of indentation" in {
    outline("    ***\n") shouldBe
      """document
        |  code indented "***\n"""".stripMargin
  }

  "Up to three spaces" should "not change what a line means" in {
    outline("   # Titel\n   - Punkt\n") shouldBe
      """document
        |  h1 atx "Titel"
        |  list bullet '-' tight
        |    item
        |      paragraph "Punkt"""".stripMargin
  }

  "A tab" should "count as four columns, not one character" in {
    // Der Grund, warum der Parser Spalte und Offset getrennt fuehrt: `\tCode` ist eingerueckter
    // Code, obwohl es ein einziges Zeichen ist.
    outline("\tCode\n") shouldBe
      """document
        |  code indented "Code\n"""".stripMargin
  }

  it should "be expanded only as far as it is consumed" in {
    outline("  \tCode\n") shouldBe
      """document
        |  code indented "Code\n"""".stripMargin
  }

  "CRLF" should "parse like LF" in {
    outline("# Titel\r\n\r\nAbsatz\r\n") shouldBe outline("# Titel\n\nAbsatz\n")
  }

  "A lone CR" should "parse like LF too" in {
    outline("# Titel\r\rAbsatz\r") shouldBe outline("# Titel\n\nAbsatz\n")
  }

  "A source without a trailing newline" should "parse like one with it" in {
    outline("Absatz") shouldBe outline("Absatz\n")
  }

  "A NUL" should "become a replacement character" in {
    // Wie in der Vorlage, und aus demselben Grund: ein NUL, das bis in ein DOM durchkommt, ist
    // eine Gefahr. U+FFFD hat dieselbe UTF-16-Laenge, also verschiebt es keinen Offset.
    parse("a\u0000b\n").children.head match
      case MarkdownBlock.Paragraph(_, span, inlines) =>
        MarkdownInline.plainText(inlines) shouldBe "a\ufffdb"
        span shouldBe SourceSpan(0, 3)
      case other => fail(s"kein Absatz: $other")
  }

  // ---------------------------------------------------------------------------------------
  // Rohes HTML
  // ---------------------------------------------------------------------------------------

  "A raw HTML block" should "be its own block, kept as text" in {
    // §18.1: erhalten statt ausgefuehrt. Der Parser erkennt ihn, weil CommonMark ihn erkennt --
    // seine Blockgrenzen sind andere als die eines Absatzes, und ihn zu ignorieren bekaeme
    // auch die Struktur '''darum herum''' falsch.
    outline("<div>\nInhalt\n</div>\n") shouldBe
      """document
        |  html "<div>\nInhalt\n</div>"""".stripMargin
  }

  it should "be what the safe profile promises to show as text" in {
    MarkdownProfile.commonMarkSafe.rawHtml shouldBe RawHtmlPolicy.AsText
  }

  // ---------------------------------------------------------------------------------------
  // Quellbereiche
  // ---------------------------------------------------------------------------------------

  "A span" should "cover the block in the source" in {
    val source   = "# Titel\n\nEin Absatz.\n"
    val document = parse(source)

    val heading   = document.children.head
    val paragraph = document.children(1)

    source.substring(heading.span.start, heading.span.end) shouldBe "# Titel"
    source.substring(paragraph.span.start, paragraph.span.end) shouldBe "Ein Absatz."
  }

  it should "start at the content of a quote, not at the marker's line start" in {
    val source   = "> Zitat\n"
    val document = parse(source)
    val quote    = document.children.head

    source.substring(quote.span.start, quote.span.end) shouldBe "> Zitat"
  }

  it should "cover every line of a multi-line block" in {
    val source   = "```\neins\nzwei\n```\n"
    val document = parse(source)

    source.substring(document.children.head.span.start, document.children.head.span.end) shouldBe
      "```\neins\nzwei\n```"
  }

  // ---------------------------------------------------------------------------------------
  // SourceMap
  // ---------------------------------------------------------------------------------------

  "The source map" should "find the innermost block at an offset" in {
    val source = "> - eins\n"
    val result = Markdown.parseSyntax(source).getOrElse(fail("nicht parsebar"))

    result.sourceMap.blockAt(result.document, source.indexOf("eins")) should matchPattern {
      case Some(MarkdownBlock.Paragraph(_, _, Vector(MarkdownInline.Text(_, _, "eins")))) =>
    }
  }

  it should "answer with the document outside every child" in {
    val result = Markdown.parseSyntax("eins\n\nzwei\n").getOrElse(fail("nicht parsebar"))

    // Offset 4 ist das `\n` nach `eins` -- Teil keines Absatzes, aber im Dokument.
    result.sourceMap.blockAt(result.document, 5) should matchPattern {
      case Some(_: MarkdownDocument) =>
    }
  }

  it should "answer with nothing outside the source" in {
    val result = Markdown.parseSyntax("eins\n").getOrElse(fail("nicht parsebar"))

    result.sourceMap.blockAt(result.document, 999) shouldBe None
  }

  it should "give the span of a block by its id" in {
    val result   = Markdown.parseSyntax("# Titel\n").getOrElse(fail("nicht parsebar"))
    val heading  = result.document.children.head

    result.sourceMap.spanOf(heading.id) shouldBe Some(heading.span)
  }

  it should "name the one-based line of an offset" in {
    val source = "eins\nzwei\ndrei\n"
    val result = Markdown.parseSyntax(source).getOrElse(fail("nicht parsebar"))

    result.sourceMap.lineAt(0) shouldBe 1
    result.sourceMap.lineAt(source.indexOf("zwei")) shouldBe 2
    result.sourceMap.lineAt(source.indexOf("drei")) shouldBe 3
  }

  // ---------------------------------------------------------------------------------------
  // Grenzen
  // ---------------------------------------------------------------------------------------

  /** Generous everywhere. Each test below tightens exactly the one bound it is about.
    *
    * Sharing one tight profile does not work, and the way it fails is instructive: the bounds
    * are checked in a fixed order, so a small `maxSourceChars` fires before the parser ever
    * gets deep enough to reach `maxDepth`, and the test then proves the wrong thing while
    * still being green about it.
    */
  private val roomy = ParseLimits(
    maxSourceChars = 1_000_000,
    maxLines = 100_000,
    maxDepth = 1_000,
    maxBlocks = 100_000,
    maxSteps = 10_000_000
  )

  private def under(limits: ParseLimits): MarkdownProfile =
    MarkdownProfile.commonMarkSafe.copy(limits = limits)

  "The size limit" should "refuse before parsing" in {
    Markdown.parseSyntax("x" * 100, under(roomy.copy(maxSourceChars = 40))) should matchPattern {
      case Left(ParseError.LimitExceeded("maxSourceChars", 40, 100)) =>
    }
  }

  "The line limit" should "refuse a source with too many lines" in {
    Markdown.parseSyntax("a\n" * 10, under(roomy.copy(maxLines = 5))) should matchPattern {
      case Left(ParseError.LimitExceeded("maxLines", 5, 10)) =>
    }
  }

  "The depth limit" should "refuse deep nesting instead of overflowing the stack" in {
    // Das ist der Fall, der ohne Grenze abstuerzt statt fehlzuschlagen: der Baum wird rekursiv
    // aufgebaut, und die Rekursionstiefe ist die Verschachtelungstiefe der Eingabe.
    Markdown.parseSyntax("> " * 20 + "tief\n", under(roomy.copy(maxDepth = 4))) should
      matchPattern { case Left(ParseError.LimitExceeded("maxDepth", 4, _)) => }
  }

  it should "hold against an input that would overflow the default limit" in {
    // 50 000 Zitatebenen sind 100 kB und ein Baum, der jeden Stack sprengt. Mit Grenze ist es
    // ein Fehlerwert.
    Markdown.parseSyntax("> " * 50_000 + "tief\n") should matchPattern {
      case Left(_: ParseError) =>
    }
  }

  "The block limit" should "refuse a source with too many blocks" in {
    Markdown.parseSyntax("- a\n\n- b\n\n- c\n\n- d\n\n", under(roomy.copy(maxBlocks = 6))) should
      matchPattern { case Left(ParseError.LimitExceeded("maxBlocks", 6, _)) => }
  }

  "The step budget" should "run out on a source that is small but expensive" in {
    // Der ganze Grund, warum es das Budget ueberhaupt gibt: Groesse und Tiefe sehen den
    // Unterschied nicht. Beide Quellen sind rund 130 Zeichen lang und 30 Zeilen tief -- aber
    // `*` passiert den Vorfilter und laesst alle Blockanfaenge probieren, `t` nicht.
    val cheap     = "text\n" * 30
    val expensive = "*x*\n" * 30
    val budget    = under(roomy.copy(maxSteps = 45))

    Markdown.parseSyntax(cheap, budget) should matchPattern { case Right(_) => }
    Markdown.parseSyntax(expensive, budget) should matchPattern {
      case Left(ParseError.BudgetExhausted(45, _)) =>
    }
  }

  it should "name the line it ran out on" in {
    Markdown.parseSyntax("# a\n" * 30, under(roomy.copy(maxSteps = 20))) match
      case Left(ParseError.BudgetExhausted(_, atLine)) => atLine should be > 0
      case other                                       => fail(s"kein Budgetfehler: $other")
  }

  "The default limits" should "let an ordinary document through" in {
    val document = ("# Kapitel\n\nEin Absatz mit mehreren Zeilen.\n" * 500)

    Markdown.parseSyntax(document) should matchPattern { case Right(_) => }
  }

  // ---------------------------------------------------------------------------------------
  // Das Profil
  // ---------------------------------------------------------------------------------------

  "The profile" should "say how much it resolves" in {
    // §18.1: "Bis die Konformitaetsfaelle vollstaendig bestanden sind, wird nur die
    // tatsaechlich getestete Teilmenge beworben." Ein Feld statt eines Kommentars -- und was
    // `Inlines` wert ist, steht als Zahl in `CommonMarkConformanceSpec`.
    MarkdownProfile.commonMarkSafe.conformance shouldBe Conformance.Inlines
    MarkdownProfile.untrustedPaste.conformance shouldBe Conformance.Inlines
    MarkdownProfile.blocksOnly.conformance shouldBe Conformance.BlocksOnly
  }

  it should "carry paste-sized limits in the paste profile" in {
    MarkdownProfile.untrustedPaste.limits shouldBe ParseLimits.paste
    ParseLimits.paste.maxSourceChars should be < ParseLimits.default.maxSourceChars
  }

  "Parsing" should "be deterministic" in {
    // P17, Abnahme: "Deterministisches Ergebnis ohne DOM." Zweimal derselbe Quelltext, zweimal
    // derselbe Baum -- IDs eingeschlossen.
    val source = "# Titel\n\n> - eins\n> - zwei\n\n```scala\nval x = 1\n```\n"

    parse(source) shouldBe parse(source)
  }
}
