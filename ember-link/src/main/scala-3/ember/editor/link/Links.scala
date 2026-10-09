package ember.editor.link

import ember.editor.core.*
import ember.editor.richtext.*

/** Reading a document in link terms, and the rules that keep links legal. */
object Links:

  /** The link a point sits in, if any.
    *
    * Walks upwards, not downwards: a point names a run, and the link -- if there is one -- is one
    * of its ancestors. Stopping at the first block keeps the search finite and honest, since a link
    * is inline and cannot be above one (§8.2).
    */
  def linkAt(document: DocumentRead, point: Point): Option[LinkNode] =
    ancestorsOf(document, point.owner)
      .flatMap(document.node)
      .collectFirst { case link: LinkNode => link }

  private def ancestorsOf(document: DocumentRead, node: NodeId): Vector[NodeId] =
    Iterator
      .iterate(document.parentOf(node))(_.flatMap(document.parentOf))
      .takeWhile(_.isDefined)
      .flatten
      .toVector

  /** Whether a point stands inside a link. */
  def isLinked(document: DocumentRead, point: Point): Boolean = linkAt(document, point).isDefined

  /** Moves a link's children out and removes the link.
    *
    * The children keep their ids -- `move` preserves the node (§11), so marks, text and any point
    * inside them survive. What happens afterwards is P12's business: adjacent runs with equal marks
    * grow back together, so unlinking in the middle of a sentence leaves one run.
    */
  def unwrap(scope: TransformScope, link: LinkNode): Unit =
    (for
      parent <- scope.document.parentOf(link.id)
      at     <- scope.document.indexOfChild(link.id)
    yield (parent, at)).foreach { (parent, at) =>
      link.children.zipWithIndex.foreach { (child, offset) =>
        scope.move(child, parent, at + offset): Unit
      }
      scope.remove(link.id): Unit
    }
