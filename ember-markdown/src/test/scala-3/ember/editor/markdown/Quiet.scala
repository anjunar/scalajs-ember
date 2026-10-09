package ember.editor.markdown

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
private case object Quiet extends TextMark:
  val markId: MarkId = MarkId("test.quiet/1")
