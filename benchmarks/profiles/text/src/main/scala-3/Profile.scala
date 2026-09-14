package ember.editor.profiles

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.standard.ParagraphSupport
import ember.editor.browsersupport.RichTextBindings
import scala.scalajs.js.annotation.JSExportTopLevel

private object TextSetup:
  val generator = NodeIdGenerator.sequential("text")
  val resolved  = ExtensionResolver.resolve(Vector(RichText(generator))).toOption.get
  def decode(source: String): Document = Document.unsafe(
    resolved.schema,
    NodeId("root"),
    Vector(
      RootNode(NodeId("root"), Vector(NodeId("p"))),
      ParagraphNode(NodeId("p"), Vector(NodeId("t"))),
      TextNode(NodeId("t"), source)
    )
  )

@JSExportTopLevel("profile")
object TextProfile
    extends ProfileApi(
      "text",
      TextSetup.resolved,
      ParagraphSupport.views,
      TextSetup.decode,
      document => document.inDocumentOrder.collect { case t: TextNode => t.text }.mkString,
      RichTextBindings.input,
      RichTextBindings.keyboard
    )
