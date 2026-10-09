package ember.editor.browsersupport

import ember.editor.browser.*
import ember.editor.code.{CodeCommands, CodeInfo}

/** Code blocks on the keyboard.
  *
  * ==Tab, again==
  *
  * §22 names "Code-Tab" in the same sentence as list indentation, and it gets the same treatment:
  * [[tabIndentation]] is separate, and it only makes sense together with
  * [[TabPolicy.IndentsUntilEscape]]. A code block is the place where a Tab trap is most tempting
  * and most harmful -- a keyboard user who lands in one and cannot leave has lost the page.
  */
object CodeBindings:

  val keyboard: KeyboardBindings =
    KeyboardBindings.of {
      // The spelling most editors share for a fenced block.
      case Shortcut("e", true, true, false) =>
        session => session.dispatch(CodeCommands.ToggleCodeBlock, CodeInfo.empty)
    }

  /** Tab and Shift+Tab inside code. Only with [[TabPolicy.IndentsUntilEscape]] (§22). */
  val tabIndentation: KeyboardBindings =
    KeyboardBindings.of {
      case Shortcut("Tab", false, false, false) =>
        session => session.dispatch(CodeCommands.IndentLine)
      case Shortcut("Tab", false, true, false) =>
        session => session.dispatch(CodeCommands.OutdentLine)
    }
