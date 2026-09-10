package ember.editor.image

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The image node and how it gets into a document (§20). */
final class ImageNodeSpec extends AnyFlatSpec with Matchers {

  private val policy = MediaUrlPolicy.default

  private def source(url: String = "https://example.com/bild.png"): MediaReference =
    MediaReference(policy.unsafe(url))

  /** `root > p0 > t0`, with images installed. A local block type stands in for a paragraph --
    * §6 puts `image` on the core alone, so the rich-text profile is not available here.
    */
  private final class Fixture(text: String = "Hallo Welt"):
    val generator: NodeIdGenerator = NodeIdGenerator.sequential("n")

    private val resolved = ExtensionResolver
      .resolve(Vector(TestBlocks, ImageExtension(generator, policy)))
      .getOrElse(throw new AssertionError("Extensions nicht aufloesbar"))

    val root: NodeId = NodeId("root")

    val session: EditorSession = EditorSession
      .create(
        Document.unsafe(
          resolved.schema,
          root,
          Vector(
            RootNode(root, Vector(NodeId("p0"))),
            BlockNode(NodeId("p0"), Vector(NodeId("t0"))),
            TextNode(NodeId("t0"), text)
          )
        ),
        resolved,
        resolved.sessionConfig()
      )
      .getOrElse(throw new AssertionError("Sitzung nicht erzeugbar"))

    def document: Document = session.document

