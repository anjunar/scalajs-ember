package ember.editor.forms

import ember.editor.browser.*
import ember.editor.clipboard.*
import ember.editor.core.*
import org.scalajs.dom
import scala.scalajs.js
import scala.util.control.NonFatal

object BrowserMediaPreviews:
  def in(window: dom.Window): MediaPreviews[dom.File] = new MediaPreviews[dom.File]:
    private def urls                           = window.asInstanceOf[js.Dynamic].URL
    def create(file: dom.File): Option[String] = Some(
      urls.createObjectURL(file).asInstanceOf[String]
    )
    def revoke(url: String): Unit = urls.revokeObjectURL(url): Unit
