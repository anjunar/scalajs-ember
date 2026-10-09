package ember.editor.browser

import ember.editor.core.*
import org.scalajs.dom

import scala.scalajs.js

/** Maps a key combination to a dispatch. Partial, for the same reason [[InputBinding]] is. */
type KeyBinding = PartialFunction[Shortcut, EditorSession => Either[UpdateError, DispatchOutcome]]
