package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.ui.*
import ember.editor.link.*
import ember.editor.richtext.*
import ui.core.render.SsrCursor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Semantic anchor rendering, and a link that moves rather than rebuilds (P14). */
final class LinkProjectionSpec extends AnyFlatSpec with Matchers {

  private val root   = NodeId("root")
  private val policy = LinkUrlPolicy.default

  private def open(): EditorSession =
    val generator = NodeIdGenerator.sequential("g")
    val resolved = ExtensionResolver
      .resolve(Vector(RichText(generator), LinkExtension(generator, policy)))
      .getOrElse(fail("Extensions nicht aufloesbar"))

    EditorSession
      .create(
        Document.unsafe(
          resolved.schema,
          root,
          Vector(
            RootNode(root, Vector(NodeId("p0"))),
            ParagraphNode(NodeId("p0"), Vector(NodeId("t0"))),
            TextNode(NodeId("t0"), "Hallo Welt!")
          )
        ),
        resolved,
        resolved.sessionConfig(errorSink = error => fail(s"Projektion: ${error.render}"))
      )
      .getOrElse(fail("Sitzung nicht erzeugbar"))

  private def edit(editor: EditorSession)(body: Transaction => Unit): Unit =
    editor.update(body) match
      case Right(_)    => ()
      case Left(error) => fail(s"abgewiesen: ${error.render}")

  private def link(editor: EditorSession, url: String): Unit =
    edit(editor)(
      _.select(
        RangeSelection(Point.textBefore(NodeId("t0"), 6), Point.textBefore(NodeId("t0"), 10))
      ): Unit
    )
    editor.dispatch(LinkCommands.SetLink, LinkTarget(policy.unsafe(url))): Unit

  private def html(editor: EditorSession, views: ViewSupport = LinkSupport.views): String =
    DocumentView.renderToHtml(editor.document, views)

  private def visible(source: String): String =
    source.replaceAll("<!--.*?-->", "").replaceAll("<[^>]*>", "")

  // ---------------------------------------------------------------------------------------
  // Semantic anchors
  // ---------------------------------------------------------------------------------------

  "A link" should "render as an anchor with its href" in {
    val editor = open()
    link(editor, "https://example.com/a")

    html(editor) should include("<a href=\"https://example.com/a\">")
    visible(html(editor)) shouldBe "Hallo Welt!"
  }

  it should "carry a title when it has one" in {
    val editor = open()
    edit(editor)(
      _.select(
        RangeSelection(Point.textBefore(NodeId("t0"), 0), Point.textBefore(NodeId("t0"), 5))
      ): Unit
    )
    editor.dispatch(
      LinkCommands.SetLink,
      LinkTarget(policy.unsafe("https://example.com"), Some("Beispiel"))
    ): Unit

    html(editor) should include("title=\"Beispiel\"")
  }

  it should "leave the title out when it has none" in {
    val editor = open()
    link(editor, "https://example.com")

    html(editor) should not include "title="
  }

  // ---------------------------------------------------------------------------------------
  // External-link attributes
  // ---------------------------------------------------------------------------------------

  "By default" should "set neither target nor rel" in {
    // P14, Abnahme: bewusst gesetzt -- und die bewusste Voreinstellung ist, nichts zu setzen.
    // `target="_blank"` ueberschreibt die Entscheidung des Lesers und bricht den Zurueck-Knopf.
    val editor = open()
    link(editor, "https://example.com")

    html(editor) should not include "target="
    html(editor) should not include "rel="
  }

  "An application that asks for it" should "get target and rel together" in {
    // Ohne `rel="noopener"` erreicht die geoeffnete Seite `window.opener`. Die beiden gehoeren
    // zusammen, also kommen sie zusammen.
    val editor = open()
    link(editor, "https://example.com")

    val views = ViewSupport.semantic(
      RichTextSupport.semantics ++ HtmlSupport.of(LinkSupport.openingExternally)
    )

    html(editor, views) should include("target=\"_blank\"")
    html(editor, views) should include("rel=\"noopener noreferrer\"")
  }

  it should "leave an internal link alone" in {
    val editor = open()
    link(editor, "/impressum")

    val views = ViewSupport.semantic(
      RichTextSupport.semantics ++ HtmlSupport.of(LinkSupport.openingExternally)
    )

    html(editor, views) should include("href=\"/impressum\"")
    html(editor, views) should not include "target="
  }

  it should "leave mailto alone" in {
    // Uebergibt an ein anderes Programm, nicht an eine andere Seite.
    val editor = open()
    link(editor, "mailto:jemand@example.com")

    val views = ViewSupport.semantic(
      RichTextSupport.semantics ++ HtmlSupport.of(LinkSupport.openingExternally)
    )

    html(editor, views) should not include "target="
  }

  // ---------------------------------------------------------------------------------------
  // The projection moves
  // ---------------------------------------------------------------------------------------

  "Linking a word" should "keep the run's component" in {
    // §15.1: "Move erhaelt Node-Identitaet." Der Lauf zieht in den Link, er wird nicht neu
    // gebaut -- ein Caret darin ueberlebte.
    val editor = open()
    val cursor = new SsrCursor()
    val view   = DocumentView.mount(editor, cursor, LinkSupport.views)

    edit(editor)(
      _.select(
        RangeSelection(Point.textBefore(NodeId("t0"), 0), Point.textBefore(NodeId("t0"), 11))
      ): Unit
    )
    val before = view.componentFor(NodeId("t0"))
    editor.dispatch(LinkCommands.SetLink, LinkTarget(policy.unsafe("https://example.com"))): Unit

    view.componentFor(NodeId("t0")) shouldBe before
    cursor.collectHtml() should include("<a ")
  }

  "Unlinking" should "keep it too" in {
    val editor = open()
    edit(editor)(
      _.select(
        RangeSelection(Point.textBefore(NodeId("t0"), 0), Point.textBefore(NodeId("t0"), 11))
      ): Unit
    )
    editor.dispatch(LinkCommands.SetLink, LinkTarget(policy.unsafe("https://example.com"))): Unit

    val cursor = new SsrCursor()
    val view   = DocumentView.mount(editor, cursor, LinkSupport.views)
    val before = view.componentFor(NodeId("t0"))

    edit(editor)(_.select(RangeSelection.caret(Point.textBefore(NodeId("t0"), 2))): Unit)
    editor.dispatch(LinkCommands.RemoveLink): Unit

    view.componentFor(NodeId("t0")) shouldBe before
    cursor.collectHtml() should not include "<a "
    visible(cursor.collectHtml()) shouldBe "Hallo Welt!"
  }

  "The rendering" should "be the same through a session and through renderToHtml" in {
    val editor = open()
    link(editor, "https://example.com")

    val cursor = new SsrCursor()
    DocumentView.mount(editor, cursor, LinkSupport.views, RenderProfile.Content)

    cursor.collectHtml() shouldBe html(editor)
  }
}
