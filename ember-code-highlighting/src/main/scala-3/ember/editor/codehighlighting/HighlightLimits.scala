package ember.editor.codehighlighting

import ember.editor.core.*

import scala.collection.mutable

/** How much a highlighter is willing to colour.
  *
  * A code block is text an author can paste anything into, and a highlighter runs on every
  * keystroke. Past these limits the block stays uncoloured -- readable, editable, and not in the
  * way.
  */
final case class HighlightLimits(maxChars: Int = 200000, maxLineChars: Int = 4000)

object HighlightLimits:
  val default: HighlightLimits = HighlightLimits()
