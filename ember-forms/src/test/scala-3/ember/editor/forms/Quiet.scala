package ember.editor.forms

import ember.editor.core.*
import ember.editor.json.*
import ember.editor.markdown.*
case object Quiet extends TextMark:
  val markId: MarkId = MarkId("test.quiet/1")
