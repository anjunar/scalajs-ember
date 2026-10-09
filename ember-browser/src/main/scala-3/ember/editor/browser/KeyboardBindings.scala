package ember.editor.browser

import ember.editor.core.*
import org.scalajs.dom

import scala.scalajs.js

/** The shortcut table of an editor, in order. */
final class KeyboardBindings private (val bindings: Vector[KeyBinding]):

  /** Every binding for this combination, in order. See [[InputBindings.resolveAll]]. */
  def resolveAll(
      shortcut: Shortcut
  ): Vector[EditorSession => Either[UpdateError, DispatchOutcome]] =
    bindings.collect { case binding if binding.isDefinedAt(shortcut) => binding(shortcut) }

  def resolve(shortcut: Shortcut): Option[EditorSession => Either[UpdateError, DispatchOutcome]] =
    resolveAll(shortcut).headOption

  def ++(other: KeyboardBindings): KeyboardBindings = new KeyboardBindings(
    bindings ++ other.bindings
  )

object KeyboardBindings:

  val empty: KeyboardBindings = new KeyboardBindings(Vector.empty)

  def of(bindings: KeyBinding*): KeyboardBindings = new KeyboardBindings(bindings.toVector)
