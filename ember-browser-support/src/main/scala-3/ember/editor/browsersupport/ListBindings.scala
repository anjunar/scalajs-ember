package ember.editor.browsersupport

import ember.editor.browser.*
import ember.editor.list.{ListCommands, ListKind}

/** Lists on the keyboard, and the one key that needs a rule of its own.
  *
  * ==Tab is opt-in, and it comes with a way out==
  *
  * §22: "Tab verlaesst die normale Editierflaeche. Listeneinrueckung oder Code-Tab ist ein
  * ausdruecklich aktiviertes Verhalten mit erreichbarer Ausstiegsmoeglichkeit. Keine permanente
  * Keyboard-Falle."
  *
  * So [[keyboard]] does '''not''' bind Tab. An application that wants list indentation on Tab takes
  * [[tabIndentation]] as well '''and''' sets [[TabPolicy.IndentsUntilEscape]] on the controller --
  * the policy is what provides the exit, and binding the key without it would build exactly the
  * trap §22 forbids.
  *
  * The escape is `Escape` and then `Tab`. It is the convention other editors already use, which
  * matters more here than elegance: a keyboard user who is stuck tries what worked elsewhere.
  */
object ListBindings:

  /** `Ctrl/Cmd+Shift+8` and `+7` -- the spelling Google Docs and Word share. */
  val keyboard: KeyboardBindings =
    KeyboardBindings.of {
      case Shortcut("8", true, true, false) =>
        session => session.dispatch(ListCommands.ToggleList, ListKind.Unordered)
      case Shortcut("7", true, true, false) =>
        session => session.dispatch(ListCommands.ToggleList, ListKind.Ordered)
      // Indentation without Tab, always available -- so that the Tab binding below stays
      // genuinely optional rather than the only way to indent.
      case Shortcut("]", true, false, false) => session => session.dispatch(ListCommands.Indent)
      case Shortcut("[", true, false, false) => session => session.dispatch(ListCommands.Outdent)
    }

  /** Tab and Shift+Tab. Only together with [[TabPolicy.IndentsUntilEscape]] (§22). */
  val tabIndentation: KeyboardBindings =
    KeyboardBindings.of {
      case Shortcut("Tab", false, false, false) => session => session.dispatch(ListCommands.Indent)
      case Shortcut("Tab", false, true, false)  => session => session.dispatch(ListCommands.Outdent)
    }

  /** The list intents the browser itself produces.
    *
    * A browser sends `insertUnorderedList` from its own context menu or from a mobile keyboard's
    * formatting bar. Without a binding those would edit the DOM behind the model.
    */
  val input: InputBindings =
    InputBindings.of {
      case InputIntent.Unknown("insertUnorderedList") =>
        session => session.dispatch(ListCommands.ToggleList, ListKind.Unordered)
      case InputIntent.Unknown("insertOrderedList") =>
        session => session.dispatch(ListCommands.ToggleList, ListKind.Ordered)
    }
