package ember.editor.browser

import ember.editor.core.*

/** What the browser says the user wants.
  *
  * ==Why a separate vocabulary==
  *
  * Because `inputType` is a string from a specification that is still a Working Draft (§15.2), and
  * because the same intent arrives by several routes: `insertText` from typing,
  * `insertReplacementText` from autocorrect, `insertFromPaste` from the clipboard. Handling strings
  * at the call site would spread the specification's spelling through the editor and make every
  * route a separate case.
  *
  * ==Why it is not a command==
  *
  * A command belongs to a feature module. §7 forbids `ember-browser` from importing one, and it is
  * the right rule: a profile without lists has no meaning for `insertUnorderedList`, and one
  * without bold has none for `formatBold`. The intent says what happened; an [[InputBindings]]
  * decides whether this editor has an answer. No binding means no take-over, which means the event
  * stays native -- exactly what §15.2 asks for.
  */
enum InputIntent:

  /** Typed, dictated or composed text. */
  case InsertText(text: String)

  /** Autocorrect and friends: text that replaces the range the event names. */
  case ReplaceText(text: String)

  /** Enter: split the block. */
  case InsertParagraph

  /** Shift+Enter: a break inside the block. */
  case InsertLineBreak

  case Delete(direction: Direction, granularity: Granularity)

  case Format(format: NativeFormat)

  /** The browser's own undo stack asking to be used. §15.2 routes it to the editor's own. */
  case History(direction: HistoryDirection)

  /** Content from the clipboard or a drag. `text` is the plain text, when the event carried it. */
  case Transfer(kind: TransferKind, text: Option[String])

  /** Something this editor has no word for. Stays native and is imported afterwards. */
  case Unknown(inputType: String)

  /** Whether acting on this would change the document.
    *
    * The question a readonly editor asks. §22: "Readonly-Policy verhindert auch programmgesteuerte
    * User-Editing-Commands" -- and refusing has to be '''deliberate''', with a `preventDefault`, or
    * the browser edits the DOM behind a model that said no.
    */
  def editsDocument: Boolean = this match
    case Unknown(_) => false
    case _          => true
