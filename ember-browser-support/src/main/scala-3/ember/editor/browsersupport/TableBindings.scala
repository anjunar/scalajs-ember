package ember.editor.browsersupport

import ember.editor.browser.*
import ember.editor.core.*
import ember.editor.table.*
import ember.editor.ui.DocumentView
import org.scalajs.dom
import ui.core.render.DomNodes

import scala.scalajs.js

/** Tables on the keyboard (X01).
  *
  * ==Tab, with the same exit as everywhere==
  *
  * Tab moves to the next cell and Shift+Tab to the previous one -- the one convention every table
  * editor shares. It is still §22's opt-in: [[tabNavigation]] belongs with
  * [[TabPolicy.IndentsUntilEscape]], and Escape then Tab leaves the editor from inside a table
  * exactly as it does from a list. Outside a table the command reports `Pass`, so list and code
  * indentation keep the key.
  */
object TableBindings:

  val tabNavigation: KeyboardBindings =
    KeyboardBindings.of {
      case Shortcut("Tab", false, false, false) =>
        session => session.dispatch(TableCommands.MoveToCell, CellDirection.Next)
      case Shortcut("Tab", false, true, false) =>
        session => session.dispatch(TableCommands.MoveToCell, CellDirection.Previous)
    }

  /** Escape turns a cell selection back into a caret. `Pass` otherwise. */
  val keyboard: KeyboardBindings =
    KeyboardBindings.of { case Shortcut("Escape", false, false, false) =>
      session => session.dispatch(TableCommands.CollapseCellSelection)
    }
