package ember.editor.browser

import ember.editor.core.*
import ember.editor.html.HtmlSupport
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** What the pre-commit rule asks, once the controller exists.
  *
  * A session is built before its controller -- the controller needs the view, the view needs the
  * session. The rule has to be in place from the first commit, so it asks through this rather than
  * holding a controller it could not have been given.
  */
final class CompositionHolder:

  private var controller: Option[BrowserInputController] = None

  def bind(value: BrowserInputController): Unit = controller = Some(value)

  def release(): Unit = controller = None

  def runningComposition: Option[Long] = controller.flatMap(_.composition).map(_.id)
