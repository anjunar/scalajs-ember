package ember.editor.browser

import ember.editor.core.*
import ember.editor.html.HtmlSupport
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** What happened to a native input session (§15.3).
  *
  * Reported rather than acted on, because the two things that care live above this module: the
  * history, which makes one undo group of a composition (§14), and the application, which may want
  * to say that something is being typed.
  */
enum CompositionEvent:
  case Started(session: Long, region: ProtectedRegion)
  case Finished(session: Long, released: Vector[IntentOutcome])
  case Discarded(session: Long, reason: String, dropped: Vector[String])
