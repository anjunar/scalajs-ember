package ember.editor.forms

import ember.editor.core.*
import ember.editor.json.*
import ember.editor.markdown.*

/** A mark with a Markdown spelling, and one without.
  *
  * [[Quiet]] is this suite's stand-in for the underline of §16: a mark the document can carry and
  * Markdown cannot write. It is what makes the pre-commit rejection observable without pulling in
  * `ember-rich-text`.
  */
case object Loud extends TextMark:
  val markId: MarkId = MarkId("test.loud/1")
