package ember.editor.codehighlighting

import java.util.regex.Pattern

import scala.collection.mutable

/** One state of a grammar. `lineBound` states end with the line: an unterminated `"` in Scala. */
final class LexerState private[codehighlighting] (
    val name: String,
    val rules: Vector[Rule],
    val lineBound: Boolean
)
