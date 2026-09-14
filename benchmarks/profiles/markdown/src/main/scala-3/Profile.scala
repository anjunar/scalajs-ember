package ember.editor.profiles

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.markdown.*
import ember.editor.standard.*
import ember.editor.browsersupport.RichTextBindings
import scala.scalajs.js.annotation.JSExportTopLevel

private object MarkdownSetup:
  val generator = NodeIdGenerator.sequential("markdown")
  val resolved  = ExtensionResolver.resolve(Vector(RichText(generator))).toOption.get
  def decode(source: String): Document = MarkdownCodec
    .decode(source, resolved.schema, MarkdownSupports.richText, generator, NodeId("root"))
    .toOption
    .get
    .document
  def encode(document: Document): String =
    MarkdownCodec.encode(document, MarkdownSupports.richText).toOption.get.source

@JSExportTopLevel("profile")
object MarkdownProfileApp
    extends ProfileApi(
      "markdown",
      MarkdownSetup.resolved,
      RichTextSupport.views,
      MarkdownSetup.decode,
      MarkdownSetup.encode,
      RichTextBindings.input,
      RichTextBindings.keyboard
    )
