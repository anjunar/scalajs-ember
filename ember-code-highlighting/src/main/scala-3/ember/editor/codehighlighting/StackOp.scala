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

  /** Continue in a language the match itself names, until a line that starts with `end`.
    *
    * A Markdown fence says its language in its info string, and its closing fence depends on the
    * opener: three backticks close three, four close only four or more. `select` reads both from
    * the matched text; `None` means the language is not known and nothing is embedded. The end is
    * only tested at the start of a line -- a fence cannot close in the middle of one.
    */
  case EmbedMatched(select: String => Option[(Grammar, Pattern)])
