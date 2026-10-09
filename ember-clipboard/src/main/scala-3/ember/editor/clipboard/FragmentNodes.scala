package ember.editor.clipboard

import ember.editor.core.*
import ember.editor.richtext.*

/** Descriptor-based reconstruction also works for application-defined nodes. */
private[clipboard] object FragmentNodes:
  def rekey(document: DocumentRead, node: EditorNode, id: NodeId): EditorNode =
    def run[N <: EditorNode](kind: NodeType[N]): EditorNode = kind.rekey(kind.project(node).get, id)
    run(document.schema.descriptorFor(node).get)

  def children(document: DocumentRead, node: ElementNode, ids: Vector[NodeId]): ElementNode =
    def run[N <: ElementNode](kind: ElementNodeType[N]): ElementNode =
      kind.withChildren(kind.project(node).get, ids)
    document.schema.descriptorFor(node).get match
      case kind: ElementNodeType[?] => run(kind)
      case _ => throw EditorContractViolation("Missing element descriptor in validated fragment")

  def equivalent(document: DocumentRead, left: ElementNode, right: ElementNode): Boolean =
    children(document, left, Vector.empty) == rekey(
      document,
      children(document, right, Vector.empty),
      left.id
    )
