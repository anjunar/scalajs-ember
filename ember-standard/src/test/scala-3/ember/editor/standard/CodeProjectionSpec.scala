package ember.editor.standard

import ember.editor.code.*
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.jfx.*
import ember.editor.richtext.*
import jfx.core.render.SsrCursor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** `pre`/`code` rendering, and what a type change costs the projection (P15). */
final class CodeProjectionSpec extends AnyFlatSpec with Matchers {

  private val root = NodeId("root")

  private def open(text: String = "val x = 1"): EditorSession =
    val generator = NodeIdGenerator.sequential("g")
    val resolved = ExtensionResolver
      .resolve(Vector(RichText(generator), CodeExtension(generator)))
      .getOrElse(fail("Extensions nicht aufloesbar"))

    EditorSession
      .create(
        Document.unsafe(
          resolved.schema,
          root,
          Vector(
            RootNode(root, Vector(NodeId("p0"))),
            ParagraphNode(NodeId("p0"), Vector(NodeId("t0"))),
            TextNode(NodeId("t0"), text)
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

  private def toCode(editor: EditorSession, info: CodeInfo): Unit =
    edit(editor)(_.select(RangeSelection.caret(Point.textBefore(NodeId("t0"), 0))): Unit)
    editor.dispatch(CodeCommands.ToggleCodeBlock, info): Unit

  private def html(editor: EditorSession): String =
    DocumentView.renderToHtml(editor.document, CodeSupport.views)

  private def visible(source: String): String =
    source.replaceAll("<!--.*?-->", "").replaceAll("<[^>]*>", "")

  // ---------------------------------------------------------------------------------------
  // Semantic HTML
  // ---------------------------------------------------------------------------------------

  "A code block" should "render as pre and code" in {
    // Beide verdienen ihren Platz: `pre` erhaelt den Whitespace, `code` sagt, was der Inhalt ist.
    val editor = open()
    toCode(editor, CodeInfo.empty)

    html(editor) should include("<pre>")
    html(editor) should include("<code>")
    visible(html(editor)) shouldBe "val x = 1"
  }

  it should "put the two tags around one node" in {
    // Sie in zwei Dokumentknoten aufzuteilen hiesse, dem Dokument eine Struktur anzudichten,
    // die nur die Darstellung braucht.
    val editor = open()
    toCode(editor, CodeInfo.empty)

    editor.document.inDocumentOrder.count(_.isInstanceOf[CodeBlockNode]) shouldBe 1
    html(editor) should include("<pre><code>")
  }

  it should "carry the language as a class" in {
    val editor = open()
    toCode(editor, CodeInfo.of("scala"))

    html(editor) should include("<pre class=\"language-scala\">")
  }

  it should "write no class without a language" in {
    val editor = open()
    toCode(editor, CodeInfo.empty)

    html(editor) should not include "class="
  }

  it should "keep the meta out of the HTML" in {
    // `{highlight=3-5}` ist Werkzeugkonfiguration und gehoert in den Fence (§18.2), nicht in
    // das HTML, das ein Leser bekommt. Im Dokument bleibt es -- P18 schreibt es zurueck.
    val editor = open()
    toCode(editor, CodeInfo.parse("scala {highlight=3-5}"))

    html(editor) should include("language-scala")
    html(editor) should not include "highlight"

    editor.document.inDocumentOrder
      .collectFirst { case code: CodeBlockNode => code.info.meta }
      .flatten shouldBe Some("{highlight=3-5}")
  }

  it should "keep newlines and blank lines in the output" in {
    val editor = open("eins\n\ndrei")
    toCode(editor, CodeInfo.empty)

    visible(html(editor)) shouldBe "eins\n\ndrei"
  }

  // ---------------------------------------------------------------------------------------
  // The projection
  // ---------------------------------------------------------------------------------------

  "Turning a paragraph into code" should "replace the view" in {
    // §15.1: "Ein typwechselnder Node unter gleicher ID ist eine explizite View-Ersetzung."
    // `<p>` wird nicht zu `<pre>`, indem man Attribute schreibt.
    val editor = open()
    val cursor = new SsrCursor()
    val view   = DocumentView.mount(editor, cursor, CodeSupport.views)
    val before = view.componentFor(NodeId("p0"))

    toCode(editor, CodeInfo.of("scala"))

    view.componentFor(NodeId("p0")) should not be before
    cursor.collectHtml() should include("<pre")
    cursor.collectHtml() should not include "<p "
  }

  it should "leave its siblings alone" in {
    val editor = open()
    edit(editor) { tx =>
      tx.insert(
        root,
        1,
        ParagraphNode(NodeId("p1"), Vector(NodeId("t1"))),
        Vector(TextNode(NodeId("t1"), "Danach"))
      ): Unit
    }

    val cursor    = new SsrCursor()
    val view      = DocumentView.mount(editor, cursor, CodeSupport.views)
    val untouched = view.componentFor(NodeId("p1"))

    toCode(editor, CodeInfo.empty)

    view.componentFor(NodeId("p1")) shouldBe untouched
    visible(cursor.collectHtml()) shouldBe "val x = 1Danach"
  }

  "Adding a language later" should "not replace the view" in {
    // Nur ein Attribut aendert sich -- Tag und innere Tags bleiben, also passt die Komponente
    // weiter und wird nachgefuehrt statt neu gebaut.
    val editor = open()
    toCode(editor, CodeInfo.empty)

    val cursor = new SsrCursor()
    val view   = DocumentView.mount(editor, cursor, CodeSupport.views)
    val before = view.componentFor(NodeId("p0"))

    edit(editor)(_.select(RangeSelection.caret(Point.textBefore(NodeId("t0"), 0))): Unit)
    editor.dispatch(CodeCommands.SetCodeInfo, CodeInfo.of("scala")): Unit

    view.componentFor(NodeId("p0")) shouldBe before
    cursor.collectHtml() should include("language-scala")
  }

  "The rendering" should "be the same through a session and through renderToHtml" in {
    val editor = open()
    toCode(editor, CodeInfo.of("scala"))

    val cursor = new SsrCursor()
    DocumentView.mount(editor, cursor, CodeSupport.views, RenderProfile.Content)

    cursor.collectHtml() shouldBe html(editor)
  }
}
