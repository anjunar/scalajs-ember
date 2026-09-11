package ember.editor.markdown

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The writer, measured against the promise §18.2 actually makes.
  *
  * ==The promise, and the one that is not made==
  *
  *   - '''Promised:''' `decode(encode(x)) ≃ x`. Write a tree, parse it back, get the same tree.
  *   - '''Not promised:''' `encode(decode(source)) == source`. §18.2: "ist kein Ziel."
  *
  * So this suite never compares Markdown to Markdown. It compares '''trees''' -- and it does so
  * over the whole conformance corpus, because 652 real inputs find escaping bugs that a dozen
  * hand-written cases do not. A writer that forgets to escape a leading `#` produces valid
  * Markdown that means something else, and only a round trip notices.
  *
  * Ids and spans are excluded from the comparison on purpose: the second parse reads a
  * different string, so its offsets are different by construction and its ids are handed out in
  * a different order. What has to survive is the '''shape'''.
  */
final class MarkdownRoundTripSpec extends AnyFlatSpec with Matchers {

  /** How many of the 652 examples survive a write-and-reparse unchanged in shape.
    *
    * Raise it when the writer gets better; never lower it without saying why in the commit.
    */
  private val StableShapes = 621

  /** The tree without ids and spans -- what the round trip has to preserve. */
  private def shape(block: MarkdownBlock): String =
    val head = block match
      case _: MarkdownDocument                          => "doc"
      case MarkdownBlock.Paragraph(_, _, inlines)       => s"p(${shapeOf(inlines)})"
      case MarkdownBlock.Heading(_, _, level, _, lines) => s"h$level(${shapeOf(lines)})"
      case MarkdownBlock.CodeBlock(_, _, literal, fence) =>
        // Der Zaunzeichen und seine Laenge sind Schreibweise, der Info-String ist Bedeutung.
        s"code[${fence.map(_.info).getOrElse("")}](${literal.replace("\n", "\\n")})"
      case MarkdownBlock.HtmlBlock(_, _, literal) => s"html(${literal.replace("\n", "\\n")})"
      case MarkdownBlock.ThematicBreak(_, _)      => "hr"
      case MarkdownBlock.BlockQuote(_, _, _)      => "quote"
      case MarkdownBlock.ListItem(_, _, _)        => "item"
      case MarkdownBlock.MarkdownList(_, _, kind, tight, _) =>
        val shown = kind match
          case ListKind.Bullet(_)             => "ul"
          case ListKind.Ordered(start, _)     => s"ol$start"
        s"$shown${if tight then "" else "-loose"}"

    if block.children.isEmpty then head
    else s"$head{${block.children.map(shape).mkString(",")}}"

  private def shapeOf(inlines: Vector[MarkdownInline]): String =
    inlines.map {
      case MarkdownInline.Text(_, _, value)         => value
      case MarkdownInline.Code(_, _, literal)       => s"c($literal)"
      case MarkdownInline.SoftBreak(_, _)           => "~"
      case MarkdownInline.HardBreak(_, _)           => "|"
      case MarkdownInline.HtmlInline(_, _, literal) => s"raw($literal)"
      case MarkdownInline.Emphasis(_, _, kids)      => s"e(${shapeOf(kids)})"
      case MarkdownInline.Strong(_, _, kids)        => s"s(${shapeOf(kids)})"
      case MarkdownInline.Link(_, _, target, title, kids) =>
        s"a[$target|${title.getOrElse("")}](${shapeOf(kids)})"
      case MarkdownInline.Image(_, _, target, title, kids) =>
        s"i[$target|${title.getOrElse("")}](${shapeOf(kids)})"
    }.mkString

  private def parse(source: String): Option[MarkdownDocument] =
    Markdown.parseSyntax(source).toOption.map(_.document)

  private enum Outcome:
    case Stable
    case Drifted(before: String, after: String, written: String)
    case Broke(reason: String)

  private lazy val outcomes: Vector[(SpecExample, Outcome)] =
    SpecFixtures.all.map { example =>
      val outcome =
        try
          parse(example.markdown) match
            case None => Outcome.Broke("erster Parse abgewiesen")
            case Some(first) =>
              val written = MarkdownWriter.write(first)
              parse(written) match
                case None => Outcome.Broke("zweiter Parse abgewiesen")
                case Some(second) =>
                  if shape(first) == shape(second) then Outcome.Stable
                  else Outcome.Drifted(shape(first), shape(second), written)
        catch case failure: Throwable => Outcome.Broke(failure.toString)

      example -> outcome
    }

  // ---------------------------------------------------------------------------------------

  "Writing" should "never throw and never produce something unparseable" in {
    val broken = outcomes.collect { case (example, Outcome.Broke(reason)) =>
      s"#${example.number} (${example.section}): $reason"
    }

    withClue(broken.take(10).mkString("\n", "\n", "\n")) { broken shouldBe empty }
  }

