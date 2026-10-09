package ember.editor.codehighlighting

import java.util.regex.Pattern

import scala.collection.mutable

/** Compiled patterns shared by source string.
  *
  * `Pattern` compares by identity, and a [[Frame]] with a dynamically chosen end has to equal the
  * frame the same fence produced last time, or the incremental relexing would never stop early.
  */
private[codehighlighting] object Patterns:
  private val compiled = mutable.HashMap.empty[String, Pattern]

  def of(regex: String): Pattern = compiled.getOrElseUpdate(regex, Pattern.compile(regex))
