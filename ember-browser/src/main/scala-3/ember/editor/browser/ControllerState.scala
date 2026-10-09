package ember.editor.browser

import ember.editor.core.*
import ember.editor.html.HtmlSupport
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** The controller's state machine (§15.2).
  *
  * > Zustaende: `Detached → Hydrating → Ready ↔ Composing → Recovering`, terminal `Disposed`.
  *
  * P22 implements `Ready`. `Composing` is entered and left, and while it lasts nothing is taken
  * over -- the full protocol with its write lock and its completion rules is P23. `Recovering` is
  * entered when a native change cannot be imported; what P22 owes there is to notice, to stop
  * claiming input, and to keep the text (§15.4). Repair is P23.
  */
enum ControllerState:
  case Detached, Ready, Composing, Recovering, Disposed
