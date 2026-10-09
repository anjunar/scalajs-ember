package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** What an element's children are.
  *
  * §19.1 lists "Verarbeitung von Kindern" among the things a rule defines, and this is why it
  * cannot be guessed: `<li>a</li>` holds a paragraph even though no `<p>` is written, and
  * `<p><span><p>x</p></span></p>` holds one paragraph even though two are. Deciding by what is
  * inside would get both wrong, and getting them wrong is how a pasted list ends up with text where
  * a block belongs.
  */
enum ChildMode:

  /** Blocks. Loose inline content is wrapped in a paragraph -- `li`, `blockquote`, `td`. */
  case Blocks

  /** Inline content. A block that turns up is flattened -- `p`, `h1`, `pre`. */
  case Inline
