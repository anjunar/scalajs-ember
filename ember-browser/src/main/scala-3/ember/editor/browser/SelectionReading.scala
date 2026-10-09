package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** What a read found. */
enum SelectionReading:

  /** No selection in this document at all. */
  case Absent

  /** There is one, but not in this host. §11: the port reads only within its editing host. */
  case Outside

  /** There is one in this host, and it does not correspond to a model position. */
  case Unmappable(problem: PositionProblem)

  /** It is inside something the editor renders but does not own (§15.2).
    *
    * A native control inside an atom view, today. The document does have a position for the atom --
    * [[DomPositionMap.toPoint]] gives the boundary in its parent -- but a caret that went into a
    * textarea is that textarea's caret, and importing it would overwrite the document selection
    * every time someone clicked a widget.
    */
  case Foreign(owner: NodeId)

  case Mapped(selection: RangeSelection)

  def toOption: Option[RangeSelection] = this match
    case Mapped(selection) => Some(selection)
    case _                 => None
