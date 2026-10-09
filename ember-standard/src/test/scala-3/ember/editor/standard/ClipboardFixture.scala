package ember.editor.standard

import ember.editor.clipboard.*
import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.history.History
import ember.editor.link.*
import ember.editor.list.*
import ember.editor.code.*
import ember.editor.image.*
import ember.editor.html.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

private[standard] final class ClipboardFixture(
    source: String = "<p>Hello world</p>",
    rules: Vector[PreCommitRule] = Vector.empty,
    val generator: NodeIdGenerator = NodeIdGenerator.sequential("g")
):
  val history    = new History()
  val extensions = ExtensionResolver
    .resolve(
      Vector(
        RichText(generator),
        history,
        new ClipboardExtension(generator),
        LinkExtension(generator),
        ListExtension(generator),
        CodeExtension(generator),
        ImageExtension(generator, MediaUrlPolicy.default)
      )
    )
    .toOption
    .get
  val schema   = extensions.schema
  val html     = StandardHtmlImport.everything(LinkUrlPolicy.default, MediaUrlPolicy.default)
  val document =
    HtmlImport.imported(source, schema, html, generator, NodeId("root")).toOption.get.document
  val session = EditorSession
    .create(document, extensions, extensions.sessionConfig().copy(preCommitRules = rules))
    .toOption
    .get
  val codec = new ClipboardCodec(
    "standard/1",
    schema,
    StandardJsonSupport.everything(),
    ImageSupport.everything,
    html
  )
  val service                = new ClipboardService(session, codec)
  def runs: Vector[TextNode] = session.document.inDocumentOrder.collect { case t: TextNode =>
    t
  }.toVector
  def text: String                        = codec.text(session.document)
  def select(from: Int, until: Int): Unit = session
    .update(
      _.select(
        RangeSelection(Point.textBefore(runs.head.id, from), Point.textBefore(runs.head.id, until))
      ): Unit
    )
    .toOption
    .get: Unit
  def caret(offset: Int): Unit                                       = select(offset, offset)
  def paste(value: String): Either[ClipboardError, ClipboardDecoded] =
    service.paste(ClipboardData(Map(ClipboardMime.Text -> value)))
