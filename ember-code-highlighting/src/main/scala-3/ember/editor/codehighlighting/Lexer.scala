package ember.editor.codehighlighting

import java.util.regex.{Matcher, Pattern}

import scala.collection.mutable

/** Lexes one line.
  *
  * ==Why the line and not the block==
  *
  * Because the line is the unit of reuse. Everything a line needs from the lines before it is the
  * [[LexState]] it starts in; given that, it produces its tokens and the state the next line starts
  * in, and nothing else. [[LexedText]] builds the incremental relexing on exactly that contract.
  *
  * Matching runs on the line's own substring. A matcher over the whole block with a region would
  * see the same characters, but Scala.js' regex engine may copy its input per match, and a copy of
  * a line is cheap where a copy of a block is not.
  */
object Lexer:

  final case class LineResult(tokens: Vector[Token], exit: LexState)

  /** Lexes `text[from, until)`, which must not contain a line break.
    *
    * `maxLineChars` protects the editor from a minified bundle pasted into a code block: such a
    * line stays uncoloured, and the state passes through it unchanged, so the lines after it are
    * still coloured correctly as far as the lexer can know.
    */
  def line(
      text: String,
      from: Int,
      until: Int,
      entry: LexState,
      maxLineChars: Int = Int.MaxValue
  ): LineResult =
    if until - from > maxLineChars then LineResult(Vector.empty, closeLine(entry))
    else
      val line     = text.substring(from, until)
      val tokens   = new TokenBuffer(from)
      val matchers = mutable.HashMap.empty[Pattern, Matcher]
      var frames   = entry.frames
      var position = 0
      var steps    = 0
      // A rule that neither consumes nor changes the stack is refused below, but two zero-width
      // rules that push and pop each other would still spin. The budget is the last line of
      // defence, generous enough never to cut off an honest grammar.
      val budget = line.length * 4 + 64

      def matcher(pattern: Pattern): Matcher =
        matchers.getOrElseUpdate(pattern, pattern.matcher(line))

      while position < line.length && steps < budget do
        steps += 1
        val boundary = frames.find(_.end.isDefined)
        val limit    = boundary.flatMap(_.end) match
          case Some(end) =>
            val finder = matcher(end)
            finder.region(position, line.length)
            if finder.find() then finder.start() else line.length
          case None => line.length

        if boundary.isDefined && limit == position then
          // The embedded language ends here. Everything above the boundary goes with it, and the
          // outer grammar reads the closing tag itself.
          frames = frames.dropWhile(frame => !(frame eq boundary.get)).tail
        else
          val rules   = frames.head.definition.rules
          var index   = 0
          var matched = false
          while !matched && index < rules.length do
            val rule = rules(index)
            if rule.guard(line, position) then
              val attempt = matcher(rule.pattern)
              attempt.region(position, limit)
              if attempt.lookingAt()
                && (attempt.end() > position || rule.ops.nonEmpty)
                && rule.words.forall(_.contains(attempt.group()))
              then
                emit(rule, attempt, tokens)
                frames = applyOps(rule.ops, frames)
                position = attempt.end()
                matched = true
            index += 1

          if !matched then
            // Nothing claims this character: it is plain text. Step over a surrogate pair as one,
            // so that no rule is ever tried in the middle of a code point.
            position +=
              (if Character.isHighSurrogate(line.charAt(position)) && position + 1 < line.length
               then 2
               else 1)

      LineResult(tokens.result(), closeLine(LexState(frames)))

  private def emit(rule: Rule, matcher: Matcher, tokens: TokenBuffer): Unit =
    if rule.groupKinds.nonEmpty then
      var group = 1
      while group <= rule.groupKinds.length do
        rule.groupKinds(group - 1).foreach { kind =>
          val start = matcher.start(group)
          if start >= 0 then tokens.add(start, matcher.end(group), kind)
        }
        group += 1
    else rule.kind.foreach(kind => tokens.add(matcher.start(), matcher.end(), kind))

  private def applyOps(ops: Vector[StackOp], frames: List[Frame]): List[Frame] =
    ops.foldLeft(frames) { (stack, op) =>
      op match
        case StackOp.Push(state) => Frame(stack.head.grammar, state, None) :: stack
        // Never the bottom frame, and never a boundary: an inner grammar that pops too often must
        // not be able to leave its embedding by accident.
        case StackOp.Pop =>
          if stack.tail.nonEmpty && stack.head.end.isEmpty then stack.tail else stack
        case StackOp.Embed(grammar, end) =>
          val inner = grammar()
          Frame(inner, inner.initial, Some(end)) :: stack
    }

  /** The state the next line starts in: every line-bound state is left. */
  private def closeLine(state: LexState): LexState =
    val deepest = state.frames.lastIndexWhere(_.definition.lineBound)
    if deepest < 0 then state
    else
      val remaining = state.frames.drop(deepest + 1)
      if remaining.isEmpty then state.frames.last.grammar.start else LexState(remaining)

  /** Collects tokens in line order, joining neighbours of the same kind.
    *
    * A comment lexed character by character would otherwise become one token per character, and the
    * view would create one DOM range for each.
    */
  private final class TokenBuffer(offset: Int):
    private val tokens      = Vector.newBuilder[Token]
    private var last: Token = null

    def add(start: Int, end: Int, kind: TokenKind): Unit =
      if end > start then
        val absoluteStart = start + offset
        val absoluteEnd   = end + offset
        if last != null && last.kind == kind && last.end == absoluteStart then
          last = Token(last.start, absoluteEnd, kind)
        else
          if last != null then tokens += last
          last = Token(absoluteStart, absoluteEnd, kind)

    def result(): Vector[Token] =
      if last != null then tokens += last
      tokens.result()
