package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.jfx.*
import ember.editor.richtext.*
import ember.editor.richtext.StandardMarks.Strong
import jfx.core.component.AbstractComponent
import jfx.core.layout.TextComponent
import jfx.core.render.SsrCursor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

/** Der Rendererbeweis aus P09 (Architektur §§5–7, 15, 19).
  *
  * Laeuft headless gegen einen `SsrCursor`. Das ist keine Notloesung, sondern derselbe Weg:
  * `DocumentView` nimmt einen beliebigen Cursor, und dass SSR und Browser dasselbe liefern,
  * ist damit keine Absprache zwischen zwei Implementierungen. Die Browserseite -- DOM-Identitaet
  * und Schreibzugriffe -- prueft `projection.spec.mjs` im Harness.
  */
final class ProjectionSpec extends AnyFlatSpec with Matchers {

  private val root = NodeId("root")

  private def open(paragraphs: String*): (EditorSession, NodeIdGenerator) =
    val generator = NodeIdGenerator.sequential("g")
    val resolved = ExtensionResolver
      .resolve(Vector(RichText(generator)))
      .getOrElse(fail("Extensions nicht aufloesbar"))

    val blocks = paragraphs.zipWithIndex.map((text, index) =>
      (NodeId(s"p$index"), NodeId(s"t$index"), text)
    )
    val nodes = RootNode(root, blocks.map(_._1).toVector) +:
      blocks.flatMap((block, text, content) =>
        Vector(ParagraphNode(block, Vector(text)), TextNode(text, content))
      )

    val document = Document.unsafe(resolved.schema, root, nodes.toVector)
    val editor = EditorSession
      .create(
        document,
        resolved,
        // Ein geworfener Listener geht sonst still an den Error-Sink -- und die Projektion ist
        // ein Listener. Ohne diese Zeile schweigt ein Projektionsfehler den Test an.
        resolved.sessionConfig(errorSink = error => fail(s"Projektion: ${error.render}"))
      )
      .getOrElse(fail("Sitzung nicht erzeugbar"))
    (editor, generator)

  private def mounted(editor: EditorSession): (DocumentView, SsrCursor) =
    val cursor = new SsrCursor()
    (DocumentView.mount(editor, cursor, RichTextSupport.views), cursor)

  private def id(value: String): NodeId = NodeId(value)

  /** Der sichtbare Text der Ausgabe, ohne Tags und Gruppenanker.
    *
    * Fuer Aussagen ueber die Reihenfolge. Seit die Textlaeufe je einen `span` tragen (§15.1),
    * stehen zwei aufeinanderfolgende Laeufe im Markup nicht mehr nebeneinander -- und genau
    * das ist der Zweck des Wrappers. Die Reihenfolge steht trotzdem fest, sie steht nur eine
    * Ebene tiefer.
    */
  private def visible(html: String): String =
    html.replaceAll("<!--.*?-->", "").replaceAll("<[^>]*>", "")

  /** Macht einen Lauf fett -- damit er nicht mit seinem Nachbarn verschmilzt (§8.2, P12). */
  private def bolden(editor: EditorSession, node: NodeId): Unit =
    edit(editor) { tx =>
      editor.document.node(node).collect { case run: TextNode => run }.foreach { run =>
        tx.replace(node, run.copy(marks = MarkSet.of(Strong))): Unit
      }
    }

  private def edit(editor: EditorSession)(body: Transaction => Unit): Unit =
    editor.update(body) match
      case Right(_)    => ()
      case Left(error) => fail(s"abgewiesen: ${error.render}")

  // ---------------------------------------------------------------------------------------
  // Erstes Rendering
  // ---------------------------------------------------------------------------------------

  "The projection" should "render semantic HTML" in {
    // §16: Absaetze, semantische Tags. Ein Leser ohne Stylesheet und ein Screenreader sollen
    // dasselbe Dokument vorfinden.
    val (editor, _)  = open("Hallo", "Welt")
    val (_, cursor)  = mounted(editor)

    cursor.collectHtml() should include("<p")
    cursor.collectHtml() should include("Hallo")
    cursor.collectHtml() should include("Welt")
  }

  it should "wrap the document in an article" in {
    val (editor, _) = open("Hallo")
    val (_, cursor) = mounted(editor)

    cursor.collectHtml() should startWith("<article")
  }

