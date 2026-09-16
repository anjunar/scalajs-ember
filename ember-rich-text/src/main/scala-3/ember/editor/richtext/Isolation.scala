package ember.editor.richtext

import ember.editor.core.*

/** A container whose boundary text editing never crosses: a table cell.
  *
  * ==Why the rich-text profile needs to know==
  *
  * Backspace at the start of a block joins it with the block before, and a range deletion joins the
  * block where it starts with the block where it ends. Both are right between two paragraphs and
  * wrong between two table cells: joining would move a cell's text into its neighbour and leave an
  * empty cell behind -- or none at all, once the empty parents are pruned.
  *
  * The profile cannot name the table module (§6 puts tables above rich text), so the table module
  * marks its nodes instead, the same way a link marks itself as an [[InlineElementNode]]. Editing
  * then asks for the marker and never for the type.
  */
trait IsolatingElementNode extends ElementNode

/** A container whose children are fixed structure rather than content: a table and its rows.
  *
  * A range deletion that covers part of such a structure '''clears''' the isolated regions inside
  * it instead of removing them. Removing a row because a selection passed through it would change
  * the table's shape, and a selection is a request to delete text, not columns.
  *
  * A structure that lies '''entirely''' inside a range still goes as a whole -- that is a table the
  * author selected, not one a selection merely touched.
  */
trait StructuralElementNode extends ElementNode

/** Reading a document in terms of isolated regions. */
object Isolation:

  /** The nearest isolating ancestor of a node, if it has one. */
  def of(document: DocumentRead, node: NodeId): Option[NodeId] =
    document.ancestorsOf(node).find(id => isIsolating(document, id))

  /** Whether editing may join these two nodes' blocks. */
  def shared(document: DocumentRead, left: NodeId, right: NodeId): Boolean =
    of(document, left) == of(document, right)

  def isIsolating(document: DocumentRead, id: NodeId): Boolean =
    document.node(id).exists(_.isInstanceOf[IsolatingElementNode])

  def isStructural(document: DocumentRead, id: NodeId): Boolean =
    document.node(id).exists(_.isInstanceOf[StructuralElementNode])
