package ember.editor.codehighlighting

import java.util.regex.Pattern

import scala.collection.mutable

/** "Everything that state recognises, here." Resolved when the grammar is built. */
final case class Include(state: String) extends RuleEntry