    /** The block's children as `"text"` and `img(alt)`. */
    def outline: Vector[String] =
      document.childrenOf(NodeId("p0")).flatMap { id =>
        document.node(id) match
          case Some(run: TextNode)    => Vector(s""""${run.text}"""")
          case Some(image: ImageNode) => Vector(s"img(${image.alt})")
          case _                      => Vector.empty
      }

    def images: Vector[ImageNode] =
      document.inDocumentOrder.collect { case image: ImageNode => image }.toVector

    def edit(body: Transaction => Unit): Unit =
      session.update(body) match
        case Right(_)    => ()
        case Left(error) => throw new AssertionError(s"abgewiesen: ${error.render}")

    def caretAt(node: String, offset: Int): Unit =
      edit(_.select(RangeSelection.caret(Point.textBefore(NodeId(node), offset))): Unit)

    def selectNode(node: String): Unit =
      edit(_.select(NodeSelection(Set(NodeId(node)))): Unit)

    def insert(image: ImageNode): Boolean =
      session.dispatch(ImageCommands.InsertImage, image).map(_.wasHandled).getOrElse(false)

    def update(change: ImageNode => ImageNode): Boolean =
      session.dispatch(ImageCommands.UpdateImage, change).map(_.wasHandled).getOrElse(false)

  private def picture(alt: String = "Ein Bild"): ImageNode =
    ImageNode(NodeId("bild"), source(), alt)

  // ---------------------------------------------------------------------------------------
  // Pixel measurements
  // ---------------------------------------------------------------------------------------

  "PositivePixels" should "refuse what is not a measurement" in {
    // §20: "validierte positive Pixelmasse". Eine Breite von null ist kein kleines Bild.
    PositivePixels.parse(640) should not be empty
    PositivePixels.parse(0) shouldBe None
    PositivePixels.parse(-1) shouldBe None
  }

  it should "refuse an absurd size" in {
    // Eine angegebene Groesse soll Layoutplatz reservieren (§20). Eine Zahl in Millionen
    // reserviert nichts und sagt nur, dass weiter oben etwas schiefging.
    PositivePixels.parse(PositivePixels.max) should not be empty
    PositivePixels.parse(PositivePixels.max + 1) shouldBe None
  }

  it should "throw on a literal that is wrong" in {
    an[EditorContractViolation] should be thrownBy PositivePixels(0)
  }

  // ---------------------------------------------------------------------------------------
  // The node
  // ---------------------------------------------------------------------------------------

  "An image" should "hold no file data" in {
    // P16, Abnahme: "Keine Dateidaten oder Object-URL im Document." Der Typ macht es unmoeglich
    // -- eine `MediaReference` ist eine Adresse und eine optionale Kennung, sonst nichts.
    val image = picture()

    image.source.src.value shouldBe "https://example.com/bild.png"
    image.source.mediaId shouldBe None
  }

  it should "carry the application's media id when there is one" in {
    val reference = MediaReference(policy.unsafe("/medien/4711.png"), Some(MediaId("4711")))

    reference.mediaId.map(_.value) shouldBe Some("4711")
  }

  it should "allow an empty alt text" in {
    // §20: "ein dekoratives Bild verwendet ausdruecklich leeren Alt-Text, nicht automatisch den
    // Dateinamen." Der leere Alt-Text *bedeutet* etwas.
    val decorative = picture(alt = "")

    decorative.alt shouldBe ""
  }

  it should "reject a whitespace title" in {
    val f = new Fixture()
    f.caretAt("t0", 5)
    f.insert(picture()): Unit

    val outcome = f.session.update(
      _.replace(NodeId("bild"), f.images.head.copy(title = Some("   "))): Unit
    )

    outcome should matchPattern { case Left(_) => }
  }

  // ---------------------------------------------------------------------------------------
  // Inserting
  // ---------------------------------------------------------------------------------------

  "InsertImage" should "split the run and sit between the halves" in {
    // Ein Inline-Atom gehoert zwischen Zeichen, nicht neben einen Absatz (§20).
    val f = new Fixture()
    f.caretAt("t0", 5)

    f.insert(picture()) shouldBe true

    f.outline shouldBe Vector("\"Hallo\"", "img(Ein Bild)", "\" Welt\"")
  }

  it should "go before the run at its start" in {
    val f = new Fixture()
    f.caretAt("t0", 0)

    f.insert(picture()): Unit

    f.outline shouldBe Vector("img(Ein Bild)", "\"Hallo Welt\"")
  }

  it should "go after the run at its end" in {
    val f = new Fixture()
    f.caretAt("t0", 10)

    f.insert(picture()): Unit

    f.outline shouldBe Vector("\"Hallo Welt\"", "img(Ein Bild)")
  }

  it should "pick a free id when the given one is taken" in {
    val f = new Fixture()
    f.caretAt("t0", 5)
    f.insert(picture()): Unit

    f.caretAt("t0", 0)
    f.insert(picture(alt = "Zweites")) shouldBe true

    f.images should have length 2
    f.images.map(_.id).distinct should have length 2
  }

  it should "pass without a caret" in {
    val f = new Fixture()

    f.insert(picture()) shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // Updating
  // ---------------------------------------------------------------------------------------

  "UpdateImage" should "change the alt text of the selected image" in {
    val f = new Fixture()
    f.caretAt("t0", 5)
    f.insert(picture()): Unit
    f.selectNode("bild")

    f.update(_.copy(alt = "Besser beschrieben")) shouldBe true

    f.images.head.alt shouldBe "Besser beschrieben"
  }

  it should "keep the node id" in {
    // `Replace` erhaelt Identitaet (§10) -- ein Bookmark auf das Bild ueberlebt eine Aenderung
    // seines Alt-Textes, und das ist die haeufigste.
    val f = new Fixture()
    f.caretAt("t0", 5)
    f.insert(picture()): Unit
    f.selectNode("bild")

    f.update(_.copy(alt = "Anders", id = NodeId("versuch"))): Unit

    f.images.head.id shouldBe NodeId("bild")
    f.document.node(NodeId("versuch")) shouldBe None
  }

  it should "set a size" in {
    val f = new Fixture()
    f.caretAt("t0", 5)
    f.insert(picture()): Unit
    f.selectNode("bild")

    f.update(_.copy(width = PositivePixels.parse(640), height = PositivePixels.parse(480))): Unit

    f.images.head.width.map(_.value) shouldBe Some(640)
    f.images.head.height.map(_.value) shouldBe Some(480)
  }

  it should "pass when nothing is selected" in {
    val f = new Fixture()
    f.caretAt("t0", 5)
    f.insert(picture()): Unit
    f.caretAt("t0", 2)

    f.update(_.copy(alt = "Anders")) shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // The document stays valid
  // ---------------------------------------------------------------------------------------

  "A document with images" should "stay valid" in {
    val f = new Fixture()
    f.caretAt("t0", 5)
    f.insert(picture()): Unit
    f.selectNode("bild")
    f.update(_.copy(alt = "", title = Some("Titel"))): Unit

    Document.build(
      f.document.schema,
      f.document.rootId,
      f.document.ids.flatMap(f.document.node).toVector
    ) should matchPattern { case Right(_) => }
  }
}

/** A block type standing in for a paragraph.
  *
  * `ember-image` depends on the core alone (§6), so the rich-text profile is not on its
  * classpath. Using a local type is not a workaround -- it is the check that an image needs
  * nothing from that profile.
  */
private final case class BlockNode(id: NodeId, children: Vector[NodeId]) extends ElementNode

private object BlockNode extends ElementNodeType[BlockNode]:
  val typeId: NodeTypeId = NodeTypeId("test.block/1")

  def project(node: EditorNode): Option[BlockNode] = node match
    case block: BlockNode => Some(block)
    case _                => None

  def rekey(node: BlockNode, id: NodeId): BlockNode        = node.copy(id = id)
  def withChildren(node: BlockNode, children: Vector[NodeId]): BlockNode =
    node.copy(children = children)

private object TestBlocks extends Extension:
  val id: ExtensionId = ExtensionId("test.blocks")

  override def contribute: ExtensionContributions =
    ExtensionContributions(nodeTypes = Vector(RootNode, TextNode, BlockNode))
