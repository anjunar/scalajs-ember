package ember.editor.markdown

/** Where each block came from, and how to get back there.
  *
  * ==What P17 builds and what P18 adds==
  *
  * §18.2 asks for two directions: "SourceMaps erfassen UTF-16-Quellbereiche '''und'''
  * Dokumentpositionen." Only the first half exists here, and for a plain reason -- P17 produces no
  * document, so there are no document positions to record. P18 adds them when it adds the adapter
  * that turns syntax into nodes.
  *
  * The half that exists is the half a source view already needs: click at an offset, find the
  * block; select a block, highlight its source.
  *
  * ==Why the spans live in the blocks too==
  *
  * Because a block without a span is meaningless and the type should not allow one. The map is the
  * '''reverse''' index -- offset to block -- and that is something a tree cannot answer without a
  * walk. Keeping both is not duplication; they answer different questions, and the map is built
  * from the blocks in one pass, so they cannot disagree.
  *
  * @param spans
  *   span by [[SyntaxId]]. Keyed by the underlying `Int` because a map key wants an ordinary type
  *   and the opaque type buys nothing here.
  * @param lineStarts
  *   absolute offset of every line start, ascending. Kept so that a diagnostic can name a line
  *   without the caller re-scanning the source.
  */
final case class SourceMap(
    private val spans: Map[Int, SourceSpan],
    lineStarts: Vector[Int]
):

  def spanOf(id: SyntaxId): Option[SourceSpan] = spans.get(id.value)

  /** How many blocks the parse produced, the document itself included. */
  def size: Int = spans.size

  /** The innermost block containing an offset.
    *
    * Walks the tree rather than the map, because "innermost" is a question about nesting and the
    * map has no nesting. Linear in the depth at that offset, not in the document.
    *
    * A block boundary belongs to what '''follows''': [[SourceSpan.contains]] is half-open, so a
    * caret between two paragraphs lands in the second. That is the documented affinity §18.2 asks
    * for at syntactic delimiters, and it matches how a caret behaves everywhere else in this editor
    * (§11, `Point.Before`/`After`).
    */
  def blockAt(root: MarkdownBlock, offset: Int): Option[MarkdownBlock] =
    if !root.span.contains(offset) then None
    else
      root.children.iterator
        .map(child => blockAt(child, offset))
        .collectFirst { case Some(found) => found }
        .orElse(Some(root))

  /** The one-based line an offset falls on.
    *
    * Binary search over [[lineStarts]] -- a source view asks this for every diagnostic, and a
    * linear scan would make a long document quadratic in the number of diagnostics.
    */
  def lineAt(offset: Int): Int =
    if lineStarts.isEmpty then 1
    else
      var low  = 0
      var high = lineStarts.length - 1
      var line = 0
      while low <= high do
        val middle = (low + high) >>> 1
        if lineStarts(middle) <= offset then
          line = middle
          low = middle + 1
        else high = middle - 1
      line + 1

  /** The offset of the start of a one-based line, if it exists. */
  def startOfLine(number: Int): Option[Int] = lineStarts.lift(number - 1)

object SourceMap:

  /** Builds a map from a tree. The parser has one already; this is for a caller that only has the
    * tree -- after a transformation, say, or a test.
    */
  def of(root: MarkdownBlock, lineStarts: Vector[Int]): SourceMap =
    val collected = Map.newBuilder[Int, SourceSpan]

    def walk(block: MarkdownBlock): Unit =
      collected += (block.id.value -> block.span)
      block.children.foreach(walk)

    walk(root)
    SourceMap(collected.result(), lineStarts)
