package ember.editor.codehighlighting

/** One lexed line: where it is, the state it starts and ends in, and its tokens (absolute). */
final case class LexedLine(
    start: Int,
    end: Int,
    entry: LexState,
    tokens: Vector[Token],
    exit: LexState
):

  def shift(delta: Int): LexedLine =
    if delta == 0 then this
    else LexedLine(start + delta, end + delta, entry, tokens.map(_.shift(delta)), exit)
