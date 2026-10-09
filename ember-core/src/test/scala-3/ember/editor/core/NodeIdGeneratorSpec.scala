package ember.editor.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Vergabe neuer IDs (P02, Architektur §8.3). */
final class NodeIdGeneratorSpec extends AnyFlatSpec with Matchers {

  private def free: NodeId => Boolean = _ => false

  "The sequential generator" should "produce the same ids on every run" in {
    // §8.3 verlangt deterministische Testgeneratoren. Ohne sie waeren Fixtures fuer JSON- und
    // Markdown-Roundtrips nicht vergleichbar.
    NodeIdGenerator.sequential().nextBatch(3, free).map(_.value) shouldBe
      Vector("n1", "n2", "n3")
    NodeIdGenerator.sequential().nextBatch(3, free).map(_.value) shouldBe
      Vector("n1", "n2", "n3")
  }

  it should "keep its counter per instance, never globally" in {
    // Ein globaler Zaehler wuerde unabhaengige Dokumente verknuepfen und Tests von der
    // Ausfuehrungsreihenfolge abhaengig machen.
    val first  = NodeIdGenerator.sequential()
    val second = NodeIdGenerator.sequential()

    first.next(free).value shouldBe "n1"
    second.next(free).value shouldBe "n1"
    first.next(free).value shouldBe "n2"
  }

  it should "skip ids that are already taken" in {
    val taken     = Set("n1", "n2")
    val generator = NodeIdGenerator.sequential()

    generator.next(id => taken.contains(id.value)).value shouldBe "n3"
  }

  it should "honour a custom prefix" in {
    NodeIdGenerator.sequential("para-").next(free).value shouldBe "para-1"
  }

  "The random generator" should "be reproducible when seeded" in {
    val one = NodeIdGenerator.random(seed = Some(42L)).nextBatch(5, free)
    val two = NodeIdGenerator.random(seed = Some(42L)).nextBatch(5, free)

    one shouldBe two
    one.distinct.size shouldBe 5
  }

  it should "differ between seeds" in {
    NodeIdGenerator.random(seed = Some(1L)).next(free) should not be
      NodeIdGenerator.random(seed = Some(2L)).next(free)
  }

  it should "produce ids of the requested shape" in {
    val id = NodeIdGenerator.random(prefix = "img", length = 6, seed = Some(7L)).next(free)

    id.value should startWith("img")
    id.value.length shouldBe 9
  }

  "nextBatch" should "not repeat ids it has just issued" in {
    // Wer mehrere IDs erzeugt, bevor er die Knoten einfuegt, kann `isTaken` noch nicht fragen --
    // das Dokument weiss von ihnen nichts. nextBatch zaehlt die ausgegebenen selbst mit.
    val issued = NodeIdGenerator.random(length = 1, seed = Some(3L)).nextBatch(20, free)

    issued.distinct.size shouldBe 20
  }

  it should "reject a negative count" in {
    intercept[EditorContractViolation](NodeIdGenerator.sequential().nextBatch(-1, free))
  }

  "nextFor" should "avoid the ids a document already uses" in {
    val document = Document
      .build(
        Schema.core,
        NodeId("n1"),
        Vector(RootNode(NodeId("n1"), Vector(NodeId("n2"))), TextNode(NodeId("n2"), "Hallo"))
      )
      .getOrElse(fail("gueltiges Dokument abgewiesen"))

    NodeIdGenerator.sequential().nextFor(document).value shouldBe "n3"
  }

  "A generator" should "fail loudly when its namespace is exhausted" in {
    // Ohne Grenze wuerde ein zu kleiner Zufallsraum nicht auffallen, sondern die Anwendung
    // einfrieren -- ein Fehlerbild, das sich kaum zuordnen laesst.
    val thrown = intercept[EditorContractViolation] {
      NodeIdGenerator.sequential().next(_ => true)
    }

    thrown.getMessage should include("keine freie NodeId")
  }
}
