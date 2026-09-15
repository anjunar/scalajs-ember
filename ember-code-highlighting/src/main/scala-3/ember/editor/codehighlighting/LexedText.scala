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

/** A block's text with its tokens, kept per line so an edit can reuse what it did not touch.
  *
  * ==The incremental step==
  *
  * [[update]] finds the first character that changed, relexes from the line that contains it, and
  * stops as soon as a relexed line ends where an old line began '''in the same state''' and the
  * rest of the text is unchanged. From there on the old lines are right, only shifted.
  *
  * The effect is the one an author expects: typing inside a line recolours that line; opening a
  * block comment recolours everything below it, because every line below now starts in a comment;
  * closing it again recolours the same lines back. Nothing in between is guessed.
  *
  * [[relexed]] says how many lines the last step lexed, so that a test can hold the claim to it.
  */
final class LexedText private (
    val text: String,
    val grammar: Grammar,
    val lines: Vector[LexedLine],
    val relexed: Int,
    maxLineChars: Int
):
  import LexedText.{commonPrefix, commonSuffix, lineEnd}

  def tokens: Vector[Token] = lines.flatMap(_.tokens)

  def update(next: String): LexedText =
    if next == text then new LexedText(text, grammar, lines, 0, maxLineChars)
    else
      val prefix = commonPrefix(text, next)
      val suffix = commonSuffix(text, next, math.min(text.length, next.length) - prefix)
      val delta  = next.length - text.length

      val first  = lineContaining(prefix)
      val kept   = lines.take(first)
      val result = Vector.newBuilder[LexedLine] ++= kept
      var start  = lines(first).start
      var state  = lines(first).entry
      var count  = 0
      var done   = false

      while !done do
        val end  = lineEnd(next, start)
        val line = Lexer.line(next, start, end, state, maxLineChars)
        result += LexedLine(start, end, state, line.tokens, line.exit)
        count += 1
        state = line.exit

        if end >= next.length then done = true
        else
          val following = end + 1
          // Unchanged from here to the end, and an old line started at the same text in the same
          // state: everything from it on is still right.
          if following >= next.length - suffix then
            val old = lineStartingAt(following - delta)
            if old >= 0 && lines(old).entry == state then
              result ++= lines.drop(old).map(_.shift(delta))
              done = true
          if !done then start = following

      new LexedText(next, grammar, result.result(), count, maxLineChars)

  private def lineContaining(offset: Int): Int =
    var low  = 0
    var high = lines.length - 1
    while low < high do
      val middle = (low + high + 1) >>> 1
      if lines(middle).start <= offset then low = middle else high = middle - 1
    low

  private def lineStartingAt(offset: Int): Int =
    val index = lineContaining(offset)
    if index < lines.length && lines(index).start == offset then index else -1

object LexedText:

  def lex(grammar: Grammar, text: String, maxLineChars: Int = Int.MaxValue): LexedText =
    val lines = Vector.newBuilder[LexedLine]
    var start = 0
    var state = grammar.start
    var count = 0
    var done  = false
    while !done do
      val end  = lineEnd(text, start)
      val line = Lexer.line(text, start, end, state, maxLineChars)
      lines += LexedLine(start, end, state, line.tokens, line.exit)
      count += 1
      state = line.exit
      if end >= text.length then done = true else start = end + 1
    new LexedText(text, grammar, lines.result(), count, maxLineChars)

  private def lineEnd(text: String, from: Int): Int =
    val index = text.indexOf('\n', from)
    if index < 0 then text.length else index

  private def commonPrefix(a: String, b: String): Int =
    val limit = math.min(a.length, b.length)
    var index = 0
    while index < limit && a.charAt(index) == b.charAt(index) do index += 1
    index

  private def commonSuffix(a: String, b: String, limit: Int): Int =
    var count = 0
    while count < limit && a.charAt(a.length - 1 - count) == b.charAt(b.length - 1 - count) do
      count += 1
    count
