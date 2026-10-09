package ember.editor.foreign

import ember.editor.core.*

/** Markierungen des Fremdmoduls. Der Kern bringt keine mit (§8.2). */
final case class NamedMark(name: String) extends TextMark:
  def markId: MarkId = MarkId(name)
