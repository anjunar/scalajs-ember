package ember.editor.clipboard

import ember.editor.browser.*
import ember.editor.core.*
import org.scalajs.dom
import scala.scalajs.js
import scala.util.control.NonFatal

/** Files are effects for the application/media adapter, never document payload. */
final case class ClipboardFileIntent(files: Vector[dom.File], destination: Bookmark, source: String)
