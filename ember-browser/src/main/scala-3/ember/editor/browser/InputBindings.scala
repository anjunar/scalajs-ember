package ember.editor.browser

import ember.editor.core.*

/** The bindings an editor has, in order.
  *
  * Ordered rather than keyed: two modules may both answer `InsertParagraph` -- a list wants to
  * split an item, rich text wants to split a block -- and which of them goes first is a decision
  * the application makes by the order it assembles them. That mirrors [[CommandRegistry]]'s
  * priorities without inventing a second priority scheme for the same thing.
  */
final class InputBindings private (val bindings: Vector[InputBinding]):

  /** Every binding that answers this intent, in order.
    *
    * All of them, not the first: a binding whose command reports `Pass` has '''not''' taken the
    * intent, and the next one must get its turn. §12 says that about commands -- "Nicht zustaendig.
    * Der naechste Handler kommt dran" -- and a binding layer that stopped at the first match would
    * break the rule one level up.
    *
    * The case that showed it: Tab indents a code line or a list item, and those are two different
    * commands. Stopping at the first would mean Tab in a list leaves the editor, because the code
    * command passed and nobody looked further.
    */
  def resolveAll(
      intent: InputIntent
  ): Vector[EditorSession => Either[UpdateError, DispatchOutcome]] =
    bindings.collect { case binding if binding.isDefinedAt(intent) => binding(intent) }

  def resolve(intent: InputIntent): Option[EditorSession => Either[UpdateError, DispatchOutcome]] =
    resolveAll(intent).headOption

  def ++(other: InputBindings): InputBindings = new InputBindings(bindings ++ other.bindings)

object InputBindings:

  val empty: InputBindings = new InputBindings(Vector.empty)

  def of(bindings: InputBinding*): InputBindings = new InputBindings(bindings.toVector)
