package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** Where in a document a piece of source ended up, and the other way round.
  *
  * This is the second half of §18.2's source maps: "SourceMaps erfassen UTF-16-Quellbereiche '''und
  * Dokumentpositionen'''." [[SourceMap]] answers the first half about syntax; this one answers the
  * second about nodes, and it is what a source view needs to highlight the node under the caret.
  */
final case class DocumentSourceMap(entries: Vector[(NodeId, SourceSpan)]):

  private lazy val byId: Map[NodeId, SourceSpan] = entries.toMap

  def spanOf(node: NodeId): Option[SourceSpan] = byId.get(node)

  def size: Int = byId.size

  /** The innermost node containing an offset.
    *
    * "Innermost" without a tree to walk: a child's span lies inside its parent's, so the one that
    * starts latest and is shortest is the deepest.
    *
    * Two spans can be identical -- a paragraph holding a single text run covers exactly the same
    * characters -- and then neither rule decides. The tie goes to whichever was recorded first, and
    * that is not arbitrary: the codec decodes children before their parent, so the earlier entry is
    * the deeper node. Hence a [[Vector]] here and not a [[Map]]; a map would answer the same
    * question differently depending on hashing.
    */
  def nodeAt(offset: Int): Option[NodeId] =
    entries
      .filter((_, span) => span.contains(offset))
      .reduceOption { (left, right) =>
        val (_, leftSpan)  = left
        val (_, rightSpan) = right
        if rightSpan.start > leftSpan.start then right
        else if rightSpan.start == leftSpan.start && rightSpan.length < leftSpan.length then right
        else left
      }
      .map(_._1)