  it should "index every node" in {
    val (editor, _) = open("Hallo", "Welt")
    val (view, _)   = mounted(editor)

    view.size shouldBe 5 // root + 2 Absaetze + 2 Textlaeufe
    view.componentFor(id("t0")) shouldBe defined
    view.componentFor(id("p1")) shouldBe defined
  }

  // ---------------------------------------------------------------------------------------
  // Renderprofile
  // ---------------------------------------------------------------------------------------

  "The content profile" should "carry no editor metadata" in {
    // §19.1: browserseitige Wrapper und Editor-Attribute verschwinden beim Austausch. Sie
    // entstehen hier gar nicht erst, statt hinterher entfernt zu werden.
    val (editor, _) = open("Hallo")

    val html = DocumentView.renderToHtml(editor.document, ParagraphSupport.views)

    html should not include "data-ember-"
    html should include("Hallo")
  }

  "The editor profile" should "identify its nodes" in {
    val (editor, _) = open("Hallo")

    val html = DocumentView.renderToHtml(
      editor.document,
      ParagraphSupport.views,
      RenderProfile.Editor
    )

    html should include("data-ember-node=\"p0\"")
  }

  // ---------------------------------------------------------------------------------------
  // Gezielte Aktualisierung
  // ---------------------------------------------------------------------------------------

  "A text edit" should "keep every component instance" in {
    // Die Kernzusicherung von §15.1: unveraenderte Knoten werden nicht erneut komponiert. Ein
    // neu erzeugter Textknoten naehme Caret und Selection mit ins Grab.
    val (editor, _) = open("Hallo", "Welt")
    val (view, _)   = mounted(editor)
    val before      = view.componentFor(id("t0"))
    val untouched   = view.componentFor(id("t1"))

    edit(editor)(_.spliceText(id("t0"), 5, 0, "!"))

    view.componentFor(id("t0")) shouldBe before
    view.componentFor(id("t1")) shouldBe untouched
  }

  it should "reach the rendered output" in {
    val (editor, _)  = open("Hallo")
    val (_, cursor)  = mounted(editor)

    edit(editor)(_.spliceText(id("t0"), 5, 0, " Welt"))

    cursor.collectHtml() should include("Hallo Welt")
  }

  it should "not traverse a large document" in {
    // §15.1, Akzeptanz: "Ein 10k-Node-Dokument wird fuer einen Textedit nicht vollstaendig
    // traversiert." Gemessen an den Komponenten, die eine Sicht tatsaechlich anfasst.
    val generator = NodeIdGenerator.sequential("g")
    val resolved = ExtensionResolver
      .resolve(Vector(RichText(generator)))
      .getOrElse(fail("Extensions nicht aufloesbar"))

    val count  = 2000
    val blocks = (0 until count).map(index => (NodeId(s"p$index"), NodeId(s"t$index")))
    val nodes = RootNode(root, blocks.map(_._1).toVector) +:
      blocks.flatMap((block, text) =>
        Vector(ParagraphNode(block, Vector(text)), TextNode(text, s"Absatz $text"))
      )
    val document = Document.unsafe(resolved.schema, root, nodes.toVector)
    val editor = EditorSession
      .create(document, resolved, resolved.sessionConfig())
      .getOrElse(fail("Sitzung nicht erzeugbar"))

    val touched = mutable.Set.empty[String]
    val counting = ViewSupport.of(
      ParagraphSupport.views.views.map(spyOn(_, touched))*
    )
    val cursor = new SsrCursor()
    DocumentView.mount(editor, cursor, counting)
    touched.clear()

    edit(editor)(_.spliceText(id("t0"), 0, 0, "X"))

    // Ein Splice geht ueber den ChangeSet direkt an den Textlauf; die Kindliste des Absatzes
    // aendert sich nicht, also fasst die Gruppe ihn gar nicht erst an.
    withClue(s"angefasst: ${touched.toVector.sorted.take(10)}: ") {
      touched.size should be < 5
    }
    editor.document.size shouldBe count * 2 + 1
  }

  /** Ein Adapter, der jede Aktualisierung protokolliert. */
  private def spyOn(view: NodeView[?], log: mutable.Set[String]): NodeView[?] =
    wrap(view, log)

  private def wrap[N <: EditorNode](view: NodeView[N], log: mutable.Set[String]): NodeView[N] =
    new NodeView[N]:
      val nodeType: NodeType[N] = view.nodeType
      def create(node: N, profile: RenderProfile): AbstractComponent =
        view.create(node, profile)
      def accepts(component: AbstractComponent, node: N, profile: RenderProfile): Boolean =
        view.accepts(component, node, profile)
      def update(component: AbstractComponent, node: N, profile: RenderProfile): Unit =
        log += node.id.value
        view.update(component, node, profile)

