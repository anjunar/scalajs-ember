package ember.editor.markdown

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
private object TestProfile extends Extension:
  val id: ExtensionId = ExtensionId("test.profile")

  override def contribute: ExtensionContributions =
    ExtensionContributions(nodeTypes = Vector(RootNode, TextNode, Box))
