package ember.editor.browser

import ember.editor.core.*
import ember.editor.html.HtmlSupport
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** What the controller did with one event. */
enum InputOutcome:

  /** A command took it. The native action is prevented. */
  case TakenOver(intent: InputIntent)

  /** The editor deliberately said no -- readonly, schema, limit. Also prevented.
    *
    * P22's risk list is explicit that this is not the same as "unhandled":
    * "Readonly-/Limit-/Schema-Reject verhindert native Ersatzmutation." A refusal that let the
    * event through would leave the browser editing a document the model rejected.
    */
  case Refused(intent: InputIntent, reason: String)

  /** Nothing here answers this. The browser does it, and `input` imports the result. */
  case LeftNative(intent: InputIntent)

  /** The event did not belong to this editor (§15.2, Event-Ownership). */
  case NotOurs

  /** A native change was read back into the document. */
  case Imported(node: NodeId, splice: TextSplice)

  /** Already handled in `beforeinput`; this is its echo. */
  case Deduplicated

  /** A native change could not be expressed as a document change (§15.4). */
  case Unimported(reason: String)

  /** The controller is not in a state that processes input. */
  case Idle(state: ControllerState)

  /** A change was refused because a composition is running (§15.3). */
  case Busy(session: Long)

  /** A `keydown` that is plainly text, and therefore not the keyboard layer's business.
    *
    * §15.2: "Text generell ueber Input-Pipeline." A letter without a modifier is never a shortcut,
    * and the controller does not even consult its table for one -- so this is not a decision and is
    * not reported to observers.
    */
  case NotAShortcut(key: String)

  /** Whether the caller must call `preventDefault`.
    *
    * The rule P22 names, in one place: "Event-Ownership und erfolgreiche Modelluebernahme '''oder
    * bewusste Ablehnung''' bestimmen preventDefault, nicht die blosse Existenz eines Handlers."
    */
  def preventsDefault: Boolean = this match
    case TakenOver(_)  => true
    case Refused(_, _) => true
    case _             => false

  /** Whether this is worth telling an observer about.
    *
    * A keystroke the controller never looked at is not news. Reporting one per character would bury
    * the outcomes that matter -- a refusal, a failed import -- under the typing.
    */
  def isNotable: Boolean = this match
    case NotAShortcut(_) => false
    case _               => true
