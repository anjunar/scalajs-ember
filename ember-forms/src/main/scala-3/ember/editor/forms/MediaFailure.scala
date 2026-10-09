package ember.editor.forms

import ember.editor.core.*
import ember.editor.image.*
import scala.concurrent.Future
import scala.util.control.NonFatal

final case class MediaFailure(message: String) extends EditorError
