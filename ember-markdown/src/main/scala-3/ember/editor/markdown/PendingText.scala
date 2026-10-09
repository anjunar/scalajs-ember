package ember.editor.markdown

import scala.collection.mutable

/** A text node while it is still being built.
  *
  * Emphasis processing shortens the literal of an opener or closer as it consumes delimiters, so
  * the text is mutable until the parse ends. The final [[MarkdownInline.Text]] is built afterwards,
  * from whatever is left.
  */
private[markdown] final class PendingText(var literal: String, var start: Int, var end: Int)
