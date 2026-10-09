package ember.editor.markdown

/** A range of the original source, in UTF-16 units (§18.2).
  *
  * ==Why UTF-16 and not lines and columns==
  *
  * Because everything else in this editor counts in UTF-16 units -- §11 fixes it for text
  * positions, and `spliceText` in ui-core takes them. A source map that spoke in lines and columns
  * would need a conversion at every use, and the conversion is where an off-by-one hides.
  * CommonMark's own reference implementation reports line/column; this is the one place the port
  * deliberately does something else.
  *
  * ==Why no constructor check==
  *
  * `start <= end` and both non-negative is an invariant of the '''parser''', not of a caller --
  * nobody outside builds one. A guard in the constructor would check the wrong party.
  * `MarkdownBlockSpec` checks the right one: every span of every parse of all [[SpecFixtures]]
  * examples is well formed and contained in its parent's.
  */
final case class SourceSpan(start: Int, end: Int):

  def length: Int = end - start

  def isEmpty: Boolean = end == start

  /** Half-open: the end offset is '''not''' inside. A caret sitting there belongs to whatever
    * follows, which is what a source map is asked at a block boundary.
    */
  def contains(offset: Int): Boolean = offset >= start && offset < end

  def containsSpan(other: SourceSpan): Boolean = other.start >= start && other.end <= end

  def render: String = s"[$start,$end)"
