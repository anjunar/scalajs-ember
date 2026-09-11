package ember.editor.markdown

import scala.collection.mutable

/** The two stacks the inline parser runs on.
  *
  * ==Why a doubly linked list and not a `List`==
  *
  * Because `processEmphasis` walks '''forward''' from a bottom marker looking for closers,
  * walks '''backward''' from each closer looking for an opener, and removes entries from the
  * middle -- and it does all three interleaved. That is a doubly linked list, and pretending
  * otherwise would mean rebuilding an immutable structure inside the innermost loop of the
  * parser.
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

/** A `[` or `![` waiting for its `]`. */
private[markdown] final class Bracket(
    val node: PendingText,
    val previous: Bracket | Null,
    val previousDelimiter: Delimiter | Null,
    val index: Int,
    val isImage: Boolean,
    var active: Boolean,
    var bracketAfter: Boolean
)

/** A text node while it is still being built.
  *
  * Emphasis processing shortens the literal of an opener or closer as it consumes delimiters,
  * so the text is mutable until the parse ends. The final [[MarkdownInline.Text]] is built
  * afterwards, from whatever is left.
  */
private[markdown] final class PendingText(var literal: String, var start: Int, var end: Int)

/** One node of the inline result while it is being assembled.
  *
  * The reference mutates a linked tree in place: emphasis processing moves a run of siblings
  * '''into''' a new node, and bracket processing does the same. Building the immutable tree
  * directly would mean rebuilding a vector for every delimiter pair.
  */
private[markdown] final class InlineSlot(
    var kind: SlotKind,
    var start: Int,
    var end: Int
):
  val children: mutable.ArrayBuffer[InlineSlot] = mutable.ArrayBuffer.empty
  var text: PendingText | Null                  = null
  var removed                                   = false

private[markdown] enum SlotKind:
  case Text
  case Code(literal: String)
  case SoftBreak
  case HardBreak
  case Html(literal: String)
  case Emphasis
  case Strong
  case Link(destination: String, title: Option[String])
  case Image(destination: String, title: Option[String])

/** A resolved link reference definition. */
private[markdown] final case class LinkReference(destination: String, title: Option[String])

/** The map of link reference definitions, keyed by normalised label.
  *
  * §18.2 lets reference labels be canonicalised, and CommonMark says how: strip the brackets,
  * trim, collapse internal whitespace, then case-fold. The reference does the fold as
  * `toLowerCase().toUpperCase()`, which is not a mistake -- it is how you reach a single
  * case-folded form for characters whose upper and lower cases are not symmetric.
  */
private[markdown] final class ReferenceMap:
  private val entries = mutable.Map.empty[String, LinkReference]

  def define(rawLabel: String, reference: LinkReference): Unit =
    val key = ReferenceMap.normalise(rawLabel)
    // Die erste Definition gewinnt. CommonMark sagt das ausdruecklich, und es ist die
    // Reihenfolge, in der ein Mensch ein Dokument liest.
    if key.nonEmpty && !entries.contains(key) then entries += (key -> reference)

  def lookup(rawLabel: String): Option[LinkReference] =
    entries.get(ReferenceMap.normalise(rawLabel))

  def size: Int = entries.size

private[markdown] object ReferenceMap:

  /** `[ Foo  Bar ]` and `[foo bar]` are the same label. */
  def normalise(rawLabel: String): String =
    val inner = if rawLabel.length >= 2 then rawLabel.substring(1, rawLabel.length - 1) else ""
    val collapsed = new StringBuilder
    var pendingSpace = false
    var seen = false
    inner.foreach { c =>
      if c == ' ' || c == '\t' || c == '\r' || c == '\n' then pendingSpace = seen
      else
        if pendingSpace then collapsed.append(' ')
        pendingSpace = false
        seen = true
        collapsed.append(c)
    }
    collapsed.toString.toLowerCase.toUpperCase
