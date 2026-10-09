package ember.editor.codehighlighting

import java.util.regex.Pattern

import scala.collection.mutable

/** One level of the state stack.
  *
  * `end` is set on the frame that entered an embedded language, and only there: it is the boundary
  * everything above it has to stop at.
  */
final case class Frame(
    grammar: Grammar,
    state: String,
    end: Option[Pattern],
    endAtLineStart: Boolean = false
):

  def definition: LexerState = grammar.state(state)
