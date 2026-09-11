package ember.editor.browsersupport

import ember.editor.browser.*
import ember.editor.history.HistoryCommands

/** The browser's undo stack, routed to the editor's own.
  *
  * §15.2 states the rule and the reason in one line:
  *
  * > `historyUndo/Redo`: Eigene History-Commands, definierte Grenzen bei NativeInput. Native und
  * > modellbasierte Undo-Stacks duerfen sich nicht widersprechen.
  *
  * Two stacks over one document is the kind of bug that looks like data loss. The browser
  * remembers DOM states the model never produced; the model remembers commits the browser never
  * saw. Whichever answers `Ctrl+Z` first wins, and the other one is then wrong about everything
  * that follows.
  *
  * So the browser's stack is never used. `beforeinput[historyUndo]` is taken over -- which also
  * prevents the native undo -- and the shortcut is bound as well, because §15.2 warns that a
  * `beforeinput` featuretest "garantiert nicht alle Inputtypen".
  *
  * ==Twice bound is not twice run==
  *
  * `Ctrl+Z` reaches the editor as a `keydown` '''and''' as `beforeinput[historyUndo]` in engines
  * that send both. The keydown handler takes it first and prevents the default, and no
  * `beforeinput` follows a prevented `keydown`. Where the keydown is not seen, the intent path
  * catches it. Where both somehow arrive, [[InputOperationLog]] is what keeps the second from
  * counting -- the same mechanism as for every other doubled action.
  */
object HistoryBindings:

  val input: InputBindings =
    InputBindings.of {
      case InputIntent.History(HistoryDirection.Undo) =>
        session => session.dispatch(HistoryCommands.Undo)
      case InputIntent.History(HistoryDirection.Redo) =>
        session => session.dispatch(HistoryCommands.Redo)
    }

  /** `Ctrl/Cmd+Z` and both spellings of redo.
    *
    * `Ctrl+Y` is the Windows spelling and `Ctrl/Cmd+Shift+Z` the one everywhere else. Binding
    * both costs one line and saves a user from an editor that has no redo on their keyboard.
    */
  val keyboard: KeyboardBindings =
    KeyboardBindings.of {
      case Shortcut("z", true, false, false) => session => session.dispatch(HistoryCommands.Undo)
      case Shortcut("z", true, true, false)  => session => session.dispatch(HistoryCommands.Redo)
      case Shortcut("y", true, false, false) => session => session.dispatch(HistoryCommands.Redo)
    }
