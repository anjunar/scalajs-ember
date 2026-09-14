package ember.editor.integration

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.list.*
import ember.editor.link.*
import ember.editor.image.*
import ember.editor.code.*
import ember.editor.markdown.*
import ember.editor.json.*
import ember.editor.html.*
import ember.editor.standard.*
import ember.editor.ui.DocumentView
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("corpusFixtures")
object CorpusFixtures:
  // Text-run spans and keyed group comments are projection helpers, not document semantics.
  private def semanticHtml(document: Document): String =
    DocumentView
      .renderToHtml(document, ImageSupport.views)
      .replace("<span>", "")
      .replace("</span>", "")
      .replace("<!--ui:KeyedChildren:start-->", "")
      .replace("<!--ui:KeyedChildren:end-->", "")
  private def resolved(generator: NodeIdGenerator) = ExtensionResolver
    .resolve(
      Vector(
        RichText(generator),
        ListExtension(generator),
        LinkExtension(generator),
        ImageExtension(generator),
        CodeExtension(generator)
      )
    )
    .toOption
    .get
  @JSExport def markdown(source: String): js.Object =
    val generator = NodeIdGenerator.sequential("corpus")
    val schema    = resolved(generator).schema
    val support   = MarkdownSupports.everything()
    val first     =
      MarkdownCodec.decode(source, schema, support, generator, NodeId("root")).toOption.get.document
    val encoded = MarkdownCodec.encode(first, support).toOption.get.source
    val second  = MarkdownCodec
      .decode(encoded, schema, support, generator, NodeId("root"))
      .toOption
      .get
      .document
    val stable  = MarkdownCodec.encode(second, support).toOption.get.source
    val json    = DocumentJson.encodeToString(first, StandardJsonSupport.everything()).toOption.get
    val decoded = DocumentJson
      .decodeString(json, schema, StandardJsonSupport.everything())
      .toOption
      .get
      .document
    js.Dynamic.literal(
      canonical = encoded,
      stable = encoded == stable,
      semanticStable = semanticHtml(first) == semanticHtml(second),
      jsonStable = first == decoded,
      html = semanticHtml(second)
    )

  @JSExport def html(source: String): js.Object =
    val generator = NodeIdGenerator.sequential("html-corpus")
    val first     = HtmlImport
      .imported(
        source,
        resolved(generator).schema,
        StandardHtmlImport.everything(LinkUrlPolicy.default, MediaUrlPolicy.default),
        generator,
        NodeId("root")
      )
      .toOption
      .get
    js.Dynamic.literal(
      html = DocumentView.renderToHtml(first.document, ImageSupport.views),
      losses = js.Array(first.diagnostics.map(_.kind.toString)*)
    )
