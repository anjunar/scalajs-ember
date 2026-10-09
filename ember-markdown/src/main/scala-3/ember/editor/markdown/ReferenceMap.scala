package ember.editor.markdown

import scala.collection.mutable

/** The map of link reference definitions, keyed by normalised label.
  *
  * §18.2 lets reference labels be canonicalised, and CommonMark says how: strip the brackets, trim,
  * collapse internal whitespace, then case-fold. The reference does the fold as
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
    val inner     = if rawLabel.length >= 2 then rawLabel.substring(1, rawLabel.length - 1) else ""
    val collapsed = new StringBuilder
    var pendingSpace = false
    var seen         = false
    inner.foreach { c =>
      if c == ' ' || c == '\t' || c == '\r' || c == '\n' then pendingSpace = seen
      else
        if pendingSpace then collapsed.append(' ')
        pendingSpace = false
        seen = true
        collapsed.append(c)
    }
    collapsed.toString.toLowerCase.toUpperCase
