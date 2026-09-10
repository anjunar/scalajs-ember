package ember.editor.richtext

import ember.editor.core.*
import ember.editor.richtext.StandardMarks.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The marks the next keystroke will carry (§11). */
final class TypingMarksSpec extends AnyFlatSpec with Matchers {

  private def hallo = new RichTextFixture("Hallo Welt!")

  // ---------------------------------------------------------------------------------------
  // A toggle at a collapsed caret
  // ---------------------------------------------------------------------------------------

  "A toggle at a caret" should "create no text" in {
    // §11: "Ein Toggle am kollabierten Caret aendert dieses Feld, ohne Text zu erzeugen."
    val f = hallo
    f.caretAt("t0", 5)

    f.toggle(Strong) shouldBe true

    f.textOf() shouldBe "Hallo Welt!"
    f.runs() should have length 1
  }

  it should "record the choice with its position" in {
    val f = hallo
    f.caretAt("t0", 5)

    f.toggle(Strong): Unit

    f.typingMarks match
      case TypingMarksValue.Explicit(marks, at) =>
        marks.contains(Strong.markId) shouldBe true
        at shouldBe Point.textBefore(NodeId("t0"), 5)
      case other => fail(s"unerwartet: $other")
  }

  "The next keystroke" should "use those marks" in {
    // "Die naechste Eingabe verwendet diese Marks."
    val f = hallo
    f.caretAt("t0", 5)
    f.toggle(Strong): Unit

    f.typeText("X") shouldBe true

    f.shape() shouldBe Vector(
      ("Hallo", Vector.empty),
      ("X", Vector("strong")),
      (" Welt!", Vector.empty)
    )
  }

  it should "keep the choice while typing continues" in {
    // "... blosses Mapping desselben Carets durch eigene Eingabe erhaelt die explizite Wahl."
    val f = hallo
    f.caretAt("t0", 5)
    f.toggle(Strong): Unit

    f.typeText("a"): Unit
    f.typeText("b"): Unit
    f.typeText("c"): Unit

    f.shape() shouldBe Vector(
      ("Hallo", Vector.empty),
      ("abc", Vector("strong")),
      (" Welt!", Vector.empty)
    )
  }

  it should "type into the marked run rather than making a new one each time" in {
    val f = hallo
    f.caretAt("t0", 5)
    f.toggle(Strong): Unit
    f.typeText("ab"): Unit

    f.runs() should have length 3
  }

  // ---------------------------------------------------------------------------------------
  // What clears the choice
  // ---------------------------------------------------------------------------------------

  "A caret jump" should "fall back to Inherit" in {
    // §11: "Ein expliziter Caretsprung oder Range-Wechsel setzt wieder auf kontextabhaengiges
    // Inherit."
    val f = hallo
    f.caretAt("t0", 5)
    f.toggle(Strong): Unit

    f.caretAt("t0", 2)

    f.typingMarks shouldBe TypingMarksValue.Inherit
  }

  it should "make the next keystroke plain again" in {
    val f = hallo
    f.caretAt("t0", 5)
    f.toggle(Strong): Unit
    f.caretAt("t0", 2)

    f.typeText("X"): Unit

    f.shape() shouldBe Vector(("HaXllo Welt!", Vector.empty))
  }

  "A range selection" should "clear it too" in {
    val f = hallo
    f.caretAt("t0", 5)
    f.toggle(Strong): Unit

    f.selectRange(("t0", 0), ("t0", 3))

    f.typingMarks shouldBe TypingMarksValue.Inherit
  }

  // ---------------------------------------------------------------------------------------
  // Inherit
  // ---------------------------------------------------------------------------------------

  "Inherit" should "take the marks of the run at the caret" in {
    val f = hallo
    f.selectRange(("t0", 0), ("t0", 5))
    f.toggle(Strong): Unit

    f.caretAt(f.runs().head.id.value, 3)

    f.typingMarks shouldBe TypingMarksValue.Inherit
    RangeFormatting.activeMarks(f.session.state).contains(Strong.markId) shouldBe true
  }

  it should "let typing continue the run it stands in" in {
    val f = hallo
    f.selectRange(("t0", 0), ("t0", 5))
    f.toggle(Strong): Unit
    f.caretAt(f.runs().head.id.value, 3)

    f.typeText("X"): Unit

    f.shape() shouldBe Vector(("HalXlo", Vector("strong")), (" Welt!", Vector.empty))
  }

  // ---------------------------------------------------------------------------------------
  // Toggling off again
  // ---------------------------------------------------------------------------------------

  "Toggling twice at a caret" should "come back to the surrounding marks" in {
    val f = hallo
    f.caretAt("t0", 5)

    f.toggle(Strong): Unit
    f.toggle(Strong): Unit

    f.typeText("X"): Unit
    f.shape() shouldBe Vector(("HalloX Welt!", Vector.empty))
  }

  "Toggling off inside a bold run" should "make the next keystroke plain" in {
    val f = hallo
    f.selectRange(("t0", 0), ("t0", 11))
    f.toggle(Strong): Unit
    f.caretAt("t0", 5)

    f.toggle(Strong): Unit
    f.typeText("X"): Unit

    f.shape() shouldBe Vector(
      ("Hallo", Vector("strong")),
      ("X", Vector.empty),
      (" Welt!", Vector("strong"))
    )
  }

  // ---------------------------------------------------------------------------------------
  // The history contract
  // ---------------------------------------------------------------------------------------

  "The field" should "declare itself restorable by history" in {
    // §11, last sentence: "Undo/Redo darf die fuer die naechste Eingabe wirksamen Marks nicht
    // zufaellig aus der DOM-Darstellung ableiten." A field that is not restored leaves exactly
    // that guess as the only option. `ember-history` is a separate module (§6) and cannot be
    // linked from here -- what is checkable here is the declaration it acts on.
    TypingMarks.onHistoryRestore shouldBe HistoryRestorePolicy.Restore
  }

  it should "survive a document change, not reset on it" in {
    // `DocumentChangePolicy.Reset` would clear the choice on the very keystroke it was made
    // for. The decision belongs in `reduce`, where the mapping is available.
    TypingMarks.onDocumentChange shouldBe DocumentChangePolicy.Keep
  }

  it should "be captured by a state that carries it" in {
    val f = hallo
    f.caretAt("t0", 5)
    f.toggle(Strong): Unit

    val captured = f.session.state.fields.captureForHistory

    captured should have length 1
    captured.head.field shouldBe TypingMarks
  }

  it should "be captured as Inherit too" in {
    // Inherit is a value like any other, and a snapshot has to carry it: without it, an undo
    // back to a state where nothing was chosen would leave the previous explicit choice
    // standing -- exactly the accident §11 rules out.
    val f = hallo
    f.caretAt("t0", 5)

    f.session.state.fields.captureForHistory.map(_.value) shouldBe
      Vector(TypingMarksValue.Inherit)
  }
}
