package ember.editor.browser

import ember.editor.core.*
import org.scalajs.dom

import scala.scalajs.js

/** Which keydowns the keyboard layer looks at at all.
  *
  * §15.2: "Text generell ueber Input-Pipeline." A key without a modifier that produces a single
  * character is typing, and typing goes through `beforeinput` -- where dictation, autocorrect and a
  * mobile keyboard also arrive, none of which a keydown ever sees.
  */
object KeyboardRule:

  def isShortcutCandidate(shortcut: Shortcut): Boolean =
    shortcut.primary || shortcut.alt || shortcut.key.length > 1

  /** Keys whose native default edits -- or, in some engines, navigates.
    *
    * `Backspace` is on the list for the second reason: WebKit still takes it as "go back" in a
    * focused element that is not editable, which turns a readonly editor into a way of losing the
    * page.
    */
  val editsOrNavigates: Set[String] = Set("Backspace", "Delete", "Enter")
