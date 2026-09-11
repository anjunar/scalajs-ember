package ember.editor.browser

import ember.editor.core.*

/** Which way a deletion goes. */
enum Direction:
  case Backward, Forward

/** How much a deletion takes.
  *
  * The vocabulary of the Input Events specification, not of the editor. What a word boundary is
  * belongs to the `TextBoundaryService` (§11); what the browser '''asked for''' belongs here.
  */
enum Granularity:
  case Character, Word, Line, Selection

/** A formatting request the browser made, by its own name.
  *
  * Deliberately not a [[TextMark]]. A mark is a feature's idea (§8.2), and `ember-browser` is not
  * allowed to know the features (§7). The binding turns `Bold` into whatever the profile in use
  * calls bold -- and a profile without one simply has no binding for it.
  */
enum NativeFormat:
  case Bold, Italic, Underline, StrikeThrough, Superscript, Subscript

enum HistoryDirection:
  case Undo, Redo

/** How content arrived from outside the document. */
enum TransferKind:
  case Paste, Drop, Cut

/** What the browser says the user wants.
  *
  * ==Why a separate vocabulary==
  *
  * Because `inputType` is a string from a specification that is still a Working Draft (§15.2),
  * and because the same intent arrives by several routes: `insertText` from typing,
  * `insertReplacementText` from autocorrect, `insertFromPaste` from the clipboard. Handling
  * strings at the call site would spread the specification's spelling through the editor and make
  * every route a separate case.
  *
  * ==Why it is not a command==
  *
  * A command belongs to a feature module. §7 forbids `ember-browser` from importing one, and it
  * is the right rule: a profile without lists has no meaning for `insertUnorderedList`, and one
  * without bold has none for `formatBold`. The intent says what happened; an [[InputBindings]]
  * decides whether this editor has an answer. No binding means no take-over, which means the
  * event stays native -- exactly what §15.2 asks for.
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
    * The question a readonly editor asks. §22: "Readonly-Policy verhindert auch
    * programmgesteuerte User-Editing-Commands" -- and refusing has to be '''deliberate''', with a
    * `preventDefault`, or the browser edits the DOM behind a model that said no.
    */
  def editsDocument: Boolean = this match
    case Unknown(_) => false
    case _          => true

/** Maps browser intents to dispatches on a session.
  *
  * A partial function, and that is the whole design: where it is not defined, this editor has no
  * answer for that intent, and the controller leaves the event native instead of swallowing it.
  * An editor without lists is not an editor that breaks on `insertUnorderedList`; it is one that
  * lets the browser do whatever it would have done and imports the result.
  */
type InputBinding = PartialFunction[InputIntent, EditorSession => Either[UpdateError, DispatchOutcome]]

/** The bindings an editor has, in order.
  *
  * Ordered rather than keyed: two modules may both answer `InsertParagraph` -- a list wants to
  * split an item, rich text wants to split a block -- and which of them goes first is a decision
  * the application makes by the order it assembles them. That mirrors [[CommandRegistry]]'s
  * priorities without inventing a second priority scheme for the same thing.
  */
final class InputBindings private (val bindings: Vector[InputBinding]):

  /** Every binding that answers this intent, in order.
    *
    * All of them, not the first: a binding whose command reports `Pass` has '''not''' taken the
    * intent, and the next one must get its turn. §12 says that about commands -- "Nicht
    * zustaendig. Der naechste Handler kommt dran" -- and a binding layer that stopped at the
    * first match would break the rule one level up.
    *
    * The case that showed it: Tab indents a code line or a list item, and those are two different
    * commands. Stopping at the first would mean Tab in a list leaves the editor, because the code
    * command passed and nobody looked further.
    */
  def resolveAll(intent: InputIntent): Vector[EditorSession => Either[UpdateError, DispatchOutcome]] =
    bindings.collect { case binding if binding.isDefinedAt(intent) => binding(intent) }

  def resolve(intent: InputIntent): Option[EditorSession => Either[UpdateError, DispatchOutcome]] =
    resolveAll(intent).headOption

  def ++(other: InputBindings): InputBindings = new InputBindings(bindings ++ other.bindings)

object InputBindings:

  val empty: InputBindings = new InputBindings(Vector.empty)

  def of(bindings: InputBinding*): InputBindings = new InputBindings(bindings.toVector)
