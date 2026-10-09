package ember.editor.json

import ember.editor.core.*

/** Eine Markierung ohne Nutzdaten. */
case object Marker extends TextMark:
  val markId: MarkId = MarkId("test.marker/1")
