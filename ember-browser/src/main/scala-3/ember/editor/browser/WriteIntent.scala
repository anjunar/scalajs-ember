package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** Whether a write insists on happening. */
enum WriteIntent:

  /** Write only into a focused host. The right default, and the one §22 asks for. */
  case FollowFocus

  /** Write regardless of focus -- a deliberate "select this" from a command.
    *
    * This port never '''calls''' focus; that decision is [[FocusController]]'s. But placing a
    * selection inside an editable host moves the focus there anyway in Chromium, and a browser test
    * found it. So `Explicit` is not "write without touching focus" -- it is "write, and accept that
    * the engine may follow". Which is why it is not the default.
    */
  case Explicit
