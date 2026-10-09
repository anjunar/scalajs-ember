package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** Why focus is being restored. The caller knows; this type makes them say so.
  *
  * §22: "Schliessen stellt Fokus nur im passenden Interaktionskontext wieder her. Hintergrund-
  * updates stehlen weder Page- noch Textarea-Fokus." There is no way to work out from a bookmark
  * alone which of those a call is, so the intent is a parameter and not a guess.
  */
enum FocusIntent:

  /** Put the selection back, never the focus. What a background update is allowed to do. */
  case SelectionOnly

  /** Focus only if the editor had it when the bookmark was taken. The dialog case. */
  case IfItWasOurs

  /** An explicit user gesture asking for the editor -- a "back to the text" button. */
  case Always