  // ---------------------------------------------------------------------------------------
  // Struktur
  // ---------------------------------------------------------------------------------------

  "A new paragraph" should "appear in the output" in {
    val (editor, _) = open("Hallo")
    val (view, cursor) = mounted(editor)

    edit(editor) { tx =>
      tx.select(RangeSelection.caret(Point.textBefore(id("t0"), 5)))
      tx.dispatch(RichText.InsertParagraph)
      tx.dispatch(RichText.InsertText, "Welt")
    }

    cursor.collectHtml() should include("Hallo")
    cursor.collectHtml() should include("Welt")
    view.size shouldBe editor.document.size
  }

  "A removed node" should "leave the index" in {
    val (editor, _) = open("Hallo", "Welt")
    val (view, _)   = mounted(editor)

    edit(editor)(_.remove(id("p1")))

    view.componentFor(id("p1")) shouldBe None
    view.componentFor(id("t1")) shouldBe None
    view.size shouldBe editor.document.size
  }

  "A moved node" should "keep its component instance" in {
    // §15.1: "Move erhaelt Node-Identitaet." Ueber `transferTo`, damit beide Schluesselindizes
    // konsistent bleiben.
    //
    // Der bewegte Lauf ist fett: seit P12 wachsen benachbarte Laeufe mit gleichen Marks wieder
    // zusammen (§8.2), und ein zusammengefuehrter Knoten hat keine Komponente mehr, ueber die
    // sich Identitaet pruefen liesse. Verschiedene Marks halten die beiden auseinander.
    val (editor, _) = open("Erster", "Zweiter")
    bolden(editor, id("t1"))
    val (view, _)   = mounted(editor)
    val before      = view.componentFor(id("t1"))

    edit(editor)(_.move(id("t1"), id("p0"), 1))

    view.componentFor(id("t1")) shouldBe before
    editor.document.childrenOf(id("p0")) shouldBe Vector(id("t0"), id("t1"))
  }

  it should "show up under its new parent" in {
    val (editor, _)    = open("Erster", "Zweiter")
    val (_, cursor)    = mounted(editor)

    edit(editor)(_.move(id("t1"), id("p0"), 1))

    visible(cursor.collectHtml()) should include("ErsterZweiter")
  }

  "Reordering siblings" should "follow the document order" in {
    val (editor, _) = open("A")
    val (_, cursor) = mounted(editor)

    edit(editor)(_.insert(id("p0"), 1, TextNode(id("extra"), "B", MarkSet.of(Strong))))
    visible(cursor.collectHtml()) should include("AB")

    edit(editor)(_.move(id("extra"), id("p0"), 0))
    visible(cursor.collectHtml()) should include("BA")
  }

  // ---------------------------------------------------------------------------------------
  // afterProjection
  // ---------------------------------------------------------------------------------------

  "onProjected" should "report the rendered revision after the projection" in {
    // §5: Commit und Rendering sind zwei Zeitpunkte. Wer wissen will, ob etwas zu sehen ist,
    // fragt hier -- nicht den Commit-Listener.
    val (editor, _) = open("Hallo")
    val (view, cursor) = mounted(editor)
    val seen = mutable.ArrayBuffer.empty[(Long, Boolean)]

    view.onProjected(revision =>
      seen += ((revision.value, cursor.collectHtml().contains("Hallo!")))
    )

    edit(editor)(_.spliceText(id("t0"), 5, 0, "!"))

    seen.toVector shouldBe Vector((1L, true))
  }

  it should "state the rendered revision for anyone registering later" in {
    // Beim Mount kann es noch keinen Zuhoerer geben -- die Ansicht wird erst danach
    // zurueckgegeben. Wer spaeter dazukommt, hat trotzdem nichts verpasst.
    val (editor, _) = open("Hallo")
    val (view, _)   = mounted(editor)

    view.projectedRevision shouldBe editor.state.revision

    edit(editor)(_.spliceText(id("t0"), 5, 0, "!"))

    view.projectedRevision shouldBe editor.state.revision
  }

