package ember.editor.markdown

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Two marks nobody else has. */
private case object Loud extends TextMark:
  val markId: MarkId = MarkId("test.loud/1")
