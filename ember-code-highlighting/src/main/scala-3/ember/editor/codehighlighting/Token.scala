package ember.editor.codehighlighting

/** A coloured range of a code block's text.
  *
  * Offsets are UTF-16 code units into the whole block, half-open -- the unit §11 uses for every
  * text position, and the one a DOM `Range` takes. A token never covers a `\n`: lines are lexed one
  * at a time, and a token that crossed one would make the incremental relexing unsound.
  */
final case class Token(start: Int, end: Int, kind: TokenKind):

  def length: Int = end - start

  def shift(delta: Int): Token = Token(start + delta, end + delta, kind)
