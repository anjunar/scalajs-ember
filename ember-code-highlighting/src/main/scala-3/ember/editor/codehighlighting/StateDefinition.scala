package ember.editor.codehighlighting

import java.util.regex.Pattern

import scala.collection.mutable

/** A state as written, before includes are resolved. */
final case class StateDefinition(name: String, entries: Vector[RuleEntry], lineBound: Boolean)

def state(name: String, lineBound: Boolean = false)(entries: RuleEntry*): StateDefinition =
  StateDefinition(name, entries.toVector, lineBound)
