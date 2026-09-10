package ember.editor.link

import ember.editor.core.*

/** Where a link points, and what it is called.
  *
  * Together rather than as two parameters because they belong to the same decision: a dialog
  * that asks for an address usually asks for a title in the same breath, and `SetLink` takes
  * one payload.
  */
final case class LinkTarget(url: LinkUrl, title: Option[String] = None)

/** An inline link.
  *
  * ==A container, not a mark==
  *
  * §8.2 is explicit: "Links sind Inline-Container, keine Text-Mark." The difference is not
  * taxonomy. A mark has no children and no data beyond its identity; a link has both -- it
  * wraps a stretch of content and carries a target. Modelling it as a mark would mean putting
  * a URL into a `MarkSet`, which §8.2 rules out in the same breath by requiring marks to be
  * "typisierte, normalisierte Werte" rather than payload carriers.
  *
  * It also means formatting and linking compose without either knowing about the other: bold
  * inside a link is a mark on a run inside the link node, and neither rule had to be told.
  *
  * ==No link inside a link==
  *
  * §8.2: "Ein Link enthaelt keine anderen Links." A nested anchor is not expressible in HTML --
  * the parser closes the outer one -- and in Markdown it is not writable at all. [[Links]]
  * unwraps one rather than rejecting the document, because the way it appears is a move or a
  * paste, not an author's intent.
  *
  * ==Why the URL is a [[LinkUrl]]==
  *
  * So that there is no way to build this node with something unchecked. See [[LinkUrl]].
  */
final case class LinkNode(
    id: NodeId,
    children: Vector[NodeId],
    target: LinkTarget
) extends ElementNode:

  def url: LinkUrl = target.url

  def title: Option[String] = target.title

object LinkNode extends ElementNodeType[LinkNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.link.link/1")

  def project(node: EditorNode): Option[LinkNode] = node match
    case value: LinkNode => Some(value)
    case _               => None

  def rekey(node: LinkNode, id: NodeId): LinkNode = node.copy(id = id)

  def withChildren(node: LinkNode, children: Vector[NodeId]): LinkNode =
    node.copy(children = children)

  def empty(id: NodeId, target: LinkTarget): LinkNode = LinkNode(id, Vector.empty, target)

  /** A link whose title is only whitespace says nothing and renders as an empty tooltip. */
  override def validate(node: LinkNode, document: DocumentRead): Vector[Violation] =
    node.title match
      case Some(title) if title.trim.isEmpty =>
        Vector(
          Violation.NodeRejected(
            node.id,
            "Ein Linktitel aus Leerzeichen ist kein Titel. Weglassen statt leer setzen.",
            DiagnosticPath.node(node.id.value).field("title")
          )
        )
      case _ => Vector.empty