  it should "stop after the view is disposed" in {
    val (editor, _) = open("Hallo")
    val (view, _)   = mounted(editor)
    var calls       = 0
    view.onProjected(_ => calls += 1)

    view.dispose()
    edit(editor)(_.spliceText(id("t0"), 0, 0, "X"))

    calls shouldBe 0
    view.isDisposed shouldBe true
  }

  // ---------------------------------------------------------------------------------------
  // Zwei Sitzungen
  // ---------------------------------------------------------------------------------------

  "Two editors" should "never confuse their nodes" in {
    // §15.1: "zwei Editoren verwechselt keine IDs." Beide Dokumente benutzen hier absichtlich
    // dieselben IDs -- IDs gelten dokumentlokal (§8.3), und jede Sicht fuehrt ihren eigenen
    // Index.
    val (first, _)  = open("Erster")
    val (second, _) = open("Zweiter")
    val (viewA, cursorA) = mounted(first)
    val (viewB, cursorB) = mounted(second)

    edit(first)(_.spliceText(id("t0"), 0, 0, "X"))

    viewA.componentFor(id("t0")) should not be viewB.componentFor(id("t0"))
    cursorA.collectHtml() should include("XErster")
    cursorB.collectHtml() should include("Zweiter")
    cursorB.collectHtml() should not include "X"
  }

  // ---------------------------------------------------------------------------------------
  // SSR und Browser derselbe Weg
  // ---------------------------------------------------------------------------------------

  "The initial rendering" should "be the same through a session and through renderToHtml" in {
    // Der Vergleich, den P08 noch schuldig blieb: derselbe Testfall, beide Wege. Er ist hier
    // trivial gruen, und genau das ist die Aussage -- es gibt nur eine Beschreibung.
    val (editor, _) = open("Hallo", "Welt")
    val cursor      = new SsrCursor()
    DocumentView.mount(editor, cursor, ParagraphSupport.views, RenderProfile.Content)

    val direct = DocumentView.renderToHtml(editor.document, ParagraphSupport.views)

    cursor.collectHtml() shouldBe direct
  }

  it should "carry no raw adjacent text nodes" in {
    // §15.1, Risiken: "rohe benachbarte SSR-Textnodes vermeiden." Zwei Laeufe nebeneinander
    // duerfen in der Ausgabe nicht zu einem verschmelzen, sonst laesst sich beim Hydrieren
    // nicht mehr sagen, wo der eine aufhoert.
    val (editor, _) = open("Hallo")
    edit(editor)(_.insert(id("p0"), 1, TextNode(id("zweiter"), "Welt", MarkSet.of(Strong))))

    val html = DocumentView.renderToHtml(
      editor.document,
      ParagraphSupport.views,
      RenderProfile.Editor
    )

    html should include("Hallo")
    html should include("Welt")
    // Jeder Lauf traegt seinen eigenen Wrapper, die beiden stehen also nicht unmittelbar
    // nebeneinander im Text.
    html should not include ">HalloWelt<"
    html.split("<span").length - 1 shouldBe 2
    visible(html) shouldBe "HalloWelt"
  }

  // ---------------------------------------------------------------------------------------
  // Marks and block types (P12)
  // ---------------------------------------------------------------------------------------

  "A marked run" should "render its marks as inner tags" in {
    // §15.1: "Mark-Aenderungen koennen semantische Innentags ersetzen." Innen und nicht am
    // Wrapper: der Wrapper traegt die Knoten-ID und ueberlebt eine Formatierungsaenderung.
    val (editor, _) = open("Hallo")
    edit(editor) { tx =>
      tx.replace(id("t0"), TextNode(id("t0"), "Hallo", MarkSet.of(Strong, StandardMarks.Emphasis)))
    }

    val html = DocumentView.renderToHtml(editor.document, RichTextSupport.views)

    html should include("<span><em><strong>Hallo</strong></em></span>")
  }

  it should "use semantic tags, not presentational ones" in {
    // §16 verlangt semantisches HTML: `strong` sagt, was gemeint ist, `b` nur, wie es aussieht.
    val (editor, _) = open("Hallo")
    edit(editor)(_.replace(id("t0"), TextNode(id("t0"), "Hallo", MarkSet.of(Strong))))

    val html = DocumentView.renderToHtml(editor.document, RichTextSupport.views)

    html should include("<strong>")
    html should not include "<b>"
  }

