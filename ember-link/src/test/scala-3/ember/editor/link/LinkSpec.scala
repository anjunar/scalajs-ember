package ember.editor.link

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.richtext.StandardMarks.Strong
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Setting and removing links (§8.2, §12). */
final class LinkSpec extends AnyFlatSpec with Matchers {

  private val policy = LinkUrlPolicy.default

  private def target(url: String, title: Option[String] = None): LinkTarget =
    LinkTarget(policy.unsafe(url), title)

  /** One paragraph, `root > p0 > t0`. */
  private final class Fixture(text: String = "Hallo Welt!"):
    val generator: NodeIdGenerator = NodeIdGenerator.sequential("n")

    private val resolved = ExtensionResolver
      .resolve(Vector(RichText(generator), LinkExtension(generator, policy)))
      .getOrElse(throw new AssertionError("Extensions nicht aufloesbar"))

    val root: NodeId = NodeId("root")

    val session: EditorSession = EditorSession
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
        resolved.sessionConfig()
      )
      .getOrElse(throw new AssertionError("Sitzung nicht erzeugbar"))

    def document: Document = session.document

    /** The paragraph as a flat picture: text runs, and links as `[text](url)`. */
    def outline: Vector[String] =
      def walk(id: NodeId): Vector[String] = document.node(id) match
        case Some(run: TextNode) =>
          val marks = if run.marks.isEmpty then "" else run.marks.markIds.map(_ => "*").mkString
          Vector(s"$marks\"${run.text}\"$marks")
        case Some(link: LinkNode) =>
          Vector(s"[${textOf(link.id)}](${link.url.value})")
        case Some(element: ElementNode) => element.children.flatMap(walk)
        case _                          => Vector.empty

      document.childrenOf(NodeId("p0")).flatMap(walk)

    def textOf(id: NodeId): String = document.node(id) match
      case Some(run: TextNode)        => run.text
      case Some(element: ElementNode) => element.children.map(textOf).mkString
      case _                          => ""

    def links: Vector[LinkNode] =
      document.inDocumentOrder.collect { case link: LinkNode => link }.toVector

    def runs: Vector[TextNode] =
      document.inDocumentOrder.collect { case run: TextNode => run }.toVector

    def edit(body: Transaction => Unit): Unit =
      session.update(body) match
        case Right(_)    => ()
        case Left(error) => throw new AssertionError(s"abgewiesen: ${error.render}")

    def selectRange(from: (String, Int), to: (String, Int)): Unit =
      edit(
        _.select(
          RangeSelection(
            Point.textBefore(NodeId(from._1), from._2),
            Point.textBefore(NodeId(to._1), to._2)
          )
        ): Unit
      )

    def caretAt(node: String, offset: Int): Unit =
      edit(_.select(RangeSelection.caret(Point.textBefore(NodeId(node), offset))): Unit)

    def setLink(value: LinkTarget): Boolean =
      session.dispatch(LinkCommands.SetLink, value).map(_.wasHandled).getOrElse(false)

    def removeLink(): Boolean =
      session.dispatch(LinkCommands.RemoveLink).map(_.wasHandled).getOrElse(false)

  private def linked(): Fixture =
    val f = new Fixture()
    f.selectRange(("t0", 6), ("t0", 10))
    f.setLink(target("https://example.com")): Unit
    f

  // ---------------------------------------------------------------------------------------
  // Setting
  // ---------------------------------------------------------------------------------------

  "SetLink" should "wrap the selected range" in {
    val f = linked()

    f.outline shouldBe Vector("\"Hallo \"", "[Welt](https://example.com)", "\"!\"")
  }

  it should "cut the run at both ends" in {
    val f = linked()

    f.runs.map(_.text) shouldBe Vector("Hallo ", "Welt", "!")
  }

  it should "keep the run's id and marks" in {
    // §11: ein Move erhaelt den Knoten. Der verlinkte Lauf ist derselbe, den es vorher gab.
    val f = new Fixture()
    f.selectRange(("t0", 0), ("t0", 11))
    f.session.dispatch(RichText.ToggleMark, Strong): Unit
    val before = f.runs.head.id

    f.selectRange(("t0", 0), ("t0", 11))
    f.setLink(target("https://example.com")) shouldBe true

    f.runs.head.id shouldBe before
    f.runs.head.marks.contains(Strong.markId) shouldBe true
  }

  it should "carry a title" in {
    val f = new Fixture()
    f.selectRange(("t0", 0), ("t0", 5))
    f.setLink(target("https://example.com", Some("Beispiel"))): Unit

    f.links.head.title shouldBe Some("Beispiel")
  }

  it should "work on a backward range" in {
    // §11: die Richtung gehoert dem Benutzer, der Text ist derselbe.
    val f = new Fixture()
    f.selectRange(("t0", 10), ("t0", 6))

    f.setLink(target("https://example.com")) shouldBe true

    f.outline shouldBe Vector("\"Hallo \"", "[Welt](https://example.com)", "\"!\"")
  }

  it should "retarget the link at a caret" in {
    val f = linked()
    f.caretAt(f.links.head.children.head.value, 2)

    f.setLink(target("https://andere.example")) shouldBe true

    f.links should have length 1
    f.links.head.url.value shouldBe "https://andere.example"
  }

  it should "pass at a caret outside any link" in {
    // Manche Editoren fuegen hier die URL als Text ein. Das ist eine Entscheidung fuer eine
    // Oberflaeche, nicht fuer das Modell -- still Text ins Dokument zu schreiben, den niemand
    // getippt hat, waere die schlechtere Voreinstellung.
    val f = new Fixture()
    f.caretAt("t0", 3)

    f.setLink(target("https://example.com")) shouldBe false
    f.links shouldBe empty
  }

  "A link over several blocks" should "become one link per block" in {
    // Ein Link ist inline (§8.2), und ein Knoten hat einen Elternteil. Ueber eine Blockgrenze
    // hinweg gibt es keinen gemeinsamen.
    val f = new Fixture("Erster")
    f.edit(
      _.insert(
        f.root,
        1,
        ParagraphNode(NodeId("p1"), Vector(NodeId("t1"))),
        Vector(TextNode(NodeId("t1"), "Zweiter"))
      ): Unit
    )

    f.selectRange(("t0", 2), ("t1", 3))
    f.setLink(target("https://example.com")) shouldBe true

    f.links should have length 2
    f.links.map(link => f.textOf(link.id)) shouldBe Vector("ster", "Zwe")
  }

  // ---------------------------------------------------------------------------------------
  // Removing
  // ---------------------------------------------------------------------------------------

  "RemoveLink" should "keep the text" in {
    val f = linked()
    f.caretAt(f.links.head.children.head.value, 1)

    f.removeLink() shouldBe true

    f.textOf(NodeId("p0")) shouldBe "Hallo Welt!"
    f.links shouldBe empty
  }

  it should "keep the marks" in {
    // P14, Testliste: "Unlink erhaelt Marks/Text."
    val f = new Fixture()
    f.selectRange(("t0", 6), ("t0", 10))
    f.session.dispatch(RichText.ToggleMark, Strong): Unit
    val bolded = f.runs.find(_.text == "Welt").getOrElse(fail("kein fetter Lauf"))
    f.selectRange((bolded.id.value, 0), (bolded.id.value, 4))
    f.setLink(target("https://example.com")): Unit

    f.caretAt(f.links.head.children.head.value, 1)
    f.removeLink(): Unit

    f.links shouldBe empty
    f.runs.find(_.text == "Welt").map(_.marks.contains(Strong.markId)) shouldBe Some(true)
  }

  it should "let the runs grow back together" in {
    // P12s Normalisierung, ohne dass dieses Modul davon wissen muesste: die Laeufe liegen nach
    // dem Move wieder nebeneinander und haben gleiche Marks.
    val f = linked()
    f.caretAt(f.links.head.children.head.value, 1)

    f.removeLink(): Unit

    f.runs should have length 1
    f.runs.head.text shouldBe "Hallo Welt!"
  }

  it should "pass outside a link" in {
    val f = new Fixture()
    f.caretAt("t0", 3)

    f.removeLink() shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // Normalisation
  // ---------------------------------------------------------------------------------------

  "A link inside a link" should "lose its inner wrapper" in {
    // §8.2: "Ein Link enthaelt keine anderen Links." In HTML nicht ausdrueckbar, in Markdown
    // nicht schreibbar.
    val f = linked()
    val outer = f.links.head

    f.edit(
      _.insert(
        outer.id,
        0,
        LinkNode(NodeId("inner"), Vector(NodeId("it")), target("https://innen.example")),
        Vector(TextNode(NodeId("it"), "Innen"))
      ): Unit
    )

    f.links should have length 1
    f.links.head.id shouldBe outer.id
    f.textOf(outer.id) shouldBe "InnenWelt"
  }

  "An empty link" should "disappear" in {
    val f = linked()
    val link = f.links.head

    f.edit(_.remove(link.children.head): Unit)

    f.document.node(link.id) shouldBe None
  }

  "Two adjacent links to the same place" should "become one" in {
    val f = new Fixture("EinsZwei")
    f.selectRange(("t0", 0), ("t0", 4))
    f.setLink(target("https://example.com")): Unit

    val rest = f.runs.find(_.text == "Zwei").getOrElse(fail("kein Rest"))
    f.selectRange((rest.id.value, 0), (rest.id.value, 4))
    f.setLink(target("https://example.com")): Unit

    f.links should have length 1
    f.textOf(f.links.head.id) shouldBe "EinsZwei"
  }

  it should "stay apart for different targets" in {
    val f = new Fixture("EinsZwei")
    f.selectRange(("t0", 0), ("t0", 4))
    f.setLink(target("https://eins.example")): Unit

    val rest = f.runs.find(_.text == "Zwei").getOrElse(fail("kein Rest"))
    f.selectRange((rest.id.value, 0), (rest.id.value, 4))
    f.setLink(target("https://zwei.example")): Unit

    f.links should have length 2
  }

  "A whitespace title" should "be rejected" in {
    val f = linked()
    val link = f.links.head

    val outcome = f.session.update(
      _.replace(link.id, link.copy(target = link.target.copy(title = Some("   ")))): Unit
    )

    outcome should matchPattern { case Left(_) => }
  }

  // ---------------------------------------------------------------------------------------
  // Headless
  // ---------------------------------------------------------------------------------------

  "The module" should "need no dialog" in {
    // P14, Abnahme: "Linkdialog nicht noetig, headless nutzbar." Alles hier nimmt ein
    // `LinkTarget` und liefert eine Dokumentaenderung; woher das Ziel kommt, ist eine andere
    // Frage.
    val f = new Fixture()
    f.selectRange(("t0", 0), ("t0", 5))

    f.setLink(LinkTarget(policy.unsafe("/impressum"))) shouldBe true

    f.links.head.url.value shouldBe "/impressum"
  }

  it should "leave a valid document after every command" in {
    val f = linked()
    f.caretAt(f.links.head.children.head.value, 1)
    f.setLink(target("https://andere.example")): Unit
    f.removeLink(): Unit

    Document.build(
      f.document.schema,
      f.document.rootId,
      f.document.ids.flatMap(f.document.node).toVector
    ) should matchPattern { case Right(_) => }
  }
}
