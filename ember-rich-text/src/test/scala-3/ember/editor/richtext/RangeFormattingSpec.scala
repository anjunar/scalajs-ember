package ember.editor.richtext

import ember.editor.core.*
import ember.editor.richtext.StandardMarks.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Formatting a range of text (§8.2, §12). */
final class RangeFormattingSpec extends AnyFlatSpec with Matchers {

  private def hallo = new RichTextFixture("Hallo Welt!")

  private def marksOf(f: RichTextFixture, text: String): Vector[String] =
    f.shape().find(_._1 == text).map(_._2).getOrElse(fail(s"kein Lauf `$text`: ${f.shape()}"))

  // ---------------------------------------------------------------------------------------
  // Marks are node data
  // ---------------------------------------------------------------------------------------

  "Formatting" should "change nodes, not a rendering" in {
    // P12, acceptance: "Formatierung wird durch Nodes/Marks bestimmt; kein Browser-execCommand."
    val f = hallo

    f.selectRange(("t0", 0), ("t0", 5))
    f.toggle(Strong) shouldBe true

    marksOf(f, "Hallo") shouldBe Vector("strong")
  }

  it should "leave the text untouched" in {
    val f = hallo
    f.selectRange(("t0", 0), ("t0", 5))

    f.toggle(Strong): Unit

    f.textOf() shouldBe "Hallo Welt!"
  }

  // ---------------------------------------------------------------------------------------
  // Partially covered runs
  // ---------------------------------------------------------------------------------------

  "A range inside one run" should "cut it at both ends" in {
    val f = hallo

    f.selectRange(("t0", 6), ("t0", 10))
    f.toggle(Emphasis): Unit

    f.shape().map(_._1) shouldBe Vector("Hallo ", "Welt", "!")
  }

  "A range starting at a run boundary" should "cut only where needed" in {
    val f = hallo

    f.selectRange(("t0", 0), ("t0", 5))
    f.toggle(Strong): Unit

    f.shape() shouldBe Vector(("Hallo", Vector("strong")), (" Welt!", Vector.empty))
  }

  "A range covering a whole run" should "cut nothing" in {
    val f = hallo

    f.selectRange(("t0", 0), ("t0", 11))
    f.toggle(Strong): Unit

    f.shape() shouldBe Vector(("Hallo Welt!", Vector("strong")))
  }

  // ---------------------------------------------------------------------------------------
  // Across runs and blocks
  // ---------------------------------------------------------------------------------------

  "A range across several runs" should "format all of them" in {
    val f = hallo
    f.selectRange(("t0", 6), ("t0", 10))
    f.toggle(Emphasis): Unit

    // "Hallo ", "Welt"(em), "!" -- now bold everything.
    f.selectRange(("t0", 0), (f.runs().last.id.value, 1))
    f.toggle(Strong): Unit

    f.shape().map(_._2) shouldBe Vector(
      Vector("strong"),
      Vector("emphasis", "strong"),
      Vector("strong")
    )
  }

  "A range across blocks" should "format the runs in both" in {
    val f = new RichTextFixture("Erster", "Zweiter")

    f.selectRange(("t0", 3), ("t1", 3))
    f.toggle(Strong) shouldBe true

    f.shape("p0") shouldBe Vector(("Ers", Vector.empty), ("ter", Vector("strong")))
    f.shape("p1") shouldBe Vector(("Zwe", Vector("strong")), ("iter", Vector.empty))
  }

  "A backward selection" should "format the same text" in {
    // §11: "Vorwaerts/rueckwaerts ergibt sich aus der aktuellen Dokumentordnung, nicht aus
    // lexikographischer ID-Sortierung." The direction is the user's; the text is the same.
    val f = hallo

    f.selectRange(("t0", 10), ("t0", 6))
    f.toggle(Strong): Unit

    marksOf(f, "Welt") shouldBe Vector("strong")
  }

  it should "keep its direction" in {
    // Nach dem Splitten liegen Anker und Fokus in verschiedenen Laeufen, ihre Offsets sind
    // also nicht mehr vergleichbar. Was zaehlt, ist die Dokumentordnung: der Anker steht im
    // spaeteren Lauf, der Fokus im frueheren -- rueckwaerts, wie er gesetzt wurde.
    val f = hallo
    f.selectRange(("t0", 10), ("t0", 6))

    f.toggle(Strong): Unit

    val order = f.runs().map(_.id)
    f.session.selection match
      case Some(range: RangeSelection) =>
        order.indexOf(range.anchor.owner) should be > order.indexOf(range.focus.owner)
        f.selectedText shouldBe "Welt"
      case other => fail(s"unerwartet: $other")
  }

