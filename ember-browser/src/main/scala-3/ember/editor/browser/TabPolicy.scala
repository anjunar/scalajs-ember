package ember.editor.browser

import ember.editor.core.*
import org.scalajs.dom

import scala.scalajs.js

/** What `Tab` does in this editor.
  *
  * §22 is unusually specific, and for a good reason -- this is the one key that can lock a keyboard
  * user inside a control:
  *
  * > Tab verlaesst die normale Editierflaeche. Listeneinrueckung oder Code-Tab ist ein >
  * ausdruecklich aktiviertes Verhalten mit erreichbarer Ausstiegsmoeglichkeit. Keine permanente >
  * Keyboard-Falle.
  */
enum TabPolicy:

  /** The default. `Tab` is never handled; focus moves on as it does everywhere else. */
  case LeavesEditor

  /** `Tab` indents -- but `Escape` first, then `Tab`, always leaves.
    *
    * The escape hatch is the part §22 requires, and it is deliberately the convention users already
    * know from other editors rather than a new one.
    */
  case IndentsUntilEscape
