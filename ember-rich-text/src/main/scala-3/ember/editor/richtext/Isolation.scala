package ember.editor.richtext

import ember.editor.core.*

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
