package ember.editor.richtext

import ember.editor.core.*

/** A block quote. A block container holding *blocks*, not inline content.
  *
  * ==The difference from a paragraph, and why it matters here==
  *
  * §8.2 states the schema rule: "ein Paragraph enthaelt Inline-Inhalte, eine Liste ListItems, ein
  * ListItem Blockinhalte." A quote belongs to the third kind -- it wraps whole blocks, so a quote
  * can contain several paragraphs, and later a list or a nested quote.
  *
  * That single fact decides how quoting works: it is not a property one sets on a paragraph, it is
  * a container one puts paragraphs into. `Unquote` takes them back out again, which is why both
  * commands move children rather than replacing a node.
  */
final case class QuoteNode(id: NodeId, children: Vector[NodeId]) extends ElementNode

object QuoteNode extends ElementNodeType[QuoteNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.rich-text.quote/1")

  def project(node: EditorNode): Option[QuoteNode] = node match
    case quote: QuoteNode => Some(quote)
    case _                => None

  def rekey(node: QuoteNode, id: NodeId): QuoteNode = node.copy(id = id)

  def withChildren(node: QuoteNode, children: Vector[NodeId]): QuoteNode =
    node.copy(children = children)

  def empty(id: NodeId): QuoteNode = QuoteNode(id, Vector.empty)

  /** A quote that is empty is a quote of nothing. It is removed during normalisation. */
  override def validate(node: QuoteNode, document: DocumentRead): Vector[Violation] =
    Vector.empty
