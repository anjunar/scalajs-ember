package ember.editor.json

import ember.editor.core.*

/** Ein Element mit Kindern, dessen Deskriptor '''kein''' [[ElementNodeType]] ist.
  *
  * Der Fall, den §8.1 als Schemafehler fuehrt. Hier gebraucht, um zu zeigen, dass das Dekodieren
  * ihn meldet, statt die Kindliste stillschweigend fallen zu lassen.
  */
final case class BrokenNode(id: NodeId, children: Vector[NodeId]) extends ElementNode

object BrokenNode extends NodeType[BrokenNode]:

  val typeId: NodeTypeId = NodeTypeId("test.broken/1")

  def project(node: EditorNode): Option[BrokenNode] = node match
    case broken: BrokenNode => Some(broken)
    case _                  => None

  def rekey(node: BrokenNode, id: NodeId): BrokenNode = node.copy(id = id)
