package ember.editor.foreign

import ember.editor.core.*

/** Ein fehlerhaft registrierter Container.
  *
  * Traegt Kinder, ist aber nur als [[NodeType]] beschrieben, nicht als [[ElementNodeType]] -- ohne
  * `withChildren` kann der Kern ihn nicht generisch umbauen. Existiert ausschliesslich, um
  * [[Violation.MissingElementDescriptor]] ausloesen zu koennen.
  */
final case class UndescribedContainer(id: NodeId, children: Vector[NodeId]) extends ElementNode

object UndescribedContainer extends NodeType[UndescribedContainer]:

  val typeId: NodeTypeId = NodeTypeId("foreign.undescribed/1")

  def project(node: EditorNode): Option[UndescribedContainer] = node match
    case container: UndescribedContainer => Some(container)
    case _                               => None

  def rekey(node: UndescribedContainer, id: NodeId): UndescribedContainer = node.copy(id = id)
