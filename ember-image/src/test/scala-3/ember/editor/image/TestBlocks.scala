package ember.editor.image

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
private object TestBlocks extends Extension:
  val id: ExtensionId = ExtensionId("test.blocks")

  override def contribute: ExtensionContributions =
    ExtensionContributions(nodeTypes = Vector(RootNode, TextNode, BlockNode))
