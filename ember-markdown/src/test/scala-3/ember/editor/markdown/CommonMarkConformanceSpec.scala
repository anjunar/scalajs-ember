package ember.editor.markdown

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The parser against the official conformance suite.
  *
  * ==What this suite can claim and what it cannot==
  *
  * P17s risk line is blunt: "Keine behauptete vollstaendige CommonMark-Konformitaet aus
  * einfachen Happy-Path-Tests." So this suite claims nothing. It measures three things, and
  * each is a different kind of statement:
  *
  *   1. '''Robustness.''' All 652 examples parse, within the default limits, without an
  *      exception. That is a real property and it holds today.
  *   2. '''Well-formed spans.''' Every span of every parse lies inside its parent's and inside
  *      the source. §18.2 asks for source maps; a source map with a span pointing outside its
  *      block is worse than none.
  *   3. '''Coverage, as a number.''' How many examples the parser reproduces byte for byte.
  *      That number is the "tatsaechlich getestete Teilmenge" of §18.1 -- and because it is
  *      asserted exactly and not as a lower bound, neither a regression nor an improvement can
  *      pass unnoticed.
  *
  * After P18 the number is 651 of 652, and the one that is left is not an accident: it uses
  * named character references the default [[EntityTable]] deliberately does not carry. Swapping
  * in a fuller table is one line of application code; shipping 2231 names in every browser
  * bundle is not something a library should decide for its users.
  */
final class CommonMarkConformanceSpec extends AnyFlatSpec with Matchers {

  /** How many of the 652 examples the parser reproduces exactly.
    *
    * Raise it when the parser gets better; never lower it without saying why in the commit.
    */
  private val ExactMatches = 651

  private lazy val outcomes: Vector[(SpecExample, Outcome)] =
    SpecFixtures.all.map(example => example -> outcomeOf(example))

  private enum Outcome:
    case Matches
    case Differs(actual: String)
    case Refused(error: ParseError)
    case Threw(reason: String)

  private def outcomeOf(example: SpecExample): Outcome =
    try
      Markdown.parseSyntax(example.markdown) match
        case Left(error) => Outcome.Refused(error)
        case Right(result) =>
          val rendered = ConformanceHtml.render(result.document)
          if rendered == example.html then Outcome.Matches else Outcome.Differs(rendered)
    catch case failure: Throwable => Outcome.Threw(s"${failure.getClass.getName}: ${failure.getMessage}")

  // ---------------------------------------------------------------------------------------
  // Die Suite selbst
  // ---------------------------------------------------------------------------------------

  "The fixtures" should "be the pinned specification version" in {
    // Die Version faellt beim Erzeugen aus dem Dateinamen an (project/MarkdownSpecFixtures.scala).
    // Damit ist die "Korpus-/Spezifikationsversion" der Abnahme eine Zahl, die der Build
    // ausrechnet, und keine Behauptung in einem Kommentar.
    SpecFixtures.specVersion shouldBe "0.31.2"
    MarkdownProfile.commonMarkSafe.specVersion shouldBe SpecFixtures.specVersion
  }

  it should "hold all 652 examples" in {
    SpecFixtures.all should have length 652
    SpecFixtures.all.map(_.number) shouldBe (1 to 652).toVector
  }

  // ---------------------------------------------------------------------------------------
  // 1. Robustheit
  // ---------------------------------------------------------------------------------------

  "Every example" should "parse without an exception" in {
    val threw = outcomes.collect { case (example, Outcome.Threw(reason)) =>
      s"#${example.number} (${example.section}): $reason"
    }

    withClue(threw.take(10).mkString("\n", "\n", "\n")) { threw shouldBe empty }
  }

  it should "stay inside the default limits" in {
    // Kein Beispiel der Spezifikation ist gross, tief oder teuer. Schlaegt das hier fehl, sind
    // die Grenzen zu eng eingestellt und nicht die Eingabe zu gross.
    val refused = outcomes.collect { case (example, Outcome.Refused(error)) =>
      s"#${example.number}: ${error.render}"
    }

    withClue(refused.take(10).mkString("\n", "\n", "\n")) { refused shouldBe empty }
  }

  // ---------------------------------------------------------------------------------------
  // 2. Wohlgeformte Spannen
  // ---------------------------------------------------------------------------------------

  "Every span" should "lie inside its parent and inside the source" in {
    val broken = SpecFixtures.all.flatMap { example =>
      Markdown.parseSyntax(example.markdown).toOption.toVector.flatMap { result =>
        val source = example.markdown

        def check(block: MarkdownBlock, parent: Option[SourceSpan]): Vector[String] =
          val span = block.span
          val here = Vector(
            Option.when(span.start < 0 || span.end > source.length)(
              s"#${example.number}: ${span.render} liegt ausserhalb von 0..${source.length}"
            ),
            Option.when(span.end < span.start)(s"#${example.number}: ${span.render} laeuft rueckwaerts"),
            parent.filterNot(_.containsSpan(span)).map(outer =>
              s"#${example.number}: ${span.render} liegt nicht in ${outer.render}"
            )
          ).flatten

          here ++ block.children.flatMap(check(_, Some(span)))

        check(result.document, None)
      }
    }

    withClue(broken.take(10).mkString("\n", "\n", "\n")) { broken shouldBe empty }
  }

  it should "be recorded in the source map, once per block" in {
    val result = Markdown
      .parseSyntax("# Titel\n\n> Zitat\n>\n> - eins\n> - zwei\n")
      .getOrElse(fail("nicht parsebar"))

    def count(block: MarkdownBlock): Int = 1 + block.children.map(count).sum

    result.sourceMap.size shouldBe count(result.document)
  }

  // ---------------------------------------------------------------------------------------
  // 3. Abdeckung, als Zahl
  // ---------------------------------------------------------------------------------------

  "The parser" should s"reproduce exactly $ExactMatches of the 652 examples" in {
    val matching = outcomes.count(_._2 == Outcome.Matches)

    withClue(
      s"\nAbdeckung: $matching von ${SpecFixtures.all.length}. " +
        "Steigt sie, ist die Zahl in dieser Suite nachzuziehen; faellt sie, ist etwas kaputt.\n" +
        report + "\n"
    ) {
      matching shouldBe ExactMatches
    }
  }

  /** Per-section counts. Only rendered when the assertion above fails -- but then it says
    * immediately '''which''' construct moved, which a single number never does.
    */
  private def report: String =
    outcomes
      .groupBy(_._1.section)
      .toVector
      .map { (section, entries) =>
        val matching = entries.count(_._2 == Outcome.Matches)
        f"  $matching%3d/${entries.length}%-3d  $section"
      }
      .sorted
      .mkString("\n")
}
