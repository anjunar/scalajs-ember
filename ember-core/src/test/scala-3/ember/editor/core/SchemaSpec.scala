package ember.editor.core

import ember.editor.foreign.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Registry der Node-Arten und der Typzeuge (P02, Architektur §8.1). */
final class SchemaSpec extends AnyFlatSpec with Matchers {

  "Schema.core" should "bring the root and the text run, and nothing else" in {
    // §6: Paragraph, Heading, Liste, Link und Bild kommen aus ihren Feature-Modulen. Waere hier
    // mehr registriert, zoege eine reine Paragraph-Anwendung Code mit, den sie nie benutzt.
    Schema.core.types.map(_.typeId.value).sorted shouldBe
      Vector("ember.core.root/1", "ember.core.text/1")
  }

  it should "not know a foreign node" in {
    Schema.core.descriptorFor(StickerNode(NodeId("s1"), "*")) shouldBe None
    Schema.core.knows(CaptionNode.typeId) shouldBe false
  }

  "A schema" should "accept foreign descriptors without any core change" in {
    val schema = Schema
      .of(RootNode, TextNode, CaptionNode, StickerNode)
      .getOrElse(fail("gueltiges Schema abgewiesen"))

    schema.byId(CaptionNode.typeId) shouldBe Some(CaptionNode)
    schema.descriptorFor(StickerNode(NodeId("s1"), "*")) shouldBe Some(StickerNode)
  }

  it should "reject two descriptors claiming the same wire name" in {
    object Impostor extends NodeType[TextNode]:
      val typeId: NodeTypeId                          = TextNode.typeId
      def project(node: EditorNode)                   = TextNode.project(node)
      def rekey(node: TextNode, id: NodeId): TextNode = TextNode.rekey(node, id)

    val errors = Schema.of(RootNode, TextNode, Impostor) match
      case Left(errors) => errors
      case Right(_)     => fail("Doppelte NodeTypeId wurde akzeptiert")

    errors shouldBe Vector(SchemaError.DuplicateTypeId(TextNode.typeId))
    errors.head.render shouldBe
      "<root>.ember.core.text/1: Die NodeTypeId `ember.core.text/1` ist mehrfach registriert."
  }

  it should "grow by extension" in {
    val extended = Schema.core
      .extendedWith(CaptionNode)
      .getOrElse(fail("Erweiterung abgewiesen"))

    extended.knows(CaptionNode.typeId) shouldBe true
    Schema.core.knows(CaptionNode.typeId) shouldBe false
  }

  it should "refuse an extension that would duplicate a wire name" in {
    Schema.core.extendedWith(TextNode).isLeft shouldBe true
  }

  "Schema.unsafe" should "throw a contract violation rather than return an invalid schema" in {
    val thrown = intercept[EditorContractViolation](Schema.unsafe(TextNode, TextNode))

    thrown.getMessage should include("mehrfach registriert")
  }

  "A descriptor" should "act as a type witness and refuse foreign nodes" in {
    // §8.1: Jeder typisierte Aufruf laeuft ueber `project`. Deshalb braucht der Kern weder
    // Reflection noch eine oeffentliche Map[String, Any].
    val text    = TextNode(NodeId("t1"), "Hallo")
    val sticker = StickerNode(NodeId("s1"), "*")

    TextNode.project(text) shouldBe Some(text)
    TextNode.project(sticker) shouldBe None
    CaptionNode.project(text) shouldBe None
  }

  it should "expose withChildren exactly for container types" in {
    CaptionNode shouldBe an[ElementNodeType[?]]
    RootNode shouldBe an[ElementNodeType[?]]
    TextNode should not be an[ElementNodeType[?]]
    StickerNode should not be an[ElementNodeType[?]]
  }

  "descriptorFor" should "resolve in registration order when several could match" in {
    // Zwei Deskriptoren fuer denselben Knoten lassen sich beim Aufbau nicht ausschliessen. Die
    // dokumentierte Regel ist: der erste registrierte gewinnt. Das gezielte Ersetzen eingebauter
    // Arten bekommt in P05 einen eigenen, validierten Vertrag.
    object Alternative extends NodeType[TextNode]:
      val typeId: NodeTypeId                          = NodeTypeId("foreign.text/1")
      def project(node: EditorNode)                   = TextNode.project(node)
      def rekey(node: TextNode, id: NodeId): TextNode = TextNode.rekey(node, id)

    val text = TextNode(NodeId("t1"), "Hallo")

    Schema.unsafe(TextNode, Alternative).descriptorFor(text) shouldBe Some(TextNode)
    Schema.unsafe(Alternative, TextNode).descriptorFor(text) shouldBe Some(Alternative)
  }
}