  // ---------------------------------------------------------------------------------------
  // Toggle semantics
  // ---------------------------------------------------------------------------------------

  "Toggling twice" should "return to the start" in {
    val f = hallo

    f.selectRange(("t0", 6), ("t0", 10))
    f.toggle(Strong): Unit
    f.toggleRun("Welt", Strong)

    f.shape() shouldBe Vector(("Hallo Welt!", Vector.empty))
  }

  "A toggle over a mixed range" should "set the mark everywhere" in {
    // The alternative -- inverting each run separately -- makes a second press look like a
    // no-op to the user while the pieces silently swap. Every editor resolves it this way, and
    // §8.2 leaves the choice to the profile.
    val f = hallo
    f.selectRange(("t0", 0), ("t0", 5))
    f.toggle(Strong): Unit

    f.selectRange(("t0", 0), (f.runs().last.id.value, 6))
    f.toggle(Strong): Unit

    f.shape() shouldBe Vector(("Hallo Welt!", Vector("strong")))
  }

  it should "clear it only when everything already has it" in {
    val f = hallo
    f.selectRange(("t0", 0), ("t0", 11))
    f.toggle(Strong): Unit

    f.selectRange(("t0", 0), ("t0", 11))
    f.toggle(Strong): Unit

    f.shape() shouldBe Vector(("Hallo Welt!", Vector.empty))
  }

  // ---------------------------------------------------------------------------------------
  // Mark conflicts
  // ---------------------------------------------------------------------------------------

  "InlineCode" should "displace the other marks" in {
    // §8.2 leaves conflicts to the profile. This profile's one rule: code is verbatim text, and
    // Markdown cannot write bold inside a code span -- so a run carrying both would be a
    // document §18 could not export without loss.
    val f = hallo
    f.selectRange(("t0", 0), ("t0", 11))
    f.toggle(Strong): Unit
    f.selectRange(("t0", 0), ("t0", 11))
    f.toggle(Emphasis): Unit

    f.selectRange(("t0", 0), ("t0", 11))
    f.toggle(InlineCode): Unit

    marksOf(f, "Hallo Welt!") shouldBe Vector("inline-code")
  }

  it should "be displaced in turn" in {
    // Neither wins by being applied second.
    val f = hallo
    f.selectRange(("t0", 0), ("t0", 11))
    f.toggle(InlineCode): Unit

    f.selectRange(("t0", 0), ("t0", 11))
    f.toggle(Strong): Unit

    marksOf(f, "Hallo Welt!") shouldBe Vector("strong")
  }

  "The other four" should "combine freely" in {
    val f = hallo
    Vector(Strong, Emphasis, Underline, Strike).foreach { mark =>
      f.selectRange(("t0", 0), ("t0", 11))
      f.toggle(mark): Unit
    }

    marksOf(f, "Hallo Welt!") shouldBe
      Vector("emphasis", "strike", "strong", "underline")
  }

  // ---------------------------------------------------------------------------------------
  // Reading back
  // ---------------------------------------------------------------------------------------

  "activeMarks" should "report what every covered run has" in {
    val f = hallo
    f.selectRange(("t0", 0), ("t0", 5))
    f.toggle(Strong): Unit

    f.selectRange(("t0", 0), (f.runs().last.id.value, 6))

    active(f).markIds.map(_.value) shouldBe Vector.empty
  }

  it should "report a mark all of them share" in {
    val f = hallo
    f.selectRange(("t0", 0), ("t0", 11))
    f.toggle(Strong): Unit
    f.selectRange(("t0", 6), ("t0", 10))
    f.toggle(Emphasis): Unit

    f.selectRange(("t0", 0), (f.runs().last.id.value, 1))

    active(f).contains(Strong.markId) shouldBe true
    active(f).contains(Emphasis.markId) shouldBe false
  }

  private def active(f: RichTextFixture): MarkSet =
    RangeFormatting.activeMarks(f.session.state)
}