  it should s"keep the shape of exactly $StableShapes of the 652 examples" in {
    val stable = outcomes.count(_._2 == Outcome.Stable)

    withClue(s"\nStabil: $stable von ${SpecFixtures.all.length}.\n$report\n") {
      stable shouldBe StableShapes
    }
  }

  /** Per-section counts, rendered only on failure -- a single number never says what moved. */
  private def report: String =
    outcomes
      .groupBy(_._1.section)
      .toVector
      .map { (section, entries) =>
        f"  ${entries.count(_._2 == Outcome.Stable)}%3d/${entries.length}%-3d  $section"
      }
      .sorted
      .mkString("\n")

  // ---------------------------------------------------------------------------------------
  // Die Faelle, die P18 ausdruecklich nennt
  // ---------------------------------------------------------------------------------------

  private def roundTrip(source: String): String =
    parse(source).map(MarkdownWriter.write).getOrElse(fail("nicht parsebar"))

  private def stable(source: String): Unit =
    val first = parse(source).getOrElse(fail("nicht parsebar"))
    val again = parse(MarkdownWriter.write(first)).getOrElse(fail("Ausgabe nicht parsebar"))
    withClue(s"\ngeschrieben:\n${MarkdownWriter.write(first)}\n") {
      shape(again) shouldBe shape(first)
    }

  "A fence" should "be long enough to survive its own content" in {
    // §18.2: "sichere Fence-Laenge beim Export." Ein Inhalt mit drei Backticks braucht vier.
    roundTrip("````\n```\ndrin\n```\n````\n") should include("````")
    stable("````\n```\ndrin\n```\n````\n")
  }

  "A code span" should "get a longer run than anything inside it" in {
    stable("`` a ` b ``\n")
    stable("` `` `\n")
  }

  "A leading hash in text" should "be escaped so it stays text" in {
    // Der Fall, den nur ein Round-Trip findet: `# ` am Zeilenanfang ist eine Ueberschrift, und
    // ein Writer, der das vergisst, erzeugt gueltiges Markdown mit anderer Bedeutung.
    roundTrip("\\# keine Ueberschrift\n") should startWith("\\#")
    stable("\\# keine Ueberschrift\n")
  }

  "A leading number with a dot" should "be escaped too" in {
    stable("1\\. kein Listenpunkt\n")
  }

  "Emphasis and strong" should "survive nested" in {
    stable("***alles*** und *nur ein bisschen*\n")
  }

  "A hard break" should "survive as a hard break" in {
    // §18.2 verlangt den Unterschied zwischen Soft und Hard erhalten -- und der Writer schreibt
    // einen Rueckstrich, weil zwei Leerzeichen am Zeilenende jeder Trimmer frisst.
    val written = roundTrip("eins  \nzwei\n")
    written should include("\\\n")
    stable("eins  \nzwei\n")
  }

  "A soft break" should "stay a soft break" in {
    stable("eins\nzwei\n")
  }

  "A link" should "survive destination and title" in {
    stable("""[Text](/ziel "Titel")""" + "\n")
  }

  it should "survive a destination with a space" in {
    stable("[Text](</mit leerzeichen>)\n")
  }

  it should "come back inline even when it was written as a reference" in {
    // Referenzlabels duerfen kanonisiert werden (§18.2). Was erhalten bleibt, ist das Ziel.
    roundTrip("[Text][ziel]\n\n[ziel]: /aufgeloest\n") should include("(/aufgeloest)")
  }

  "An image" should "survive alt and title" in {
    stable("""![Ein Bild](/b.png "Titel")""" + "\n")
  }

  "A list" should "survive tight and loose" in {
    stable("- eins\n- zwei\n")
    stable("- eins\n\n- zwei\n")
  }

  it should "keep an ordered start number" in {
    roundTrip("5. fuenf\n6. sechs\n") should startWith("5. ")
    stable("5. fuenf\n6. sechs\n")
  }

  "A nested quote in a list" should "get both prefixes right" in {
    // Zwei Ebenen, die beide eine erste Zeile schreiben und danach etwas anderes. Der Grund,
    // warum der Puffer pro Ebene merkt, ob sie schon geschrieben hat.
    stable("- > Zitat im Punkt\n  > zweite Zeile\n")
  }

  "A heading" should "keep its level" in {
    stable("# eins\n\n## zwei\n\n###### sechs\n")
  }

  it should "stay Setext when it can" in {
    roundTrip("Titel\n===\n") should include("===")
  }

  it should "fall back to ATX when Setext cannot express it" in {
    // Setext kennt nur Stufe eins und zwei. Eine Stufe drei als Setext auszugeben waere eine
    // Ausgabe, die sich nicht zurueckliest.
    roundTrip("### drei\n") should startWith("### ")
  }
}
