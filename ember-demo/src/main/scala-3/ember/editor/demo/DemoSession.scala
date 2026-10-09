package ember.editor.demo

import ember.editor.browser.{BrowserInputController, CompositionHolder}
import ember.editor.clipboard.ClipboardExtension
import ember.editor.core.*
import ember.editor.code.CodeExtension
import ember.editor.history.{History, HistoryConfig}
import ember.editor.image.{ImageExtension, MediaUrlPolicy}
import ember.editor.link.{LinkExtension, LinkUrlPolicy}
import ember.editor.list.ListExtension
import ember.editor.json.*
import ember.editor.richtext.*
import ember.editor.markdown.{LossPolicy, MarkdownCodec, MarkdownProfile}
import ember.editor.standard.{MarkdownSupports, StandardJsonSupport, TableSupport}
import ember.editor.table.TableExtension
import ember.editor.ui.DocumentView
import ember.editor.html.RenderProfile

/** Every example runs the local Ember projects and owns its document and history. */
final class DemoSession(val example: DemoExample):
  val generator     = NodeIdGenerator.sequential(example.id)
  val history       = new History(HistoryConfig.default)
  val compositions  = new CompositionHolder
  val media         = MediaUrlPolicy.default
  val codecs        = StandardJsonSupport.everything(media = media) ++ TableSupport.json
  val markdownRules =
    MarkdownSupports.everything(LinkUrlPolicy.default, media) ++ TableSupport.markdownRules
  private object CompositionGate extends Extension:
    val id                  = ExtensionId("ember.demo.composition-gate")
    override def contribute = ExtensionContributions(
      preCommitRules = Vector(BrowserInputController.busyRule(compositions))
    )
  private val resolved = ExtensionResolver
    .resolve(
      Vector(
        RichText(generator),
        ListExtension(generator),
        LinkExtension(generator),
        CodeExtension(generator),
        ImageExtension(generator, media),
        TableExtension(generator),
        new ClipboardExtension(generator),
        history,
        CompositionGate
      )
    )
    .fold(errors => throw new IllegalStateException(errors.map(_.render).mkString("; ")), identity)

  private val initial: Document = if example.markdown.trim.isEmpty then
    RichText
      .emptyDocument(resolved.schema, generator)
      .fold(
        errors => throw new IllegalStateException(errors.map(_.render).mkString("; ")),
        identity
      )
  else
    MarkdownCodec
      .decode(
        example.markdown,
        resolved.schema,
        markdownRules,
        generator,
        NodeId("document"),
        MarkdownProfile.commonMarkSafeWithTables
      )
      .fold(error => throw new IllegalArgumentException(error.message), _.document)

  val session = EditorSession
    .create(initial, resolved, resolved.sessionConfig())
    .fold(errors => throw new IllegalStateException(errors.map(_.render).mkString("; ")), identity)
  session.update(_.setSelection(RichText.caretAtStart(session.document)))

  def reset(): Either[EditorError, Unit] = session
    .update(TransactionMeta(origin = Origin.Import)) { tx =>
      val document = initial
      tx.restore(document, RichText.caretAtStart(document))
    }
    .map(_ => ())

  def json: String = DocumentJson
    .encode(session.document, codecs)
    .fold(errors => errors.map(_.render).mkString("\n"), value => JsonText.renderPretty(value))

  def markdown: String = MarkdownCodec
    .encode(session.document, markdownRules, LossPolicy.AllowLossy)
    .fold(
      _.message,
      result =>
        result.source +
          (if result.losses.isEmpty then ""
           else result.losses.map(_.message).mkString("\n\nExporthinweise:\n", "\n", ""))
    )

  def strictMarkdown: Either[EditorError, String] =
    MarkdownCodec.encode(session.document, markdownRules, LossPolicy.Strict).map(_.source)

  def html: String =
    DocumentView.renderToHtml(session.document, TableSupport.views, RenderProfile.Content)

  def outline: String =
    def walk(id: NodeId, depth: Int): Vector[String] =
      session.document.node(id).toVector.flatMap { node =>
        val kind  = session.document.schema.descriptorFor(node).map(_.typeId.value).getOrElse("?")
        val value = node match
          case text: TextNode => s"  ${text.text}"
          case _              => ""
        val line = s"${"  " * depth}$kind  [${id.value}]$value"
        node match
          case element: ElementNode => line +: element.children.flatMap(walk(_, depth + 1))
          case _                    => Vector(line)
      }
    walk(session.document.rootId, 0).mkString("\n")

  def wordCount: Int = session.document.inDocumentOrder
    .collect { case text: TextNode =>
      text.text
    }
    .mkString(" ")
    .split("\\s+")
    .count(_.nonEmpty)

  def dispose(): Unit =
    compositions.release()
    session.dispose()
