package ember.editor.forms

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.jfx.*
import ember.editor.markdown.*
import jfx.core.component.Runtime
import jfx.core.render.SsrCursor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The field as it arrives without JavaScript (P19b, §16).
  *
  * ==What this suite can answer, and what it cannot==
  *
  * It renders through an [[SsrCursor]] -- the same path a server uses, and the same one
  * `ProjectionSpec` uses in `ember-standard`. That answers the structural half of §16: is the
  * textarea there, named, labelled, holding the source, and '''not''' disabled.
  *
  * It cannot answer whether a browser submits it, whether the value survives an HTML parser, or
  * whether a leading newline makes it through the parser's textarea rule. Those need a real
  * engine and belong to the browser gate.
  */
final class EditorFieldViewSpec extends AnyFlatSpec with Matchers {

  private val root = NodeId("root")

  private val rules: MarkdownSupport =
    MarkdownSupport.of(TestRules.block, TestRules.text, TestRules.loud)

  private def identityOf(id: NodeId, profile: RenderProfile): Vector[HtmlAttribute] =
    profile match
      case RenderProfile.Content => Vector.empty
      case RenderProfile.Editor  => Vector(HtmlAttribute.editor("node", id.value))

  /** A minimal HTML adapter for the local block type -- `ember-standard` is not on this
    * module's classpath, and §6 says it should not be.
    */
  private val views: ViewSupport =
    ViewSupport.semantic(
      HtmlSupport.of(
        new HtmlSemantics[RootNode]:
          val nodeType: NodeType[RootNode] = RootNode
          def shapeOf(node: RootNode, profile: RenderProfile): HtmlShape =
            HtmlShape.Element("article", identityOf(node.id, profile)),
        new HtmlSemantics[Box]:
          val nodeType: NodeType[Box] = Box
          def shapeOf(node: Box, profile: RenderProfile): HtmlShape =
            HtmlShape.Element("p", identityOf(node.id, profile)),
        new HtmlSemantics[TextNode]:
          val nodeType: NodeType[TextNode] = TextNode
          def shapeOf(node: TextNode, profile: RenderProfile): HtmlShape =
            HtmlShape.TextRun("span", node.text, identityOf(node.id, profile), Vector.empty)
      )
    )

  private def field: EditorField =
    EditorFields.markdown("body", rules, NodeIdGenerator.sequential("m"))

  private def open(field: EditorField, text: String): EditorSession =
    val resolved = ExtensionResolver
      .resolve(Vector(TestProfile, FormFieldExtension(field)))
      .getOrElse(fail("Extensions nicht aufloesbar"))

    EditorSession
      .create(
        Document.unsafe(
          resolved.schema,
          root,
          Vector(
            RootNode(root, Vector(NodeId("b"))),
            Box(NodeId("b"), Vector(NodeId("t"))),
            TextNode(NodeId("t"), text, MarkSet.empty)
          )
        ),
        resolved,
        resolved.sessionConfig()
      )
      .getOrElse(fail("Sitzung nicht erzeugbar"))

  /** Renders the field the way a server would. */
  private def render(text: String = "Hallo"): (String, EditorFieldView, EditorFormBinding) =
    val installed = field
    val session   = open(installed, text)
    val binding   = new EditorFormBinding(session, installed)
    val cursor    = new SsrCursor()
    val view      = new EditorFieldView(binding, session, views, "Inhalt")

    Runtime.mount(view, cursor)
    (cursor.collectHtml(), view, binding)

  // ---------------------------------------------------------------------------------------

  "The field" should "render a named textarea" in {
    // §16: "Die Textarea ist ohne JavaScript sichtbar, benannt, fokussierbar und normal
    // submitbar." Der Name ist der Teil, ohne den ein Server nichts bekommt.
    val (html, _, _) = render()

    html should include("<textarea")
    html should include("""name="body"""")
  }

  it should "label it" in {
    val (html, _, _) = render()
    html should include("""aria-label="Inhalt"""")
  }

  it should "carry the source as its value" in {
    val (html, _, _) = render("Ein Absatz")
    html should include("Ein Absatz")
  }

  it should "never disable the textarea" in {
    // §16: "genau ein '''erfolgreiches''' benanntes Formularfeld". Ein disabled Control ist
    // nicht erfolgreich -- das Formular saendete fuer diesen Namen gar nichts. Verborgen ja,
    // abgeschaltet nie.
    val (html, _, _) = render()

    html should not include "disabled"
  }

  it should "show the textarea before activation" in {
    // §16: "Die Textarea ist ohne JavaScript sichtbar, benannt, fokussierbar und normal
    // submitbar." Erst "nach erfolgreicher Aktivierung" wird sie fuer die Rich-Ansicht
    // verborgen -- eine serverseitig gerenderte Seite haette sonst ein Formular, das niemand
    // ausfuellen kann. Ein Browsertest hat genau das gefunden.
    val (html, _, _) = render()

    html should not include "aria-hidden"
    html should include("""data-editor-mode="nojs"""")
  }

  it should "hide it once the rich view has taken over" in {
    val (_, view, _) = render()
    view.activate()

    val (html, _, _) = renderActivated()
    html should include("""aria-hidden="true"""")
    html should include("""data-editor-mode="rich"""")
  }

  it should "show it in the source mode" in {
    // Neu gerendert statt den Zustand inspiziert: was zaehlt, ist was beim Leser ankommt.
    val (html, _, _) = renderInSourceMode()

    html should include("""data-editor-mode="source"""")
    html should not include """aria-hidden="true""""
  }

  it should "give the preview no form name" in {
    // §16: "Der Editor-Host hat keinen konkurrierenden Formularnamen." Sonst gaebe es zwei
    // Werte fuer ein Feld, und der Server bekaeme den zweiten.
    val (html, _, _) = render()

    html.split("name=\"body\"", -1).length - 1 shouldBe 1
    html should include("data-editor-preview")
  }

  it should "render the document as the preview" in {
    val (html, _, _) = render("Ein Absatz")

    html should include("<article")
    html should include("<p")
  }

  it should "mark which mode it is in" in {
    val (html, _, _) = render()
    html should include("""data-editor-mode="nojs"""")
  }

  private def renderActivated(): (String, EditorFieldView, EditorFormBinding) =
    val installed = field
    val session   = open(installed, "Hallo")
    val binding   = new EditorFormBinding(session, installed)
    val cursor    = new SsrCursor()
    val view      = new EditorFieldView(binding, session, views, "Inhalt")

    Runtime.mount(view, cursor)
    view.activate()

    val second = new SsrCursor()
    val again  = new EditorFieldView(binding, session, views, "Inhalt")
    Runtime.mount(again, second)
    again.activate()
    (second.collectHtml(), again, binding)

  private def renderInSourceMode(): (String, EditorFieldView, EditorFormBinding) =
    val installed = field
    val session   = open(installed, "Hallo")
    val binding   = new EditorFormBinding(session, installed)
    val cursor    = new SsrCursor()
    val view      = new EditorFieldView(binding, session, views, "Inhalt")

    Runtime.mount(view, cursor)
    view.enterSource(): Unit

    val second = new SsrCursor()
    val again  = new EditorFieldView(binding, session, views, "Inhalt")
    Runtime.mount(again, second)
    (second.collectHtml(), again, binding)
}
