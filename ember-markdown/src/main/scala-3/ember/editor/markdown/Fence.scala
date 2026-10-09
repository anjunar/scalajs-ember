package ember.editor.markdown

/** The fence of a fenced code block.
  *
  * @param char
  *   `` ` `` or `~`. Needed for the export side: a block whose content holds backticks has to be
  *   fenced with something else, or with a longer run.
  * @param length
  *   how many fence characters opened it, at least three.
  * @param info
  *   the info string, unescaped and trimmed. Empty when there was none. §18.2 wants the language
  *   typed -- that typing happens in the adapter, because [[CodeLanguage]] lives in `ember-code`
  *   and this module does not know it.
  */
final case class Fence(char: Char, length: Int, info: String)
