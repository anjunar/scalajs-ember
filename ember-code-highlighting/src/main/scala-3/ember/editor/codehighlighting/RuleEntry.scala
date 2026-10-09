package ember.editor.codehighlighting

import java.util.regex.Pattern

import scala.collection.mutable

/** One entry of a state: a rule, or the rules of another state. */
trait RuleEntry
