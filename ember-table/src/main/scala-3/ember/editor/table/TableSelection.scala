package ember.editor.table

import ember.editor.core.*

/** A rectangle of cells.
  *
  * ==Why its own kind==
  *
  * §11 keeps [[Selection]] open for exactly this, and says why: a cell range is neither a text
  * range nor a set of nodes. Dragging from the top-left cell to the bottom-right one selects a
  * rectangle -- a text range between the same two points would run row by row through every cell in
  * between, and a node set could not say which corner is the anchor.
  *
  * Anchor and focus are cells, and the rectangle is whatever they span in the grid '''at the time
  * it is asked'''. An inserted row inside the rectangle is simply part of it afterwards, which is
  * what an author who selected "these two corners" means.
  */
final case class TableSelection(table: NodeId, anchor: NodeId, focus: NodeId) extends Selection:

  /** The selected cells, row by row. Empty when the selection no longer fits the document. */
  def cells(document: DocumentRead): Vector[NodeId] =
    Tables.grid(document, table).map(_.rectangle(anchor, focus).map(_.id)).getOrElse(Vector.empty)
