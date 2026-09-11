package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.ui.*
import ember.editor.list.*
import ember.editor.richtext.*
import ui.core.render.SsrCursor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Lists as HTML, and as a projection that moves items instead of rebuilding them (P13). */
final class ListProjectionSpec extends AnyFlatSpec with Matchers {

  private val root = NodeId("root")

  /** A session with lists installed, on one paragraph. */
  private def open(): (EditorSession, NodeIdGenerator) =
    val generator = NodeIdGenerator.sequential("g")
    val resolved = ExtensionResolver
      .resolve(Vector(RichText(generator), ListExtension(generator)))
      .getOrElse(fail("Extensions nicht aufloesbar"))

    val document = Document.unsafe(
      resolved.schema,
      root,
      Vector(
        RootNode(root, Vector(NodeId("p0"))),
        ParagraphNode(NodeId("p0"), Vector(NodeId("t0"))),
        TextNode(NodeId("t0"), "Eins")
      )
    )

    val editor = EditorSession
      .create(
        document,
        resolved,
        resolved.sessionConfig(errorSink = error => fail(s"Projektion: ${error.render}"))
      )
      .getOrElse(fail("Sitzung nicht erzeugbar"))
    (editor, generator)

  private def edit(editor: EditorSession)(body: Transaction => Unit): Unit =
    editor.update(body) match
      case Right(_)    => ()
      case Left(error) => fail(s"abgewiesen: ${error.render}")

  private def bullets(editor: EditorSession): Unit =
    edit(editor)(_.select(RangeSelection.caret(Point.textBefore(NodeId("t0"), 0))): Unit)
    editor.dispatch(ListCommands.ToggleList, ListKind.Unordered): Unit

  private def html(editor: EditorSession): String =
    DocumentView.renderToHtml(editor.document, ListSupport.views)

  private def visible(source: String): String =
    source.replaceAll("<!--.*?-->", "").replaceAll("<[^>]*>", "")

  // ---------------------------------------------------------------------------------------
  // Semantic HTML
  // ---------------------------------------------------------------------------------------

  "A bulleted list" should "render as ul and li" in {
    val (editor, _) = open()
    bullets(editor)

    html(editor) should include("<ul>")
    html(editor) should include("<li>")
    visible(html(editor)) shouldBe "Eins"
  }

  "A numbered list" should "render as ol" in {
    val (editor, _) = open()
    edit(editor)(_.select(RangeSelection.caret(Point.textBefore(NodeId("t0"), 0))): Unit)
    editor.dispatch(ListCommands.ToggleList, ListKind.Ordered): Unit

    html(editor) should include("<ol>")
    html(editor) should not include "<ul>"
  }

  it should "carry a start that is not one" in {
    // §18.2: "Startnummer ... erhalten." A round trip that renumbered silently would be lossy.
    val (editor, _) = open()
    edit(editor)(_.select(RangeSelection.caret(Point.textBefore(NodeId("t0"), 0))): Unit)
    editor.dispatch(ListCommands.ToggleList, ListKind.Ordered): Unit

    val list = editor.document.inDocumentOrder.collectFirst { case value: ListNode => value }
      .getOrElse(fail("keine Liste"))
    edit(editor)(_.replace(list.id, list.copy(start = 7)): Unit)

    html(editor) should include("<ol start=\"7\">")
  }

  it should "leave the attribute out at one" in {
    // A `start="1"` on every list is noise no reader and no diff wants.
    val (editor, _) = open()
    edit(editor)(_.select(RangeSelection.caret(Point.textBefore(NodeId("t0"), 0))): Unit)
    editor.dispatch(ListCommands.ToggleList, ListKind.Ordered): Unit

    html(editor) should not include "start="
  }

  "A nested list" should "nest in the output" in {
    val (editor, generator) = open()
    bullets(editor)
    val item = editor.document.inDocumentOrder.collectFirst { case value: ListItemNode => value }
      .getOrElse(fail("kein Item"))

    // Als ganzer Teilbaum: eine leere Liste raeumt `emptyListGoes` im selben Commit wieder weg,
    // und der naechste Schritt faende sie nicht mehr.
    edit(editor) { tx =>
      tx.insert(
        item.id,
        1,
        ListNode(NodeId("nested"), Vector(NodeId("inner")), ListKind.Unordered),
        Vector(
          ListItemNode(NodeId("inner"), Vector(NodeId("ip"))),
          ParagraphNode(NodeId("ip"), Vector(NodeId("it"))),
          TextNode(NodeId("it"), "Tief")
        )
      ): Unit
    }

    html(editor) should include("<ul><!--ui:KeyedChildren:start--><li>")
    visible(html(editor)) shouldBe "EinsTief"
  }

  // ---------------------------------------------------------------------------------------
  // The projection moves, it does not rebuild
  // ---------------------------------------------------------------------------------------

  "Wrapping a paragraph" should "keep its component" in {
    // §15.1: "Move erhaelt Node-Identitaet." Wrapping is a move, so the paragraph that ends up
    // inside the item is the one that was there -- and a caret in it would survive.
    val (editor, _) = open()
    val cursor      = new SsrCursor()
    val view        = DocumentView.mount(editor, cursor, ListSupport.views)
    val before      = view.componentFor(NodeId("p0"))

    bullets(editor)

    view.componentFor(NodeId("p0")) shouldBe before
    cursor.collectHtml() should include("<li")
  }

  it should "keep the text run too" in {
    val (editor, _) = open()
    val cursor      = new SsrCursor()
    val view        = DocumentView.mount(editor, cursor, ListSupport.views)
    val before      = view.componentFor(NodeId("t0"))

    bullets(editor)

    view.componentFor(NodeId("t0")) shouldBe before
    visible(cursor.collectHtml()) shouldBe "Eins"
  }

  "Indenting an item" should "move the same component" in {
    val (editor, _) = open()
    edit(editor) { tx =>
      tx.insert(
        root,
        1,
        ParagraphNode(NodeId("p1"), Vector(NodeId("t1"))),
        Vector(TextNode(NodeId("t1"), "Zwei"))
      ): Unit
    }
    bullets(editor)
    edit(editor)(_.select(RangeSelection.caret(Point.textBefore(NodeId("t1"), 0))): Unit)
    editor.dispatch(ListCommands.ToggleList, ListKind.Unordered): Unit

    val cursor = new SsrCursor()
    val view   = DocumentView.mount(editor, cursor, ListSupport.views)
    val before = view.componentFor(NodeId("t1"))

    edit(editor)(_.select(RangeSelection.caret(Point.textBefore(NodeId("t1"), 0))): Unit)
    editor.dispatch(ListCommands.Indent): Unit

    view.componentFor(NodeId("t1")) shouldBe before
    visible(cursor.collectHtml()) shouldBe "EinsZwei"
  }

  "The rendering" should "be the same through a session and through renderToHtml" in {
    val (editor, _) = open()
    bullets(editor)

    val cursor = new SsrCursor()
    DocumentView.mount(editor, cursor, ListSupport.views, RenderProfile.Content)

    cursor.collectHtml() shouldBe html(editor)
  }
}
