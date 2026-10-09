package ember.editor.forms

import ember.editor.core.*

/** A range in the textarea, in UTF-16 units -- the same counting as everywhere else (§11). */
final case class SourceSelection(start: Int, end: Int):
  def isCollapsed: Boolean = start == end
