package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.{ContainerElement, DocumentView, TextRunElement}
import ui.core.render.DomNodes
import org.scalajs.dom

/** Why a position could not be mapped. */
enum PositionProblem:

  /** The DOM node is not inside the editing host (§11: the port reads only its own host). */
  case OutsideHost

  /** No document node owns this DOM node -- something someone else put into the host. */
  case Unowned

  /** The document node has no mounted component; the projection has not caught up. */
  case NotProjected(node: NodeId)

  /** The point names a node the document does not have. */
  case NoSuchNode(node: NodeId)

  /** The position is inside an atom, whose inside is not editor text (§8.1, §11). */
  case InsideAtom(node: NodeId)

  def render: String = this match
    case OutsideHost      => "Die Position liegt ausserhalb des Editing-Hosts."
    case Unowned          => "Zu dieser DOM-Position gehoert kein Dokumentknoten."
    case NotProjected(id) => s"`${id.value}` ist nicht projiziert."
    case NoSuchNode(id)   => s"`${id.value}` gibt es im Dokument nicht."
    case InsideAtom(id)   => s"`${id.value}` ist atomar; sein Inneres ist kein Textbereich."
