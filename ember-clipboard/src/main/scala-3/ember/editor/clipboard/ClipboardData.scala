package ember.editor.clipboard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.json.*
import ember.editor.richtext.*

final case class ClipboardData(formats: Map[String, String]):
  def get(mime: String): Option[String] = formats.get(mime)
