package ember.editor.list

import ember.editor.core.*

/** Numbered or bulleted. */
enum ListKind:
  case Ordered
  case Unordered

/** A list.
  *
  * ==Its children are items, never blocks==
  *
  * §8.2 states the schema rule: "eine Liste ListItems, ein ListItem Blockinhalte." A paragraph
  * sitting directly in a list is not a shortcut, it is a document neither HTML nor Markdown can
  * express -- `<ul><p>` is invalid, and Markdown has no way to write it at all.
  * [[ListNormalization]] wraps such a child rather than rejecting the document, because the way
  * it usually appears is a move that landed one level too high.
  *
  * @param start
  *   the number the first item carries. §18.2 asks for it: "Startnummer, Verschachtelung, enge
  *   und weite Listen und mehrteilige ListItems erhalten." Markdown can write a list that
  *   begins at 3, and a round trip that silently renumbered it would be lossy. Meaningless for
  *   [[ListKind.Unordered]], where it stays at its default.
  * @param tight
  *   whether the items are rendered without paragraph spacing. A CommonMark distinction that
  *   survives into HTML: a tight item renders its single paragraph as bare inline content, a
  *   loose one keeps the `<p>`. Carried here rather than derived, because deriving it from the
  *   current children would change the document every time someone adds a second paragraph.
  */
final case class ListNode(
    id: NodeId,
    children: Vector[NodeId],
    kind: ListKind,
    start: Int = 1,
    tight: Boolean = true
) extends ElementNode

object ListNode extends ElementNodeType[ListNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.list.list/1")

  def project(node: EditorNode): Option[ListNode] = node match
    case value: ListNode => Some(value)
    case _               => None

  def rekey(node: ListNode, id: NodeId): ListNode = node.copy(id = id)

  def withChildren(node: ListNode, children: Vector[NodeId]): ListNode =
    node.copy(children = children)

  def empty(id: NodeId, kind: ListKind): ListNode = ListNode(id, Vector.empty, kind)

  /** A start number below one is not a list that anything can render. */
  override def validate(node: ListNode, document: DocumentRead): Vector[Violation] =
    if node.start >= 1 then Vector.empty
    else
      Vector(
        Violation.NodeRejected(
          node.id,
          s"Eine Liste beginnt bei 1 oder spaeter, nicht bei ${node.start}.",
          DiagnosticPath.node(node.id.value).field("start")
        )
      )

/** One item of a list.
  *
  * ==Why it holds blocks and not inline content==
  *
  * §8.2 again: "ein ListItem Blockinhalte." That is not pedantry -- it is what makes a list item
  * able to hold two paragraphs, a nested list, or a quote, all of which real documents do. An
  * item that held inline content directly would need a second, parallel structure for every one
  * of those cases.
  *
  * The price is one more level of nesting than a naive model, and it is paid in exactly one
  * place: [[ListNormalization]] gives an empty item a paragraph, so that there is always a
  * caret position inside it. Same reason as `BlockNeedsText` in the rich-text profile.
  */
final case class ListItemNode(id: NodeId, children: Vector[NodeId]) extends ElementNode

object ListItemNode extends ElementNodeType[ListItemNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.list.item/1")

  def project(node: EditorNode): Option[ListItemNode] = node match
    case value: ListItemNode => Some(value)
    case _                   => None

  def rekey(node: ListItemNode, id: NodeId): ListItemNode = node.copy(id = id)

  def withChildren(node: ListItemNode, children: Vector[NodeId]): ListItemNode =
    node.copy(children = children)

  def empty(id: NodeId): ListItemNode = ListItemNode(id, Vector.empty)
