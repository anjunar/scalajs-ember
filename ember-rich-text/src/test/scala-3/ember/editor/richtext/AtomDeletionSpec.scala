package ember.editor.richtext

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** An atom in the flow, and the two keys that have to reach it (§22).
  *
  * ==Why this suite exists==
  *
  * §22: "Atomare Medien sind per Tastatur erreichbar und loeschbar." They were not. A caret
  * resolves to a position in a '''text run''' ([[TextEditing.textPositionOf]]), and an atom is not
  * one -- so a Backspace behind a picture reached past it and took the last character of the run in
  * front instead. The picture stayed; something else vanished.
  *
  * Found by using the demo, which is what a demo is for.
  *
  * ==The local atom==
  *
  * `ember-rich-text` has no atom type of its own, and §6 keeps `image` out of it. A marker node
  * standing in for one is enough: the rule is about [[AtomNode]] and not about pictures, and a test
  * that needed the image module would be testing the wrong level.
  */
final class AtomDeletionSpec extends AnyFlatSpec with Matchers {

  /** A leaf whose inside is not a text area. Stands in for a picture. */
  private final case class MarkerNode(id: NodeId) extends AtomNode

  private object MarkerNode extends NodeType[MarkerNode]:
    val typeId: NodeTypeId = NodeTypeId("ember.test.marker/1")

    def project(node: EditorNode): Option[MarkerNode] = node match
      case marker: MarkerNode => Some(marker)
      case _                  => None

    def rekey(node: MarkerNode, id: NodeId): MarkerNode = node.copy(id = id)

  private object MarkerExtension extends Extension:
    val id: ExtensionId = ExtensionId("ember.test.marker")

    override def contribute: ExtensionContributions =
      ExtensionContributions(nodeTypes = Vector(MarkerNode))

  /** `root > p0 > [t0 "Hallo ", a0, t1 " Welt"]`. */
  private final class Fixture:
    val generator: NodeIdGenerator = NodeIdGenerator.sequential("n")

    private val resolved = ExtensionResolver
      .resolve(Vector(RichText(generator), MarkerExtension))
      .getOrElse(throw new AssertionError("Extensions nicht aufloesbar"))

    val session: EditorSession = EditorSession
      .create(
        Document.unsafe(
          resolved.schema,
          NodeId("root"),
          Vector(
            RootNode(NodeId("root"), Vector(NodeId("p0"))),
            ParagraphNode(NodeId("p0"), Vector(NodeId("t0"), NodeId("a0"), NodeId("t1"))),
            TextNode(NodeId("t0"), "Hallo "),
            MarkerNode(NodeId("a0")),
            TextNode(NodeId("t1"), " Welt")
          )
        ),
        resolved,
        resolved.sessionConfig()
      )
      .getOrElse(throw new AssertionError("Sitzung nicht erzeugbar"))

    def document: Document = session.document

    /** The block's children as `"text"` and `atom`. */
    def outline: Vector[String] =
      document.childrenOf(NodeId("p0")).map { id =>
        document.node(id) match
          case Some(run: TextNode) => s""""${run.text}""""
          case Some(_: MarkerNode) => "atom"
          case _                   => "?"
      }

    def hasAtom: Boolean = document.node(NodeId("a0")).isDefined

    def caretAtText(node: String, offset: Int): Unit =
      apply(_.select(RangeSelection.caret(Point.textBefore(NodeId(node), offset))): Unit)

    def caretAtChild(index: Int): Unit =
      apply(_.select(RangeSelection.caret(Point.childrenBefore(NodeId("p0"), index))): Unit)

    def selectRange(anchor: Point, focus: Point): Unit =
      apply(_.select(RangeSelection(anchor, focus)): Unit)

    def selectAtom(): Unit =
      apply(_.select(NodeSelection(Set(NodeId("a0")))): Unit)

    def backspace(): Boolean = dispatch(RichText.DeleteBackward)

    def delete(): Boolean = dispatch(RichText.DeleteForward)

    def caret: Option[(String, Int)] =
      session.selection
        .collect { case range: RangeSelection if range.isCollapsed => range.focus }
        .collect { case Point.Text(node, offset, _) => (node.value, offset) }

    private def dispatch(command: EditorCommand[Unit]): Boolean =
      session.dispatch(command).map(_.wasHandled).getOrElse(false)

    private def apply(body: Transaction => Unit): Unit =
      session.update(body) match
        case Right(_)    => ()
        case Left(error) => throw new AssertionError(s"abgewiesen: ${error.render}")

  // ---------------------------------------------------------------------------------------
  // Rueckwaerts
  // ---------------------------------------------------------------------------------------

