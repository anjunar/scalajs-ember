package ember.editor.browser

import ember.editor.core.*
import org.scalajs.dom

import scala.scalajs.js

/** Whether `Tab` may be taken over right now.
  *
  * Pure, because it is a rule and not an event handler: a policy, plus whether `Escape` was the key
  * pressed just before.
  */
object TabRule:

  val TabKey: String    = "Tab"
  val EscapeKey: String = "Escape"

  def handlesTab(policy: TabPolicy, escapeArmed: Boolean): Boolean =
    policy match
      case TabPolicy.LeavesEditor       => false
      case TabPolicy.IndentsUntilEscape => !escapeArmed

  /** Whether pressing this key arms the escape hatch. */
  def arms(key: String): Boolean = key == EscapeKey
