package ember.editor.browser

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The input pipeline's rules, as rules (P22).
  *
  * ==What is here==
  *
  * The parts that are functions of their inputs: which intent an `inputType` means, when an
  * operation counts as already handled, what the smallest splice between two strings is, and when
  * `Tab` may be taken. Those carry most of the pipeline's reasoning and none of its plumbing.
  *
  * ==What is not==
  *
  * Everything that needs an engine: whether a keystroke actually produces a `beforeinput`,
  * whether it is cancelable, whether preventing it really stops the DOM change, and what a
  * browser does when nobody prevents anything. `editing.spec.mjs` and `native-input.spec.mjs`.
  */
final class InputPipelineSpec extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------------------
  // Die Tabelle
  // ---------------------------------------------------------------------------------------

  "An input type" should "become the intent it names" in {
    BeforeInputAdapter.intentOf("insertText", Some("a")) shouldBe InputIntent.InsertText("a")
    BeforeInputAdapter.intentOf("insertParagraph") shouldBe InputIntent.InsertParagraph
    BeforeInputAdapter.intentOf("insertLineBreak") shouldBe InputIntent.InsertLineBreak
    BeforeInputAdapter.intentOf("formatBold") shouldBe InputIntent.Format(NativeFormat.Bold)
    BeforeInputAdapter.intentOf("historyUndo") shouldBe
      InputIntent.History(HistoryDirection.Undo)
  }

  it should "carry no text when the browser sent none" in {
    // `data` is null for a deletion, and `null.toString` is how an editor inserts the word
    // "null" into someone's document.
    BeforeInputAdapter.intentOf("insertText", None) shouldBe InputIntent.InsertText("")
  }

  it should "keep autocorrect apart from typing" in {
    // §15.2 lists "Autokorrektur-Replacement" as its own case, because the range it replaces is
    // not the selection -- it comes from `getTargetRanges`. Reporting it as ordinary typing
    // would insert the correction and leave the mistake standing.
    BeforeInputAdapter.intentOf("insertReplacementText", Some("Haus")) shouldBe
      InputIntent.ReplaceText("Haus")
  }

  it should "give a deletion its direction and its granularity" in {
    BeforeInputAdapter.intentOf("deleteContentBackward") shouldBe
      InputIntent.Delete(Direction.Backward, Granularity.Character)
    BeforeInputAdapter.intentOf("deleteWordForward") shouldBe
      InputIntent.Delete(Direction.Forward, Granularity.Word)
    BeforeInputAdapter.intentOf("deleteSoftLineBackward") shouldBe
      InputIntent.Delete(Direction.Backward, Granularity.Line)
  }

  it should "treat a plain deleteContent as the selection, not as a character" in {
    // A `deleteContent` has no direction: what goes is what is selected. Reading it as backward
    // would take one character too many at a caret that was never there.
    BeforeInputAdapter.intentOf("deleteContent") shouldBe
      InputIntent.Delete(Direction.Backward, Granularity.Selection)
  }

  it should "find a paste's payload in the data transfer, not in data" in {
    BeforeInputAdapter.intentOf("insertFromPaste", None, Some("Text")) shouldBe
      InputIntent.Transfer(TransferKind.Paste, Some("Text"))
  }

  it should "stay unknown when it is" in {
    // The important half: an unknown type is not an error and not a no-op. It is an event this
    // editor leaves to the browser, and `Unknown` is what makes the controller do that.
    BeforeInputAdapter.intentOf("insertHorizontalRule") shouldBe
      InputIntent.Unknown("insertHorizontalRule")
  }

  "An unknown intent" should "not count as a document change" in {
    // What a readonly editor asks. Refusing everything would prevent the defaults of events that
    // were never going to change anything.
    InputIntent.Unknown("x").editsDocument shouldBe false
    InputIntent.InsertText("a").editsDocument shouldBe true
    InputIntent.Delete(Direction.Backward, Granularity.Character).editsDocument shouldBe true
  }

  // ---------------------------------------------------------------------------------------
  // preventDefault
  // ---------------------------------------------------------------------------------------

  "preventDefault" should "follow the take-over or the refusal, and nothing else" in {
    // P22's risk list, verbatim: "Event-Ownership und erfolgreiche Modelluebernahme oder bewusste
    // Ablehnung bestimmen preventDefault, nicht die blosse Existenz eines Handlers."
    InputOutcome.TakenOver(InputIntent.InsertParagraph).preventsDefault shouldBe true
    InputOutcome.Refused(InputIntent.InsertParagraph, "readonly").preventsDefault shouldBe true

    InputOutcome.LeftNative(InputIntent.InsertParagraph).preventsDefault shouldBe false
    InputOutcome.NotOurs.preventsDefault shouldBe false
    InputOutcome.Deduplicated.preventsDefault shouldBe false
    InputOutcome.Unimported("kaputt").preventsDefault shouldBe false
    InputOutcome.Idle(ControllerState.Composing).preventsDefault shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // Genau einmal
  // ---------------------------------------------------------------------------------------

  "An operation log" should "let the echo of a handled event pass once" in {
    val log = new InputOperationLog()
    log.record("insertText"): Unit

    log.consume("insertText") shouldBe true
    log.consume("insertText") shouldBe false
  }

  it should "keep two of the same kind apart" in {
    // Two backspaces in a row are two operations. An `inputType` alone would collapse them and
    // the second `input` would be taken for the first one's echo -- so the second deletion would
    // never reach the model.
    val log = new InputOperationLog()
    log.record("deleteContentBackward"): Unit
    log.record("deleteContentBackward"): Unit

    log.consume("deleteContentBackward") shouldBe true
    log.consume("deleteContentBackward") shouldBe true
    log.consume("deleteContentBackward") shouldBe false
  }

  it should "not answer for a different kind" in {
    val log = new InputOperationLog()
    log.record("insertText"): Unit

    log.consume("insertParagraph") shouldBe false
    log.outstanding shouldBe 1
  }

  it should "forget the oldest claims rather than grow" in {
    // A `beforeinput` whose `input` never came would otherwise sit in the log and swallow the
    // next real event of its type -- a deletion that silently does nothing.
    val log = new InputOperationLog(limit = 2)
    log.record("a"): Unit
    log.record("b"): Unit
    log.record("c"): Unit

    log.outstanding shouldBe 2
    log.consume("a") shouldBe false
    log.consume("c") shouldBe true
  }

  // ---------------------------------------------------------------------------------------
  // Der Splice
  // ---------------------------------------------------------------------------------------

  "A native change" should "be the smallest splice that explains it" in {
    // §15.1's whole argument: a splice reaches `replaceData` for the changed range, an assignment
    // rewrites the run. In a long paragraph that is the difference between a caret that stays and
    // one that jumps to the end.
    NativeInputReader.delta("Hallo", "Hallo") shouldBe None
    NativeInputReader.delta("Hallo", "Hallo!") shouldBe Some(TextSplice(5, 0, "!"))
    NativeInputReader.delta("Hallo", "Hall") shouldBe Some(TextSplice(4, 1, ""))
    NativeInputReader.delta("Hallo", "Hallo Welt") shouldBe Some(TextSplice(5, 0, " Welt"))
  }

  it should "touch only the middle when both ends stayed" in {
    NativeInputReader.delta("Hallo Welt", "Hallo schoene Welt") shouldBe
      Some(TextSplice(6, 0, "schoene "))
    NativeInputReader.delta("abXYZcd", "abQcd") shouldBe Some(TextSplice(2, 3, "Q"))
  }

  it should "describe an insertion at the very front" in {
    NativeInputReader.delta("Welt", "Hallo Welt") shouldBe Some(TextSplice(0, 0, "Hallo "))
  }

  it should "describe emptying a run" in {
    NativeInputReader.delta("Hallo", "") shouldBe Some(TextSplice(0, 5, ""))
    NativeInputReader.delta("", "Hallo") shouldBe Some(TextSplice(0, 0, "Hallo"))
  }

  it should "never cut a surrogate pair in half" in {
    // §11: UTF-16 is the offset measure, and an offset inside a pair addresses half a codepoint.
    // The splice would produce two broken strings, and every position in the run would then fail
    // `Point.validateIn`.
    val grinning = "😀"  // U+1F600
    val thinking = "🤔"  // U+1F914, shares no unit with it

    val splice = NativeInputReader.delta(s"a${grinning}b", s"a${thinking}b").value
    splice.start shouldBe 1
    splice.deleteCount shouldBe 2
    splice.inserted shouldBe thinking
  }

  it should "not split a pair whose halves happen to match" in {
    // Both emoji start with the same high surrogate. A naive common prefix would stop between
    // the two units and hand back an offset in the middle of a character.
    val one = "😀" // grinning
    val two = "😁" // beaming: same high surrogate

    val splice = NativeInputReader.delta(one, two).value
    splice.start shouldBe 0
    splice.deleteCount shouldBe 2
    splice.inserted shouldBe two
  }

  it should "apply to the string it came from" in {
    // The property that matters, checked on every case above at once.
    val cases = Vector(
      ("Hallo", "Hallo!"),
      ("Hallo", "Hall"),
      ("abXYZcd", "abQcd"),
      ("", "neu"),
      ("weg", ""),
      ("aXa", "aYa"),
      ("😀x", "🤔x")
    )

    cases.foreach { (before, after) =>
      NativeInputReader.delta(before, after) match
        case None => before shouldBe after
        case Some(splice) =>
          val applied =
            before.take(splice.start) + splice.inserted +
              before.drop(splice.start + splice.deleteCount)
          applied shouldBe after
    }
  }

  // ---------------------------------------------------------------------------------------
  // Tab (§22)
  // ---------------------------------------------------------------------------------------

  "Tab" should "leave the editor by default" in {
    // §22: "Tab verlaesst die normale Editierflaeche."
    TabRule.handlesTab(TabPolicy.LeavesEditor, escapeArmed = false) shouldBe false
    TabRule.handlesTab(TabPolicy.LeavesEditor, escapeArmed = true) shouldBe false
  }

  it should "indent only where that was switched on" in {
    TabRule.handlesTab(TabPolicy.IndentsUntilEscape, escapeArmed = false) shouldBe true
  }

  it should "always have a way out" in {
    // "Keine permanente Keyboard-Falle." Escape first, then Tab, and the focus moves on -- the
    // convention other editors use, which is what a stuck user will try.
    TabRule.handlesTab(TabPolicy.IndentsUntilEscape, escapeArmed = true) shouldBe false
    TabRule.arms("Escape") shouldBe true
    TabRule.arms("a") shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // Shortcuts
  // ---------------------------------------------------------------------------------------

  "A shortcut" should "compare letters in lower case and keep named keys" in {
    // `Ctrl+Shift+Z` arrives with `key == "Z"`. A table written in lower case would miss it, and
    // an editor would have undo but no redo.
    Shortcut.normalise("B") shouldBe "b"
    Shortcut.normalise("b") shouldBe "b"
    Shortcut.normalise("Tab") shouldBe "Tab"
    Shortcut.normalise("ArrowLeft") shouldBe "ArrowLeft"
  }

  // ---------------------------------------------------------------------------------------
  // Bindings
  // ---------------------------------------------------------------------------------------

  private def answer(label: String): InputBinding = { case InputIntent.InsertText(_) =>
    _ => Left(UpdateError.InvalidDocument(Vector.empty))
  }

  "Bindings" should "answer with the first that is defined" in {
    // Two modules may both answer one intent -- a list splits an item, rich text splits a block.
    // Which goes first is the application's decision, expressed by the order it assembles them.
    val bindings = InputBindings.of(answer("erster"), answer("zweiter"))

    bindings.resolve(InputIntent.InsertText("a")).isDefined shouldBe true
    bindings.resolve(InputIntent.InsertParagraph) shouldBe None
  }

  it should "let an editor without a feature simply not answer" in {
    // The point of a partial function here: no binding means the controller leaves the event
    // native. An editor without lists is not one that breaks on `insertUnorderedList`.
    InputBindings.empty.resolve(InputIntent.Format(NativeFormat.Bold)) shouldBe None
    KeyboardBindings.empty.resolve(Shortcut("b", primary = true)) shouldBe None
  }

  it should "compose in order" in {
    val first  = InputBindings.of(answer("erster"))
    val second = InputBindings.of(answer("zweiter"))

    (first ++ second).bindings.length shouldBe 2
  }

  extension [A](option: Option[A])
    private def value: A = option.getOrElse(fail("Erwartet: ein Wert, gefunden: None"))
}