  "Backspace" should "remove an atom the caret stands behind" in {
    val fixture = new Fixture
    fixture.caretAtText("t1", 0)

    fixture.backspace() shouldBe true

    fixture.hasAtom shouldBe false
    // The two runs carried the same marks, so P12's normalisation grows them back together.
    fixture.outline shouldBe Vector(""""Hallo  Welt"""")
  }

  it should "take the character, not the atom, from the middle of a run" in {
    // The regression this whole change could have caused: an atom somewhere in the block is not
    // a reason to stop deleting text.
    val fixture = new Fixture
    fixture.caretAtText("t1", 3)

    fixture.backspace() shouldBe true

    fixture.hasAtom shouldBe true
    fixture.outline shouldBe Vector(""""Hallo """", "atom", """" Wlt"""")
  }

  it should "work from the child boundary as well as from the text" in {
    // Both shapes of caret occur. A click on the boundary gives the first, arrow navigation the
    // second -- and a rule that only knew one of them would work in half the cases.
    val fixture = new Fixture
    fixture.caretAtChild(2)

    fixture.backspace() shouldBe true

    fixture.hasAtom shouldBe false
  }

  it should "leave the caret where the atom was" in {
    // After deleting a picture between two words the caret belongs between those words, not at
    // some child boundary nobody can see.
    val fixture = new Fixture
    fixture.caretAtText("t1", 0)

    fixture.backspace() shouldBe true

    fixture.caret shouldBe Some(("t0", 6))
  }

  it should "still delete a character where there is no atom next to it" in {
    val fixture = new Fixture
    fixture.caretAtText("t0", 6)

    fixture.backspace() shouldBe true

    fixture.hasAtom shouldBe true
    fixture.outline shouldBe Vector(""""Hallo"""", "atom", """" Welt"""")
  }

  // ---------------------------------------------------------------------------------------
  // Vorwaerts
  // ---------------------------------------------------------------------------------------

  "Delete" should "remove an atom the caret stands in front of" in {
    val fixture = new Fixture
    fixture.caretAtText("t0", 6)

    fixture.delete() shouldBe true

    fixture.hasAtom shouldBe false
    fixture.outline shouldBe Vector(""""Hallo  Welt"""")
  }

  it should "work from the child boundary too" in {
    val fixture = new Fixture
    fixture.caretAtChild(1)

    fixture.delete() shouldBe true

    fixture.hasAtom shouldBe false
  }

  it should "still delete a character where there is no atom next to it" in {
    val fixture = new Fixture
    fixture.caretAtText("t1", 0)

    fixture.delete() shouldBe true

    fixture.hasAtom shouldBe true
    fixture.outline shouldBe Vector(""""Hallo """", "atom", """"Welt"""")
  }

  // ---------------------------------------------------------------------------------------
  // Bereiche innerhalb eines Blocks
  // ---------------------------------------------------------------------------------------

  "A range covering an atom" should "take the atom and nothing else" in {
    // What a click on a picture produces: the browser selects the image, and the port maps that
    // to a range from the boundary before it to the boundary after it.
    val fixture = new Fixture
    fixture.selectRange(
      Point.childrenBefore(NodeId("p0"), 1),
      Point.childrenBefore(NodeId("p0"), 2)
    )

    fixture.backspace() shouldBe true

    fixture.hasAtom shouldBe false
    fixture.outline shouldBe Vector(""""Hallo  Welt"""")
  }

  "A range across two runs of one block" should "leave everything outside it standing" in {
    // The bug the atom made visible, and it was never about atoms: `deleteAcross` removed every
    // sibling after the start node and every sibling before the end node -- which inside a single
    // block means "everything to the end of it". Text far behind the selection disappeared.
    val fixture = new Fixture
    fixture.selectRange(
      Point.textBefore(NodeId("t0"), 3),
      Point.textBefore(NodeId("t1"), 3)
    )

    fixture.backspace() shouldBe true

    fixture.hasAtom shouldBe false
    // "Hallo " behaelt "Hal", " Welt" verliert " We" -- zusammengewachsen "Hallt".
    fixture.outline shouldBe Vector(""""Hallt"""")
  }

  it should "keep a run that lies beyond the end" in {
    val fixture = new Fixture
    fixture.selectRange(
      Point.textBefore(NodeId("t0"), 2),
      Point.childrenBefore(NodeId("p0"), 2)
    )

    fixture.backspace() shouldBe true

    fixture.outline shouldBe Vector(""""Ha Welt"""")
  }

  // ---------------------------------------------------------------------------------------
  // Knotenauswahl (§11)
  // ---------------------------------------------------------------------------------------

  "A selected atom" should "go on Backspace" in {
    // §11 keeps `NodeSelection` as its own kind -- several selected pictures are not a text
    // range. Before this, every delete path returned early on one: `currentRange` saw no range,
    // and Backspace on a selected picture did nothing at all.
    val fixture = new Fixture
    fixture.selectAtom()

    fixture.backspace() shouldBe true

    fixture.hasAtom shouldBe false
  }

  it should "go on Delete as well" in {
    val fixture = new Fixture
    fixture.selectAtom()

    fixture.delete() shouldBe true

    fixture.hasAtom shouldBe false
  }

  it should "leave a caret behind, not an empty selection" in {
    val fixture = new Fixture
    fixture.selectAtom()

    fixture.backspace() shouldBe true

    fixture.session.selection should not be empty
  }
}
