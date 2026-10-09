package ember.editor.history

import ember.editor.core.*

/** Die Knotenarten der Tests als Extension -- der uebliche Weg, eine Sitzung aufzubauen (§13). */
object TestNodes extends Extension:
  val id: ExtensionId = ExtensionId("test.nodes")

  override def contribute: ExtensionContributions =
    ExtensionContributions(nodeTypes = Vector(RootNode, TextNode, BlockNode))