  it should "keep the wrapper across a mark change" in {
    // Der Punkt des Wrappers: die Komponente, an der die ID haengt, bleibt dieselbe.
    val (editor, _) = open("Hallo")
    val (view, _)   = mounted(editor)
    val before      = view.componentFor(id("t0"))

    edit(editor)(_.replace(id("t0"), TextNode(id("t0"), "Hallo", MarkSet.of(Strong))))

    view.componentFor(id("t0")) shouldBe before
  }

  it should "replace the inner tags when the marks change" in {
    val (editor, _)  = open("Hallo")
    val (_, cursor)  = mounted(editor)

    edit(editor)(_.replace(id("t0"), TextNode(id("t0"), "Hallo", MarkSet.of(Strong))))
    cursor.collectHtml() should include("<strong>Hallo</strong>")

    edit(editor)(_.replace(id("t0"), TextNode(id("t0"), "Hallo", MarkSet.of(StandardMarks.Emphasis))))
    cursor.collectHtml() should include("<em>Hallo</em>")
    cursor.collectHtml() should not include "<strong>"
  }

  it should "drop the tags again when the marks go" in {
    val (editor, _) = open("Hallo")
    val (_, cursor) = mounted(editor)
    edit(editor)(_.replace(id("t0"), TextNode(id("t0"), "Hallo", MarkSet.of(Strong))))

    edit(editor)(_.replace(id("t0"), TextNode(id("t0"), "Hallo")))

    visible(cursor.collectHtml()) shouldBe "Hallo"
    cursor.collectHtml() should not include "<strong>"
  }

  "The block types" should "render semantically" in {
    val (editor, _) = open("Hallo")
    edit(editor) { tx =>
      tx.replace(id("p0"), HeadingNode(id("p0"), Vector(id("t0")), HeadingLevel.H2))
      tx.insert(root, 1, ThematicBreakNode(id("hr")))
    }

    val html = DocumentView.renderToHtml(editor.document, RichTextSupport.views)

    html should include("<h2>")
    html should include("<hr>")
  }

  it should "keep a hard break distinct from a soft one" in {
    // §8.2: beide bleiben unterscheidbar, damit Markdown und HTML ihre Bedeutung behalten.
    val (editor, _) = open("Hallo")
    edit(editor) { tx =>
      tx.insert(id("p0"), 1, BreakNode(id("hard"), BreakKind.Hard))
      tx.insert(id("p0"), 2, BreakNode(id("soft"), BreakKind.Soft))
    }

    val html = DocumentView.renderToHtml(editor.document, RichTextSupport.views)

    html should include("<br>")
    html should include("<span></span>")
  }

  it should "wrap a quote in blockquote" in {
    val (editor, _) = open("Hallo")
    edit(editor) { tx =>
      tx.insert(root, 0, QuoteNode(id("q"), Vector.empty))
      tx.move(id("p0"), id("q"), 0)
    }

    val html = DocumentView.renderToHtml(editor.document, RichTextSupport.views)

    html should include("<blockquote>")
    visible(html) shouldBe "Hallo"
  }

  "A node that changes its type" should "be replaced, not patched" in {
    // §15.1: "Ein typwechselnder Node unter gleicher ID ist eine explizite View-Ersetzung."
    // Seit P12 gibt es den Fall wirklich -- `SetHeading` wechselt den Tag unter gleicher ID.
    val (editor, _) = open("Hallo")
    val (view, cursor) = mounted(editor)
    val before = view.componentFor(id("p0"))

    edit(editor)(_.replace(id("p0"), HeadingNode(id("p0"), Vector(id("t0")), HeadingLevel.H2)))

    cursor.collectHtml() should include("<h2")
    cursor.collectHtml() should not include "<p "
    view.componentFor(id("p0")) should not be before
  }

  it should "keep its siblings" in {
    val (editor, _) = open("Erster", "Zweiter")
    val (view, _)   = mounted(editor)
    val untouched   = view.componentFor(id("p1"))

    edit(editor)(_.replace(id("p0"), HeadingNode(id("p0"), Vector(id("t0")), HeadingLevel.H1)))

    view.componentFor(id("p1")) shouldBe untouched
    view.size shouldBe editor.document.size
  }

  it should "rebuild its subtree" in {
    val (editor, _) = open("Hallo")
    val (view, cursor) = mounted(editor)
    val child = view.componentFor(id("t0"))

    edit(editor)(_.replace(id("p0"), HeadingNode(id("p0"), Vector(id("t0")), HeadingLevel.H3)))

    view.componentFor(id("t0")) should not be child
    visible(cursor.collectHtml()) shouldBe "Hallo"
  }
}
