package ember.editor.json

import ember.editor.core.*

/** Eine Markierung mit Nutzdaten. */
final case class Highlight(color: String) extends TextMark:
  val markId: MarkId = Highlight.markId

object Highlight:
  val markId: MarkId = MarkId("test.highlight/1")
