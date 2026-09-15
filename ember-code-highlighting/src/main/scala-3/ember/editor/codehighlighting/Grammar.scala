package ember.editor.codehighlighting

import java.util.regex.Pattern

import scala.collection.mutable

/** What a matched rule does to the lexer's state stack. */
enum StackOp:

  /** Enter another state of the same grammar -- a string, a comment, an interpolation. */
  case Push(state: String)

  /** Leave the current state. The bottom frame and an embedding boundary are never popped this way.
    */
  case Pop

  /** Continue in another language until `end` matches: JavaScript inside `<script>`.
    *
    * The grammar is a thunk because grammars refer to each other -- HTML embeds CSS and JavaScript
    * -- and a plain value would make their initialisation order a puzzle. `end` is compiled once,
    * here, so that two frames from the same rule compare equal: the incremental relexing stops as
    * soon as a line starts in the same state as before, and "the same state" has to be cheap to
    * ask.
    */
  case Embed(grammar: () => Grammar, end: Pattern)

/** One entry of a state: a rule, or the rules of another state. */
sealed trait RuleEntry

/** "Everything that state recognises, here." Resolved when the grammar is built. */
final case class Include(state: String) extends RuleEntry

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

/** One state of a grammar. `lineBound` states end with the line: an unterminated `"` in Scala. */
final class LexerState private[codehighlighting] (
    val name: String,
    val rules: Vector[Rule],
    val lineBound: Boolean
)

/** A state as written, before includes are resolved. */
final case class StateDefinition(name: String, entries: Vector[RuleEntry], lineBound: Boolean)

def state(name: String, lineBound: Boolean = false)(entries: RuleEntry*): StateDefinition =
  StateDefinition(name, entries.toVector, lineBound)

/** A language, as a set of states with ordered rules.
  *
  * ==Why a grammar and not a parser==
  *
  * The Markdown and HTML modules have real parsers, and neither is used here. A highlighter
  * recolours on every keystroke, so it has to work line by line and resume in the middle of a
  * block; a parser that decides after the whole input -- CommonMark's delimiter stack is exactly
  * that -- cannot. A stack of named states can: the state at the start of a line is everything the
  * line needs to know about the lines before it.
  *
  * Equality is identity. A grammar is built once and shared, and the state stack compares frames by
  * it.
  */
final class Grammar private (
    val name: String,
    val initial: String,
    states: Map[String, LexerState]
):

  def state(name: String): LexerState =
    states.getOrElse(name, throw new IllegalStateException(s"$this has no state `$name`"))

  /** The state a block starts in. */
  def start: LexState = LexState(List(Frame(this, initial, None)))

  override def toString: String = s"Grammar($name)"

object Grammar:

  def apply(name: String, initial: String = "root")(definitions: StateDefinition*): Grammar =
    val byName = definitions.map(definition => definition.name -> definition).toMap
    require(byName.size == definitions.size, s"$name: duplicate state names")
    require(byName.contains(initial), s"$name: no initial state `$initial`")

    def resolve(definition: StateDefinition, seen: Set[String]): Vector[Rule] =
      definition.entries.flatMap {
        case rule: Rule =>
          val groups = rule.pattern.matcher("").groupCount()
          require(
            rule.groupKinds.isEmpty || rule.groupKinds.length == groups,
            s"$name/${definition.name}: `${rule.pattern.pattern}` has $groups groups " +
              s"but ${rule.groupKinds.length} kinds"
          )
          rule.ops.foreach {
            case StackOp.Push(target) =>
              require(byName.contains(target), s"$name/${definition.name}: no state `$target`")
            case _ => ()
          }
          Vector(rule)
        case Include(target) =>
          require(!seen.contains(target), s"$name: include cycle through `$target`")
          val included = byName.getOrElse(
            target,
            throw new IllegalArgumentException(s"$name/${definition.name}: no state `$target`")
          )
          resolve(included, seen + target)
      }

    val states = mutable.LinkedHashMap.empty[String, LexerState]
    definitions.foreach { definition =>
      states(definition.name) = new LexerState(
        definition.name,
        resolve(definition, Set(definition.name)),
        definition.lineBound
      )
    }
    new Grammar(name, initial, states.toMap)

/** One level of the state stack.
  *
  * `end` is set on the frame that entered an embedded language, and only there: it is the boundary
  * everything above it has to stop at.
  */
final case class Frame(grammar: Grammar, state: String, end: Option[Pattern]):

  def definition: LexerState = grammar.state(state)

/** Where the lexer is at the start of a line. The top of the stack is the head of the list. */
final case class LexState(frames: List[Frame]):

  def top: Frame = frames.head
