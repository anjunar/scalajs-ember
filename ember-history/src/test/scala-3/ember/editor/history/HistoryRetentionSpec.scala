package ember.editor.history

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Grenzen, Trimmen und das Freigeben alter Staende (§14).
  *
  * ==Was ein Test hier belegen kann und was nicht==
  *
  * §14 verlangt Benchmarks, die "die Freigabe alter Snapshots nach Trimmen/Dispose" pruefen.
  * Eine Heapmessung ist unter Scala.js nicht zu haben, und eine erfundene waere schlechter als
  * keine. Was pruefbar ist, ist das '''Beobachtbare''': dass eine getrimmte Stufe verschwunden
  * ist, dass die Schaetzung mitfaellt, und dass nach einem Reset kein Snapshot mehr referenziert
  * wird. Ob die Engine den Speicher dann tatsaechlich freigibt, ist ihre Sache -- referenziert
  * wird er von hier aus nicht mehr.
  */
final class HistoryRetentionSpec extends AnyFlatSpec with Matchers {

  /** Erzeugt `count` voneinander getrennte Stufen -- jede ausserhalb des Zeitfensters. */
  private def steps(f: HistoryFixture, count: Int): Unit =
    (1 to count).foreach { index =>
      f.clock.advance(10_000)
      f.typeChar(index.toString.last.toString)
    }

  // ---------------------------------------------------------------------------------------
  // Anzahl
  // ---------------------------------------------------------------------------------------

  "The entry limit" should "drop the oldest steps" in {
    val f = new HistoryFixture(HistoryConfig(HistoryLimits(maxEntries = 3)))

    steps(f, 6)

    f.history.state.undo should have length 3
  }

  it should "leave the newest steps undoable" in {
    val f = new HistoryFixture(HistoryConfig(HistoryLimits(maxEntries = 2)))
    steps(f, 4)
    val text = f.textOf()

    f.history.undo() shouldBe Right(true)
    f.history.undo() shouldBe Right(true)

    f.textOf() should not be text
    f.history.undo() shouldBe Right(false)
  }

  it should "reject a limit below one" in {
    an[IllegalArgumentException] should be thrownBy HistoryLimits(maxEntries = 0)
  }

  // ---------------------------------------------------------------------------------------
  // Byte-Budget
  // ---------------------------------------------------------------------------------------

  "The byte budget" should "drop old steps once it is exceeded" in {
    val f = new HistoryFixture(HistoryConfig(HistoryLimits(maxRetainedBytes = 200)))

    steps(f, 8)

    f.history.state.undo.length should be < 8
    f.history.estimatedBytes should be <= 200
  }

  it should "keep the newest step even when it alone exceeds the budget" in {
    // §14 nennt den Fall und verweist ihn woandershin: "ein riesiger einzelner Import ist
    // separat zu behandeln". Die Alternative waere eine History, die ausgerechnet die letzte
    // Aktion nicht zuruecknehmen kann.
    val f = new HistoryFixture(HistoryConfig(HistoryLimits(maxRetainedBytes = 0)))

    f.typeChar("a")

    f.history.state.undo should have length 1
    f.history.canUndo shouldBe true
  }

  "The estimate" should "count only what an entry adds" in {
    // Die Begruendung steht in `HistoryEntry.estimate`: `before` und `after` teilen fast alle
    // Knoten, und die Summe der Dokumentgroessen waere um Groessenordnungen zu hoch. Ein
    // Zeichen mehr kostet deshalb ungefaehr ein Zeichen, nicht ein Dokument.
    val f = new HistoryFixture()
    f.edit()(_.spliceText(f.text, 0, 0, "x" * 1000): Unit)
    val small = f.history.state.undo.head.estimatedBytes

    val g = new HistoryFixture()
    g.edit()(_.spliceText(g.text, 0, 0, "x" * 100_000): Unit)
    val large = g.history.state.undo.head.estimatedBytes

    small should be < 5_000
    large should be > 100_000
  }

  it should "grow with a merged group" in {
    val f = new HistoryFixture()
    f.typeChar("a")
    val afterFirst = f.history.state.undo.head.estimatedBytes

    f.clock.advance(10)
    f.typeChar("b")

    f.history.state.undo should have length 1
    f.history.state.undo.head.estimatedBytes should be >= afterFirst
  }

  // ---------------------------------------------------------------------------------------
  // Freigeben
  // ---------------------------------------------------------------------------------------

  "A reset" should "let go of every snapshot" in {
    val f = new HistoryFixture()
    steps(f, 5)
    f.history.estimatedBytes should be > 0

    f.history.reset()

    f.history.state shouldBe HistoryState.empty
    f.history.estimatedBytes shouldBe 0
    f.history.canUndo shouldBe false
    f.history.canRedo shouldBe false
  }

  it should "not disturb the session" in {
    val f = new HistoryFixture()
    steps(f, 3)
    val text = f.textOf()

    f.history.reset()

    f.textOf() shouldBe text
    f.session.isDisposed shouldBe false
  }

  "A trimmed step" should "be gone from both stacks" in {
    val f = new HistoryFixture(HistoryConfig(HistoryLimits(maxEntries = 2)))
    steps(f, 3)
    val oldest = f.history.state.undo.head

    steps(f, 1)

    f.history.state.undo should not contain oldest
    f.history.state.redo shouldBe empty
  }

  "Undone steps" should "still count against the budget" in {
    // Ein Eintrag auf dem Redo-Stapel haelt seine Snapshots genauso fest wie einer auf dem
    // Undo-Stapel. Ihn beim Zaehlen zu uebergehen hiesse, das Budget genau dann zu verfehlen,
    // wenn jemand viel rueckgaengig gemacht hat.
    val f = new HistoryFixture()
    steps(f, 3)
    val total = f.history.estimatedBytes

    f.history.undo(): Unit

    f.history.state.redo should have length 1
    f.history.estimatedBytes shouldBe total
  }
}
