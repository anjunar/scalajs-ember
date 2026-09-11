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

/** Everything this repository can wire, assembled.
  *
  * ==Order matters, and it is stated here rather than discovered==
  *
  * [[InputBindings]] and [[KeyboardBindings]] both resolve first match wins. Where two modules
  * answer the same thing, the one listed earlier decides -- so the list below is a design decision,
  * not a convenience.
  *
  * Code comes before rich text for Tab-free shortcuts that overlap (`Ctrl+Shift+E` for a block
  * versus `Ctrl+E` for inline code -- different shortcuts, listed near each other on purpose so
  * that the next person who adds one sees the neighbourhood).
  *
  * History comes first among the input bindings: `historyUndo` must never fall through to anything
  * else, because the thing it would fall through to is the browser's own undo stack.
  */
object EditorBindings:

  /** Rich text alone: typing, Enter, deletion, the four marks. */
  val richText: InputBindings = RichTextBindings.input

  val richTextKeyboard: KeyboardBindings = RichTextBindings.keyboard

  /** Everything wired: history, lists, rich text. */
  def everything: InputBindings =
    HistoryBindings.input ++ ListBindings.input ++ RichTextBindings.input

  /** Tab and Shift+Tab for code '''and''' lists, in that order.
    *
    * Both bind the same key, and both commands report `Pass` where they are not responsible, so the
    * controller tries them in turn. Code first: inside a code block a Tab indents the line, and a
    * list item inside one would be the stranger case.
    *
    * Only together with [[TabPolicy.IndentsUntilEscape]] (§22).
    */
  val tabIndentation: KeyboardBindings =
    CodeBindings.tabIndentation ++ ListBindings.tabIndentation

  /** The shortcuts that need no application decision.
    *
    * Tab is not among them, and neither is the link dialog -- both need something this module
    * cannot supply on its own (§22's exit, and a URL).
    */
  def everythingKeyboard: KeyboardBindings =
    HistoryBindings.keyboard ++ ListBindings.keyboard ++ CodeBindings.keyboard ++
      LinkBindings.keyboard ++ RichTextBindings.keyboard
