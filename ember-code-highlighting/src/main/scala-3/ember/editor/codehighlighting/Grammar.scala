package ember.editor.codehighlighting

import java.util.regex.Pattern

import scala.collection.mutable

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
