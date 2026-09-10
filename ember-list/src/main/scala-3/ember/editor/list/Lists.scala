package ember.editor.list

import ember.editor.core.*
import ember.editor.richtext.*

/** Where the caret is, in list terms.
  *
  * Every list command starts by asking this, and none of them can do anything useful without
  * the whole answer: the item, the list it is in, and where in that list it sits. Computing it
  * once, in one place, is the difference between five commands that agree and five that drift.
  */
private[list] final case class ListContext(
    block: NodeId,
    item: NodeId,
    list: NodeId,
    kind: ListKind,
    indexInList: Int
)

/** Reading a document in list terms. */
object Lists:

  /** The list context at the caret, if the caret is inside a list at all. */
  private[list] def contextAt(scope: TransformScope): Option[ListContext] =
    val document = scope.document
    for
      block <- blockAtCaret(scope)
      item  <- document.parentOf(block)
      _     <- document.node(item).collect { case value: ListItemNode => value }
      list  <- document.parentOf(item)
      node  <- document.node(list).collect { case value: ListNode => value }
      index <- document.indexOfChild(item)
    yield ListContext(block, item, list, node.kind, index)

  /** The block the caret stands in -- the parent of its run. */
  private[list] def blockAtCaret(scope: TransformScope): Option[NodeId] =
    scope.selection
      .collect { case range: RangeSelection => range.focus }
      .flatMap {
        case Point.Text(node, _, _)       => scope.document.parentOf(node)
        case Point.Children(parent, _, _) => Some(parent)
      }

  /** Whether the caret sits at the very start of its block.
    *
    * What Backspace needs to know: at offset zero of the first run, there is nothing left in
    * this block to delete, and the key means "get me out of here" rather than "remove a
    * character".
    */
  private[list] def atBlockStart(scope: TransformScope): Boolean =
    val document = scope.document

    val position = scope.selection
      .collect { case range: RangeSelection if range.isCollapsed => range.focus }
      .collect { case Point.Text(node, offset, _) => (node, offset) }

    position.exists { (node, offset) =>
      offset == 0 && document.parentOf(node).exists(document.childrenOf(_).headOption.contains(node))
    }

  /** Whether an item has no text at all.
    *
    * Enter in an empty item means something different from Enter in a full one (§18.2 and every
    * editor since the eighties): the first leaves the list, the second starts a new item.
    */
  private[list] def isEmpty(document: DocumentRead, item: NodeId): Boolean =
    textOf(document, item).isEmpty

  private def textOf(document: DocumentRead, node: NodeId): String =
    document.node(node) match
      case Some(run: TextNode)        => run.text
      case Some(element: ElementNode) => element.children.map(textOf(document, _)).mkString
      case _                          => ""

  /** Whether this node may sit inside a list item. Blocks may; runs and items may not. */
  private[list] def isBlock(node: EditorNode): Boolean = node match
    case _: ListItemNode => false
    case _: TextNode     => false
    case _: ElementNode  => true
    case _               => false
