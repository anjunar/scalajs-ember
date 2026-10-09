package ember.editor.browser

import org.scalajs.dom

import scala.scalajs.js

/** What a batch of DOM mutations amounted to.
  *
  * Counts and kinds, not the records themselves. What the editor does with them is to ask whether
  * the DOM still says what the document says -- and for that question the records are a trigger,
  * not evidence.
  */
final case class MutationSummary(
    characterData: Int,
    childList: Int,
    attributes: Int
):
  def total: Int       = characterData + childList + attributes
  def isEmpty: Boolean = total == 0

  def ++(other: MutationSummary): MutationSummary =
    MutationSummary(
      characterData + other.characterData,
      childList + other.childList,
      attributes + other.attributes
    )

object MutationSummary:
  val empty: MutationSummary = MutationSummary(0, 0, 0)
