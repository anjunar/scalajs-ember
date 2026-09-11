package ember.editor.browsersupport

import ember.editor.browser.*
import ember.editor.link.{LinkCommands, LinkTarget}

/** Links on the keyboard.
  *
  * ==Why there is no `insertLink` intent binding==
  *
  * Because a link needs a URL, and a `beforeinput[insertLink]` does not carry one the editor may
  * trust. §21 and §22 both put the asking in the application: a dialog collects the target, the
  * application validates it against its [[ember.editor.link.LinkUrlPolicy]], and then dispatches
  * the command. A binding here would have to invent a URL or open UI, and this module has no UI.
  *
  * What it does offer is the shortcut, so that the application can hang its dialog on something,
  * and the removal, which needs no input at all.
  */
object LinkBindings:

  /** Removing a link needs nothing from the user, so it can be bound outright. */
  val keyboard: KeyboardBindings =
    KeyboardBindings.of {
      case Shortcut("k", true, true, false) => session => session.dispatch(LinkCommands.RemoveLink)
    }

  /** The shortcut that opens an application's link dialog.
    *
    * Takes the dialog as a function rather than owning one: §13 puts UI in extensions, and a
    * binding module that popped up its own input field would be an editor deciding what an
    * application's dialogs look like.
    */
  def keyboardWith(ask: () => Option[LinkTarget]): KeyboardBindings =
    KeyboardBindings.of {
      case Shortcut("k", true, false, false) =>
        session =>
          ask() match
            case Some(target) => session.dispatch(LinkCommands.SetLink, target)
            case None         => session.dispatch(LinkCommands.RemoveLink)
    } ++ keyboard
