package ember.editor.browser

import ember.editor.core.*

/** Which blocks a composition starts in.
  *
  * ==Why blocks and not leaves==
  *
  * Because §15.3 forbids the narrow reading in so many words: "ein blockuebergreifender Start darf
  * nicht als Ein-Leaf-Fall behandelt werden". An IME replacement reaches further than the run it
  * started in -- across marks, across atom boundaries, across blocks -- and a barrier drawn around
  * one leaf would let the projection rewrite the rest of the sentence underneath a composition in
  * progress.
  *
  * A block is also the smallest area that is cheap to name and always sufficient: the guard
  * protects a subtree, so protecting the block protects every leaf, mark and atom in it.
  *
  * ==Why this is a function and not part of the session==
  *
  * It is the one part of the composition protocol that is pure: a document and a selection go in, a
  * set of node ids comes out. Everything around it needs an engine.
  */
object CompositionRegion:

  def of(document: DocumentRead, selection: Option[Selection]): ProtectedRegion =
    selection match
      // No selection means no idea where the composition is. §15.3's escape hatch: "Reicht eine
      // lokale Schutzgrenze nicht, wird der ganze Editing-Host geschuetzt."
      case None => ProtectedRegion.WholeHost

      case Some(range: RangeSelection) =>
        val (from, to) = range.ordered(document)
        (blockOf(document, from.owner), blockOf(document, to.owner)) match
          case (Some(first), Some(last)) => ProtectedRegion.Blocks(span(document, first, last))
          case _                         => ProtectedRegion.WholeHost

      case Some(nodes: NodeSelection) =>
        val blocks = nodes.nodes.toVector.flatMap(blockOf(document, _)).distinct
        if blocks.isEmpty then ProtectedRegion.WholeHost
        else ProtectedRegion.Blocks(inDocumentOrder(document, blocks))

      // A selection kind this module does not know. §11 keeps the contract open, and guessing a
      // narrower area than the truth is the one mistake that costs data.
      case Some(_) => ProtectedRegion.WholeHost

  /** The child of the root that contains a node, or the node itself when it is one.
    *
    * `None` for the root: a composition that claims the root claims everything, and saying so as
    * [[ProtectedRegion.WholeHost]] is more honest than protecting "the root's block".
    */
  def blockOf(document: DocumentRead, node: NodeId): Option[NodeId] =
    if node == document.rootId then None
    else
      val blocks = document.childrenOf(document.rootId)
      if blocks.contains(node) then Some(node)
      else document.ancestorsOf(node).find(blocks.contains)

  /** Every top-level block from the first to the last, inclusive. */
  private def span(document: DocumentRead, first: NodeId, last: NodeId): Vector[NodeId] =
    val blocks = document.childrenOf(document.rootId)
    (blocks.indexOf(first), blocks.indexOf(last)) match
      case (start, end) if start >= 0 && end >= 0 =>
        blocks.slice(math.min(start, end), math.max(start, end) + 1)
      case _ => Vector(first, last).distinct

  private def inDocumentOrder(document: DocumentRead, nodes: Vector[NodeId]): Vector[NodeId] =
    val blocks = document.childrenOf(document.rootId)
    nodes.sortBy(blocks.indexOf)
