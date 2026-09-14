package ember.editor.richtext

import ember.editor.core.*

/** Ein Absatz: der Standardblock des Rich-Text-Profils.
  *
  * Bewusst hier und nicht im Kern (§6, §8.1). Der Kern kennt nur Wurzel und Textlauf; welche
  * Blockarten es gibt, entscheidet das Profil. Eine reine Paragraph-Anwendung linkt deshalb weder
  * Heading noch Liste noch Bild mit.
  *
  * Enthaelt Inline-Inhalte -- im Umfang von P06 sind das Textlaeufe. Heading, Quote und Breaks
  * folgen in P12.
  */
final case class ParagraphNode(id: NodeId, children: Vector[NodeId]) extends ElementNode

object ParagraphNode extends ElementNodeType[ParagraphNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.rich-text.paragraph/1")

  def project(node: EditorNode): Option[ParagraphNode] = node match
    case paragraph: ParagraphNode => Some(paragraph)
    case _                        => None

  def rekey(node: ParagraphNode, id: NodeId): ParagraphNode = node.copy(id = id)

  def withChildren(node: ParagraphNode, children: Vector[NodeId]): ParagraphNode =
    node.copy(children = children)

  def empty(id: NodeId): ParagraphNode = ParagraphNode(id, Vector.empty)

  override def validate(node: ParagraphNode, document: DocumentRead): Vector[Violation] =
    InlineContent.validate(node, document)

/** Shared by inline-content blocks and link containers, without naming feature modules. */
object InlineContent:
  def validate(node: ElementNode, document: DocumentRead): Vector[Violation] =
    node.children.flatMap(document.node).collect {
      case child: ElementNode if !child.isInstanceOf[InlineElementNode] =>
        Violation.NodeRejected(
          node.id,
          "Inline content cannot contain a block",
          document.pathTo(child.id)
        )
      case child: ThematicBreakNode =>
        Violation.NodeRejected(
          node.id,
          "A thematic break belongs between blocks",
          document.pathTo(child.id)
        )
    }
