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
    * The obvious implementation asks the platform -- `Cmd` on a Mac, `Ctrl` elsewhere -- and it was
    * the first one here. A browser test took it apart: Playwright's WebKit build on Windows reports
    * a Macintosh user agent, so `Ctrl+Z` matched nothing and the editor had no undo.
    * `navigator.platform` is deprecated and `userAgentData` is not everywhere, and both would have
    * been wrong in the same way.
    *
    * Accepting both costs one thing: on macOS `Ctrl+B` now toggles bold, where the system would
    * otherwise move the caret back one character. That is a rarely used emacs binding inside a text
    * field. The alternative was a shortcut table that silently does nothing on an entire engine.
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
    * `Ctrl+Shift+Z` arrives with `key == "Z"`, and a table written in lower case would miss it. The
    * shift state is carried separately, where a binding can actually read it.
    */
  def normalise(key: String): String = if key.length == 1 then key.toLowerCase else key
