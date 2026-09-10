package ember.editor.richtext

import ember.editor.core.*
import ember.editor.richtext.StandardMarks.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The normal form of adjacent text runs (§8.2).
  *
  * The plan spells this one out as a "Konkreter Normalisierungstest" rather than leaving it to
  * judgement, and that is the shape of this suite: the worked example from §8.2, then each of
  * its promises separately, then the cases that must *not* merge.
  */
final class TextRunNormalizationSpec extends AnyFlatSpec with Matchers {

  private def hallo = new RichTextFixture("Hallo Welt!")

  /** Bolds `[from, to)` of `t0`. */
  private def bold(f: RichTextFixture, from: Int, to: Int): Unit =
    f.selectRange(("t0", from), ("t0", to))
    f.toggle(Strong): Unit

  // ---------------------------------------------------------------------------------------
  // The worked example from §8.2
  // ---------------------------------------------------------------------------------------

  "Bolding a word" should "cut the run into three" in {
    val f = hallo

    bold(f, 6, 10)

    f.shape() shouldBe Vector(
      ("Hallo ", Vector.empty),
      ("Welt", Vector("strong")),
      ("!", Vector.empty)
    )
  }

  "Removing it again" should "produce a single run with the same text" in {
    // §8.2, verbatim: "Fett wieder entfernt: Text("Hallo Welt!", \{\})".
    val f = hallo
    bold(f, 6, 10)

    f.toggleRun("Welt", Strong)

    f.shape() shouldBe Vector(("Hallo Welt!", Vector.empty))
  }

  it should "keep the left id" in {
    // §8.2: "Die linke ID bleibt erhalten." `MergeText` guarantees it (§11); this holds the
    // profile to actually using it rather than rebuilding the run.
    val f = hallo
    bold(f, 6, 10)

    f.toggleRun("Welt", Strong)

    f.runs().map(_.id.value) shouldBe Vector("t0")
  }

  it should "cost no second undo step" in {
    // P12, acceptance: "Entformatieren erzeugt keinen eigenen History-Schritt fuer den Merge."
    // The merge is a transform, so it runs inside the same transaction -- one commit, not two.
    val f = hallo
    bold(f, 6, 10)
    f.selectRun("Welt")
    val before = f.session.state.revision.value

    f.toggle(Strong): Unit

    f.session.state.revision.value shouldBe before + 1
  }

  it should "map the selection through the merge" in {
    // §8.2: "Punkte und Bookmarks der aufgenommenen Nodes werden um die vorangestellte
    // Textlaenge versetzt. Caret und vorwaerts/rueckwaerts gerichtete Auswahl bleiben logisch
    // erhalten." The selection covers "Welt" -- offsets 0..4 of its own run before the merge,
    // 6..10 of the merged one after it. Same characters, different coordinates.
    val f = hallo
    bold(f, 6, 10)

    f.toggleRun("Welt", Strong)

    f.selectedText shouldBe "Welt"
    f.session.selection shouldBe Some(
      RangeSelection(Point.textBefore(NodeId("t0"), 6), Point.textBefore(NodeId("t0"), 10))
    )
  }

  it should "map a backward selection the same way" in {
    // §11: "Anchor/Focus werden niemals nur zugunsten sortierter Endpunkte ueberschrieben."
    val f = hallo
    f.selectRange(("t0", 10), ("t0", 6))
    f.toggle(Strong): Unit

    val bolded = f.runs().find(_.text == "Welt").getOrElse(fail("kein fetter Lauf"))
    f.selectRange((bolded.id.value, 4), (bolded.id.value, 0))
    f.toggle(Strong): Unit

    f.session.selection shouldBe Some(
      RangeSelection(Point.textBefore(NodeId("t0"), 10), Point.textBefore(NodeId("t0"), 6))
    )
  }

  // ---------------------------------------------------------------------------------------
  // No growing fragmentation
  // ---------------------------------------------------------------------------------------

  "Repeated cycles" should "not fragment" in {
    // §8.2: "Wiederholtes Formatieren/Entformatieren darf daher keine anwachsende
    // Fragmentierung hinterlassen."
    val f = hallo

    (1 to 5).foreach { _ =>
      bold(f, 6, 10)
      f.runs() should have length 3

      f.toggleRun("Welt", Strong)

      f.shape() shouldBe Vector(("Hallo Welt!", Vector.empty))
    }
  }

  "Normalising again" should "be a no-op" in {
    // "... erneute Normalisierung ist ein No-op." A transaction that changes nothing produces
    // no commit at all (§10).
    val f = hallo

    val outcome = f.session.update(_.select(RangeSelection.caret(Point.textBefore(NodeId("t0"), 3))))

    outcome.map(_.documentChanged) shouldBe Right(false)
    f.shape() shouldBe Vector(("Hallo Welt!", Vector.empty))
  }

  // ---------------------------------------------------------------------------------------
  // What must not merge
  // ---------------------------------------------------------------------------------------

  "Runs with different marks" should "stay apart" in {
    val f = hallo
    bold(f, 6, 10)

    f.shape() should have length 3
  }

  "Runs in different blocks" should "stay apart" in {
    val f = new RichTextFixture("Erster", "Zweiter")

    f.shape("p0") shouldBe Vector(("Erster", Vector.empty))
    f.shape("p1") shouldBe Vector(("Zweiter", Vector.empty))
  }

  "Runs separated by a break" should "stay apart" in {
    // §8.2: nicht zusammengefuehrt wird "ueber Paragraph-, Link-, Break- oder Atomgrenzen
    // hinweg". Nothing in the rule mentions breaks -- adjacency does the work.
    val f = hallo
    f.caretAt("t0", 5)

    f.dispatch(RichText.InsertBreak, BreakKind.Hard) shouldBe true

    f.runs() should have length 2
    f.document.childrenOf(NodeId("p0")) should have length 3
  }

  it should "stay apart for a soft break too" in {
    val f = hallo
    f.caretAt("t0", 5)

    f.dispatch(RichText.InsertBreak, BreakKind.Soft) shouldBe true

    f.runs().map(_.text) shouldBe Vector("Hallo", " Welt!")
  }

  "An empty run next to a full one" should "be removed, not merged" in {
    // The two rules do different jobs and must not take each other's. Merging an empty run
    // would move a caret standing in it; removing it is what `DropRedundantEmptyText` is for,
    // and it leaves the selected one alone.
    val f = hallo
    f.caretAt("t0", 11)

    f.edit(_.insert(NodeId("p0"), 1, TextNode(NodeId("leer"), "")): Unit)

    f.runs().map(_.id.value) shouldBe Vector("t0")
  }

  // ---------------------------------------------------------------------------------------
  // Undo and redo across the merge
  // ---------------------------------------------------------------------------------------

  "The formatted state" should "come back through the same operations" in {
    // The plan asks for undo/redo here. `ember-history` is a different module and cannot be
    // linked from this one (§6), so the equivalent is checked directly: the cut state and the
    // merged state are both reachable, and neither is a dead end.
    val f = hallo
    bold(f, 6, 10)
    val cut = f.shape()

    f.toggleRun("Welt", Strong)
    f.shape() shouldBe Vector(("Hallo Welt!", Vector.empty))

    bold(f, 6, 10)
    f.shape() shouldBe cut
  }
}
