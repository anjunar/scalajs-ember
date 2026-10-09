package ember.editor.markdown

import scala.collection.mutable

/** The two stacks the inline parser runs on.
  *
  * ==Why a doubly linked list and not a `List`==
  *
  * Because `processEmphasis` walks '''forward''' from a bottom marker looking for closers, walks
  * '''backward''' from each closer looking for an opener, and removes entries from the middle --
  * and it does all three interleaved. That is a doubly linked list, and pretending otherwise would
  * mean rebuilding an immutable structure inside the innermost loop of the parser.
  *
  * The mutability is contained the same way the block parser's is: these types are
  * `private[markdown]`, they exist only during one inline parse, and nothing outside sees them.
  *
  * Ported from `lib/inlines.js` of commonmark.js 0.31.2 -- see `ember-markdown/NOTICE`.
  */
private[markdown] final class Delimiter(
    val char: Char,
    var count: Int,
    val originalCount: Int,
    var node: PendingText,
    val canOpen: Boolean,
    val canClose: Boolean,
    var previous: Delimiter | Null,
    var next: Delimiter | Null
)
