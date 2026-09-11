package ember.editor.browser

import ember.editor.core.*
import org.scalajs.dom

import scala.scalajs.js

/** A key combination, normalised.
  *
  * `primary` is the platform's command modifier -- `Cmd` on Apple keyboards, `Ctrl` everywhere
  * else. One flag rather than two, because a binding means "the shortcut for bold", not "control
  * and b"; writing both at every call site is how a Mac build ends up with `Ctrl+B` and nothing
  * else.
  */
final case class Shortcut(
    key: String,
    primary: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false
)

object Shortcut:

  /** Either physical key sets [[Shortcut.primary]].
    *
    * The obvious implementation asks the platform -- `Cmd` on a Mac, `Ctrl` elsewhere -- and it
    * was the first one here. A browser test took it apart: Playwright's WebKit build on Windows
    * reports a Macintosh user agent, so `Ctrl+Z` matched nothing and the editor had no undo.
    * `navigator.platform` is deprecated and `userAgentData` is not everywhere, and both would have
    * been wrong in the same way.
    *
    * Accepting both costs one thing: on macOS `Ctrl+B` now toggles bold, where the system would
    * otherwise move the caret back one character. That is a rarely used emacs binding inside a
    * text field. The alternative was a shortcut table that silently does nothing on an entire
    * engine.
    */
  def of(event: dom.KeyboardEvent): Shortcut =
    Shortcut(
      key = normalise(event.key),
      primary = event.metaKey || event.ctrlKey,
      shift = event.shiftKey,
      alt = event.altKey
    )

  /** Letters are compared in lower case; named keys keep their spelling.
    *
    * `Ctrl+Shift+Z` arrives with `key == "Z"`, and a table written in lower case would miss it.
    * The shift state is carried separately, where a binding can actually read it.
    */
  def normalise(key: String): String = if key.length == 1 then key.toLowerCase else key

/** Maps a key combination to a dispatch. Partial, for the same reason [[InputBinding]] is. */
type KeyBinding = PartialFunction[Shortcut, EditorSession => Either[UpdateError, DispatchOutcome]]

/** The shortcut table of an editor, in order. */
final class KeyboardBindings private (val bindings: Vector[KeyBinding]):

  /** Every binding for this combination, in order. See [[InputBindings.resolveAll]]. */
  def resolveAll(shortcut: Shortcut): Vector[EditorSession => Either[UpdateError, DispatchOutcome]] =
    bindings.collect { case binding if binding.isDefinedAt(shortcut) => binding(shortcut) }

  def resolve(shortcut: Shortcut): Option[EditorSession => Either[UpdateError, DispatchOutcome]] =
    resolveAll(shortcut).headOption

  def ++(other: KeyboardBindings): KeyboardBindings = new KeyboardBindings(bindings ++ other.bindings)

object KeyboardBindings:

  val empty: KeyboardBindings = new KeyboardBindings(Vector.empty)

  def of(bindings: KeyBinding*): KeyboardBindings = new KeyboardBindings(bindings.toVector)

/** What `Tab` does in this editor.
  *
  * §22 is unusually specific, and for a good reason -- this is the one key that can lock a
  * keyboard user inside a control:
  *
  * > Tab verlaesst die normale Editierflaeche. Listeneinrueckung oder Code-Tab ist ein
  * > ausdruecklich aktiviertes Verhalten mit erreichbarer Ausstiegsmoeglichkeit. Keine permanente
  * > Keyboard-Falle.
  */
enum TabPolicy:

  /** The default. `Tab` is never handled; focus moves on as it does everywhere else. */
  case LeavesEditor

  /** `Tab` indents -- but `Escape` first, then `Tab`, always leaves.
    *
    * The escape hatch is the part §22 requires, and it is deliberately the convention users
    * already know from other editors rather than a new one.
    */
  case IndentsUntilEscape

/** Which keydowns the keyboard layer looks at at all.
  *
  * §15.2: "Text generell ueber Input-Pipeline." A key without a modifier that produces a single
  * character is typing, and typing goes through `beforeinput` -- where dictation, autocorrect and
  * a mobile keyboard also arrive, none of which a keydown ever sees.
  */
object KeyboardRule:

  def isShortcutCandidate(shortcut: Shortcut): Boolean =
    shortcut.primary || shortcut.alt || shortcut.key.length > 1

  /** Keys whose native default edits -- or, in some engines, navigates.
    *
    * `Backspace` is on the list for the second reason: WebKit still takes it as "go back" in a
    * focused element that is not editable, which turns a readonly editor into a way of losing the
    * page.
    */
  val editsOrNavigates: Set[String] = Set("Backspace", "Delete", "Enter")

/** Whether `Tab` may be taken over right now.
  *
  * Pure, because it is a rule and not an event handler: a policy, plus whether `Escape` was the
  * key pressed just before.
  */
object TabRule:

  val TabKey: String    = "Tab"
  val EscapeKey: String = "Escape"

  def handlesTab(policy: TabPolicy, escapeArmed: Boolean): Boolean =
    policy match
      case TabPolicy.LeavesEditor      => false
      case TabPolicy.IndentsUntilEscape => !escapeArmed

  /** Whether pressing this key arms the escape hatch. */
  def arms(key: String): Boolean = key == EscapeKey
