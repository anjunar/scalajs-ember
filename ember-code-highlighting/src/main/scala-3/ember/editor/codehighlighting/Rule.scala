package ember.editor.codehighlighting

import java.util.regex.Pattern

import scala.collection.mutable

/** A pattern, what its match is, and what it does.
  *
  * ==Matching==
  *
  * Rules are tried in order at the current position and the first match wins, the way Monarch and
  * Prism grammars read. The pattern is anchored at the position (`lookingAt`) and cannot see past
  * the end of the line, nor past the point where an embedding ends.
  *
  * A match must consume something, unless the rule changes the stack: a zero-width `(?=\})` that
  * pops is how a CSS value ends at a brace it leaves for the block to read.
  *
  * ==Guards==
  *
  * Some decisions cannot be made by a pattern that only sees forward. Whether `/` starts a regular
  * expression or divides depends on what came before it, and whether `#` starts a shell comment
  * depends on the character before it. A guard gets the line and the position and answers that --
  * it is a lookbehind that is not limited by the region, and it costs nothing when it says no.
  */
final class Rule private (
    val pattern: Pattern,
    val kind: Option[TokenKind],
    val groupKinds: Vector[Option[TokenKind]],
    val ops: Vector[StackOp],
    val guard: Rule.Guard,
    val words: Option[Set[String]]
) extends RuleEntry:

  def push(state: String): Rule = withOps(StackOp.Push(state))

  def pop: Rule = withOps(StackOp.Pop)

  def embed(grammar: => Grammar, end: String): Rule =
    withOps(StackOp.Embed(() => grammar, Pattern.compile(end)))

  /** See [[StackOp.EmbedMatched]]. End patterns are shared per source string, so that two frames
    * for the same fence compare equal.
    */
  def embedMatched(select: String => Option[(Grammar, String)]): Rule =
    withOps(
      StackOp.EmbedMatched(text => select(text).map((grammar, end) => grammar -> Patterns.of(end)))
    )

  def when(test: Rule.Guard): Rule =
    val previous = guard
    new Rule(
      pattern,
      kind,
      groupKinds,
      ops,
      (line, at) => previous(line, at) && test(line, at),
      words
    )

  private def withOps(op: StackOp): Rule =
    new Rule(pattern, kind, groupKinds, ops :+ op, guard, words)

object Rule:

  /** The line and a position in it. */
  type Guard = (String, Int) => Boolean

  private val always: Guard = (_, _) => true

  /** A match that becomes one token. */
  def token(regex: String, kind: TokenKind): Rule =
    new Rule(Pattern.compile(regex), Some(kind), Vector.empty, Vector.empty, always, None)

  /** A match that is consumed and not coloured. Also what keeps an identifier from being read as a
    * keyword that happens to be its prefix.
    */
  def skip(regex: String): Rule =
    new Rule(Pattern.compile(regex), None, Vector.empty, Vector.empty, always, None)

  /** A match whose capture groups become tokens of their own -- `def` and the name after it. */
  def groups(regex: String, kinds: Option[TokenKind]*): Rule =
    new Rule(Pattern.compile(regex), None, kinds.toVector, Vector.empty, always, None)

  /** A word from a list. The pattern reads the whole word first, so `valid` is not `val` + `id`. */
  def words(kind: TokenKind, identifier: String, values: String*): Rule =
    new Rule(
      Pattern.compile(identifier),
      Some(kind),
      Vector.empty,
      Vector.empty,
      always,
      Some(values.toSet)
    )

  // ---------------------------------------------------------------------------------------
  // Guards used by more than one grammar
  // ---------------------------------------------------------------------------------------

  /** Only whitespace before the position on this line. */
  val atLineStart: Guard = (line, at) =>
    var index = 0
    while index < at && line.charAt(index).isWhitespace do index += 1
    index == at

  /** The previous character is not part of a word. */
  val atWordStart: Guard = (line, at) =>
    at == 0 || {
      val previous = line.charAt(at - 1)
      !(previous.isLetterOrDigit || previous == '_' || previous == '$')
    }
