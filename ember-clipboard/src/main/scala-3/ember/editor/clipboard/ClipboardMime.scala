package ember.editor.clipboard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.json.*
import ember.editor.richtext.*

object ClipboardMime:
  val Internal = "application/x-ember-editor+json"
  val Html     = "text/html"
  val Text     = "text/plain"
  val priority = Vector(Internal, Html, Text)
