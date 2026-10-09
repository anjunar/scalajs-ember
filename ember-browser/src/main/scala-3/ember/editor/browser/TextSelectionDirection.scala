package ember.editor.browser

import ember.editor.core.*
import org.scalajs.dom

/** Reads the DOM's `selectionDirection` as the core's [[SelectionDirection]].
  *
  * The core already has the concept (§11), and a second enum for the same thing would need a
  * conversion at every use -- which is where the two would eventually disagree. What differs is
  * only the vocabulary: the DOM says `"none"` where the core says `Collapsed`, and for a textarea
  * selection the two mean the same thing.
  *
  * §17 step 7 asks for the direction to survive into the rich view, and P20 names it in its test
  * list. Dropping it would turn every restored selection forward, and the next arrow key would move
  * the wrong end.
  */
object TextSelectionDirection:

  def parse(raw: String): SelectionDirection = raw match
    case "forward"  => SelectionDirection.Forward
    case "backward" => SelectionDirection.Backward
    case _          => SelectionDirection.Collapsed
