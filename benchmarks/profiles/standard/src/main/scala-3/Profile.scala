package ember.editor.profiles

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.list.*
import ember.editor.link.*
import ember.editor.image.*
import ember.editor.code.*
import ember.editor.history.*
import ember.editor.markdown.*
import ember.editor.standard.*
import ember.editor.browsersupport.EditorBindings
import scala.scalajs.js.annotation.JSExportTopLevel

private object StandardSetup:
  val generator = NodeIdGenerator.sequential("standard")
  val resolved  = ExtensionResolver
    .resolve(
      Vector(
        RichText(generator),
        ListExtension(generator),
        LinkExtension(generator),
        ImageExtension(generator),
        CodeExtension(generator),
        new History()
      )
    )
    .toOption
    .get
  def decode(source: String): Document = MarkdownCodec
    .decode(source, resolved.schema, MarkdownSupports.everything(), generator, NodeId("root"))
    .toOption
    .get
    .document
  def encode(document: Document): String =
    MarkdownCodec.encode(document, MarkdownSupports.everything()).toOption.get.source

@JSExportTopLevel("profile")
object StandardProfileApp
    extends ProfileApi(
      "standard",
      StandardSetup.resolved,
      ImageSupport.views,
      StandardSetup.decode,
      StandardSetup.encode,
      EditorBindings.everything,
      EditorBindings.everythingKeyboard
    )
