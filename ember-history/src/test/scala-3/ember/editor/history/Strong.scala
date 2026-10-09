package ember.editor.history

import ember.editor.core.*

/** Eine Markierung, um die Mark-Konfigurationsregel aus §14 pruefen zu koennen. */
final case class Strong() extends TextMark:
  val markId: MarkId = MarkId("test.strong/1")
