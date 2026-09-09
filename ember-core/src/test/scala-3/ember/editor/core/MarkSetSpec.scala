package ember.editor.core

import ember.editor.foreign.NamedMark
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Normalisierung der Markierungsmenge (P02, Architektur §8.2).
  *
  * Eigene Suite statt eines Anhangs an `DocumentSpec`: `MarkSet` ist der Vertrag, an dem in P12 das
  * Wiederzusammenwachsen getrennter Textlaeufe haengt. Diese Zusicherungen sollen dort einzeln
  * auffindbar sein, wenn sie brechen.
  */
final class MarkSetSpec extends AnyFlatSpec with Matchers {

  private val strong   = NamedMark("strong")
  private val emphasis = NamedMark("emphasis")
  private val code     = NamedMark("code")

  "An empty MarkSet" should "carry nothing" in {
    MarkSet.empty.isEmpty shouldBe true
    MarkSet.empty.size shouldBe 0
    MarkSet.empty.marks shouldBe empty
  }

  "MarkSet equality" should "ignore the order marks were added in" in {
    // Das ist die Zusicherung, an der §8.2 haengt: benachbarte Textlaeufe wachsen genau dann
    // wieder zusammen, wenn ihre normalisierten Marks gleich sind. Waere die Gleichheit
    // ordnungsabhaengig, wuerde wiederholtes Formatieren und Entformatieren den Baum
    // fragmentieren, ohne dass irgendein Test das bemerkt.
    MarkSet.of(strong, emphasis) shouldBe MarkSet.of(emphasis, strong)
    MarkSet.of(strong, emphasis).hashCode() shouldBe MarkSet.of(emphasis, strong).hashCode()
  }

  it should "distinguish different contents" in {
    MarkSet.of(strong) should not be MarkSet.of(emphasis)
    MarkSet.of(strong) should not be MarkSet.of(strong, emphasis)
  }

  "A MarkSet" should "order its marks by mark id, not by insertion" in {
    // Deterministische Ordnung, damit JSON- und Markdown-Export bei gleichem Dokument
    // byteweise gleich sind. Ohne das sind Roundtrip-Fixtures wertlos.
    MarkSet.of(strong, code, emphasis).markIds.map(_.value) shouldBe
      Vector("code", "emphasis", "strong")
  }

  it should "hold at most one mark per mark id" in {
    val replaced = MarkSet.of(NamedMark("strong")) + strong

    replaced.size shouldBe 1
    replaced.get(MarkId("strong")) shouldBe Some(strong)
  }

  it should "remove by mark id and stay unchanged for absent ones" in {
    val set = MarkSet.of(strong, emphasis)

    (set - MarkId("strong")).markIds.map(_.value) shouldBe Vector("emphasis")
    (set - MarkId("underline")) shouldBe set
  }

  it should "let the later side win on union" in {
    val left  = MarkSet.of(strong, emphasis)
    val right = MarkSet.of(code)

    left.union(right).markIds.map(_.value) shouldBe Vector("code", "emphasis", "strong")
    MarkSet.empty.union(left) shouldBe left
    left.union(MarkSet.empty) shouldBe left
  }

  it should "answer membership by mark id" in {
    val set = MarkSet.of(strong)

    set.contains(MarkId("strong")) shouldBe true
    set.contains(MarkId("emphasis")) shouldBe false
  }

  "The core" should "define no concrete marks of its own" in {
    // §8.2 und §6: Strong, Emphasis, Underline, Strike und InlineCode gehoeren ins
    // Rich-Text-Modul. Der Kern kennt nur den offenen Vertrag -- die Marks in dieser Suite
    // stammen alle aus dem Fremdmodul-Fixture.
    strong shouldBe a[TextMark]
    MarkSet.of(strong).marks.head.markId.value shouldBe "strong"
  }
}
