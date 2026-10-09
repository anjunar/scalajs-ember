package ember.editor.foreign

import ember.editor.core.*

/** Eine Art, die absichtlich in keinem Testschema registriert wird. */
final case class UnregisteredNode(id: NodeId) extends EditorNode
