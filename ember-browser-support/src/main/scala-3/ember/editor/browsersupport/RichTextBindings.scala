package ember.editor.browsersupport

import ember.editor.browser.*
import ember.editor.core.*
import ember.editor.richtext.*

/** Which browser intent becomes which rich-text command.
  *
  * ==Why this is a module of its own==
  *
  * §7 forbids `ember-browser` from importing a feature, and the rule earns its keep here. An
  * intent is browser vocabulary -- `formatBold` is what the specification calls the event, not
  * what this editor calls the mark. A profile that has no bold has no binding for it, and the
  * controller then leaves the event native instead of swallowing it. That is not a special case;
  * it is the same mechanism for every intent nobody claims.
  *
  * ==What is deliberately not bound==
  *
  * `Transfer` -- paste, drop, cut. Those are §21's clipboard module, and a binding here that
  * inserted the plain text would quietly become the paste implementation, with none of §21's
  * sanitising. Until that module exists, paste stays native and `input` imports the result,
  * which is the honest state of affairs rather than a half one.
  */
object RichTextBindings:

  /** Typing, Enter, Backspace, Delete -- the four that make a text field a text field. */
  val editing: InputBinding =
    case InputIntent.InsertText(text) =>
      session => session.dispatch(RichText.InsertText, text)

    // Autocorrect replaces a range the user did not select. Until the target ranges are read and
    // applied (P23 brings the DOM abgleich that makes that safe), the honest answer is to let the
    // browser do it and import the result -- so `ReplaceText` is not bound here.

    case InputIntent.InsertParagraph =>
      session => session.dispatch(RichText.InsertParagraph)

    case InputIntent.InsertLineBreak =>
      session => session.dispatch(RichText.InsertBreak, BreakKind.Hard)

    case InputIntent.Delete(Direction.Backward, _) =>
      session => session.dispatch(RichText.DeleteBackward)

    case InputIntent.Delete(Direction.Forward, _) =>
      session => session.dispatch(RichText.DeleteForward)

  /** The formats this profile has. Superscript and subscript are not among them.
    *
    * §8.2 leaves the set to the profile, and an unbound format is not a gap -- it is an editor
    * that does not offer it. The browser is then free to do nothing, which is what it does.
    */
  val formatting: InputBinding =
    case InputIntent.Format(NativeFormat.Bold) =>
      session => session.dispatch(RichText.ToggleMark, StandardMarks.Strong)
    case InputIntent.Format(NativeFormat.Italic) =>
      session => session.dispatch(RichText.ToggleMark, StandardMarks.Emphasis)
    case InputIntent.Format(NativeFormat.Underline) =>
      session => session.dispatch(RichText.ToggleMark, StandardMarks.Underline)
    case InputIntent.Format(NativeFormat.StrikeThrough) =>
      session => session.dispatch(RichText.ToggleMark, StandardMarks.Strike)

  val input: InputBindings = InputBindings.of(editing, formatting)

  /** The shortcuts §22 expects a rich-text editor to have.
    *
    * Plain Enter and Backspace are '''not''' here. §15.2: "Text generell ueber Input-Pipeline"
    * -- a keydown table cannot see dictation, autocorrect or a mobile keyboard. Shift+Enter is
    * the one exception, and the comment on it says why the engines forced it.
    */
  val keyboard: KeyboardBindings =
    KeyboardBindings.of(
      {
        // Shift+Enter is here, and Enter is not. The engines disagree about the first one:
        // WebKit reports `insertParagraph` for it, so the intent path would split the block
        // instead of inserting a break. A prevented `keydown` produces no `beforeinput` at all,
        // which makes the keyboard route the one place where all three engines agree.
        //
        // Enter needs no such treatment -- `insertParagraph` means the same thing everywhere --
        // and §15.2 keeps text on the input pipeline for good reasons.
        case Shortcut("Enter", false, true, false) =>
          session => session.dispatch(RichText.InsertBreak, BreakKind.Hard)
        case Shortcut("b", true, false, false) =>
          session => session.dispatch(RichText.ToggleMark, StandardMarks.Strong)
        case Shortcut("i", true, false, false) =>
          session => session.dispatch(RichText.ToggleMark, StandardMarks.Emphasis)
        case Shortcut("u", true, false, false) =>
          session => session.dispatch(RichText.ToggleMark, StandardMarks.Underline)
        case Shortcut("e", true, false, false) =>
          session => session.dispatch(RichText.ToggleMark, StandardMarks.InlineCode)
      }
    )
