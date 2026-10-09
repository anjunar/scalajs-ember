package ember.editor.core

/** Die Dokumentwurzel. Genau eine je Dokument, nie Kind eines anderen Knotens (§8.2).
  *
  * Der Kern erlaubt eine leere Wurzel. Dass eine editierbare Flaeche mindestens einen Paragraph und
  * eine gueltige Caretposition braucht, ist eine Regel des Rich-Text-Profils, keine des Kerns.
  */
final case class RootNode(id: NodeId, children: Vector[NodeId]) extends ElementNode

object RootNode extends ElementNodeType[RootNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.core.root/1")

  def project(node: EditorNode): Option[RootNode] = node match
    case root: RootNode => Some(root)
    case _              => None

  def rekey(node: RootNode, id: NodeId): RootNode = node.copy(id = id)

  def withChildren(node: RootNode, children: Vector[NodeId]): RootNode =
    node.copy(children = children)

  def empty(id: NodeId): RootNode = RootNode(id, Vector.empty)
