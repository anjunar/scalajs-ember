package ember.editor.history

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Undo, Redo und die Gruppierungsregeln aus §14. */
final class HistorySpec extends AnyFlatSpec with Matchers {

  private def fixture(config: HistoryConfig = HistoryConfig.default): HistoryFixture =
    new HistoryFixture(config)

  // ---------------------------------------------------------------------------------------
  // Der Grundvertrag
  // ---------------------------------------------------------------------------------------

  "An empty history" should "have nothing to undo" in {
    val f = fixture()

    f.history.canUndo shouldBe false
    f.history.canRedo shouldBe false
    f.history.undo() shouldBe Right(false)
  }

  "Undo" should "restore document and selection in one commit" in {
    // §14: "Undo/Redo stellt Inhalt und Selection in einem Commit mit neuer Revision wieder
    // her." In *einem* -- sonst saehe ein Beobachter dazwischen einen Stand, den es nie gab.
    val f = fixture()
    var seen = Vector.empty[(String, Option[Int])]

    f.typeChar("X")
    f.session.onCommit(commit =>
      seen = seen :+ ((
        commit.current.document.node(f.text).collect { case run: TextNode => run.text }.getOrElse(""),
        commit.current.selection
          .collect { case range: RangeSelection => range.focus }
          .collect { case Point.Text(_, offset, _) => offset }
      ))
    ): Unit

    f.history.undo() shouldBe Right(true)

    seen shouldBe Vector(("Hallo", Some(0)))
  }

  it should "raise both revisions" in {
    // §9: "Beide steigen auch bei Undo: der wiederhergestellte Inhalt ist ein neuer Stand,
    // kein Zurueckdrehen der Uhr."
    val f = fixture()
    f.typeChar("X")
    val before = f.session.state

    f.history.undo() shouldBe Right(true)

    f.session.state.revision.value should be > before.revision.value
    f.session.state.documentRevision.value should be > before.documentRevision.value
  }

  it should "not record itself" in {
    val f = fixture()
    f.typeChar("X")

    f.history.undo() shouldBe Right(true)

    f.history.canUndo shouldBe false
    f.history.canRedo shouldBe true
  }

  "Redo" should "put the change back" in {
    val f = fixture()
    f.typeChar("X")
    f.history.undo(): Unit

    f.history.redo() shouldBe Right(true)

    f.textOf() shouldBe "XHallo"
    f.caret shouldBe Some(1)
    f.history.canUndo shouldBe true
    f.history.canRedo shouldBe false
  }

  it should "be dropped by a new document change" in {
    // §14: "Eine neue Dokumentaenderung nach Undo loescht Redo."
    val f = fixture()
    f.typeChar("X")
    f.history.undo(): Unit
    f.history.canRedo shouldBe true

    f.typeChar("Y")

    f.history.canRedo shouldBe false
  }

  it should "survive a mere selection change" in {
    // "... ein blosser Selection-Wechsel tut dies nicht."
    val f = fixture()
    f.typeChar("X")
    f.history.undo(): Unit

    f.caretAt(f.text, 3)

    f.history.canRedo shouldBe true
  }

  // ---------------------------------------------------------------------------------------
  // Gruppierung
  // ---------------------------------------------------------------------------------------

  "Continuous typing" should "merge into one step" in {
    val f = fixture()

    f.typeChar("a")
    f.clock.advance(50)
    f.typeChar("b")
    f.clock.advance(50)
    f.typeChar("c")

    f.textOf() shouldBe "abcHallo"
    f.history.state.undo should have length 1

    f.history.undo() shouldBe Right(true)
    f.textOf() shouldBe "Hallo"
  }

  it should "start a new step after the merge window" in {
    // §14: "innerhalb des konfigurierten Zeitfensters". Eine Pause ist eine Absicht.
    val f = fixture()

    f.typeChar("a")
    f.clock.advance(5_000)
    f.typeChar("b")

    f.history.state.undo should have length 2

    f.history.undo(): Unit
    f.textOf() shouldBe "aHallo"
  }

  it should "start a new step after a caret jump" in {
    // "Ein Selection-Sprung bzw. Fokuswechsel beendet die aktuelle Tippgruppe."
    val f = fixture()

    f.typeChar("a")
    f.caretAt(f.text, 4)
    f.typeChar("b")

    f.history.state.undo should have length 2
  }

  it should "start a new step when the marks change" in {
    // "... mit gleicher Mark-Konfiguration". Wer mitten im Wort fett einschaltet, hat zwei
    // Absichten gehabt.
    val f = fixture()
    f.typeChar("a")
    f.clock.advance(10)

    f.edit() { transaction =>
      transaction.spliceText(f.text, 1, 0, "b"): Unit
      transaction.replace(
        f.text,
        TextNode(f.text, "ab" + "Hallo", MarkSet.of(Strong()))
      ): Unit
      transaction.select(RangeSelection.caret(Point.textBefore(f.text, 2))): Unit
    }

    f.history.state.undo should have length 2
  }

  "Backspace and Delete" should "form separate groups" in {
    // §14 nennt es ausdruecklich. Am Ergebnis sind beide ununterscheidbar -- der Unterschied
    // steht im Caret davor, und genau den liest die Klassifikation.
    val f = fixture()
    f.caretAt(f.text, 2)

    f.backspace()
    f.clock.advance(10)
    f.delete()

    f.textOf() shouldBe "Hlo"
    f.history.state.undo should have length 2
  }

  it should "merge repeated backspaces" in {
    val f = fixture()
    f.caretAt(f.text, 5)

    f.backspace()
    f.clock.advance(10)
    f.backspace()

    f.textOf() shouldBe "Hal"
    f.history.state.undo should have length 1

    f.history.undo(): Unit
    f.textOf() shouldBe "Hallo"
    f.caret shouldBe Some(5)
  }

  it should "merge repeated deletes" in {
    val f = fixture()
    f.caretAt(f.text, 0)

    f.delete()
    f.clock.advance(10)
    f.delete()

    f.textOf() shouldBe "llo"
    f.history.state.undo should have length 1
  }

  it should "not merge typing into a deletion" in {
    val f = fixture()
    f.caretAt(f.text, 2)

    f.backspace()
    f.clock.advance(10)
    f.typeChar("X")

    f.history.state.undo should have length 2
  }

  "A structural change" should "always be its own step" in {
    // §14: "Blockwechsel, Paste, Cut, Formatierung und Strukturaenderung bilden Grenzen."
    val f = fixture()

    f.edit()(_.insert(f.block, 1, TextNode(NodeId("t1"), "zwei")): Unit)
    f.clock.advance(10)
    f.edit()(_.insert(f.block, 2, TextNode(NodeId("t2"), "drei")): Unit)

    f.history.state.undo should have length 2

    f.history.undo(): Unit
    f.session.document.childrenOf(f.block) shouldBe Vector(f.text, NodeId("t1"))
  }

  "A range replacement" should "be a boundary" in {
    // Weder von vorn noch von hinten am Caret: das war eine Auswahl, die ersetzt wurde.
    val f = fixture()
    f.caretAt(f.text, 0)

    f.edit()(_.spliceText(f.text, 1, 3, ""): Unit)

    f.history.state.undo.last.kind shouldBe EditKind.Structural
  }

  "A selection-only commit" should "add no step" in {
    val f = fixture()
    f.typeChar("a")

    f.caretAt(f.text, 3)
    f.caretAt(f.text, 1)

    f.history.state.undo should have length 1
  }

  it should "keep the restore selection for the next edit" in {
    // §14: "erhaelt aber die passende Restore-Selection fuer die naechste Bearbeitung."
    val f = fixture()
    f.caretAt(f.text, 5)
    f.typeChar("!")

    f.history.undo() shouldBe Right(true)

    f.caret shouldBe Some(5)
  }

  // ---------------------------------------------------------------------------------------
  // Typisierte Metadaten
  // ---------------------------------------------------------------------------------------

  "HistoryPolicy.Push" should "force its own step" in {
    val f = fixture()
    f.typeChar("a")
    f.clock.advance(10)

    f.typeChar("b", TransactionMeta.user.withHistory(HistoryPolicy.Push))

    f.history.state.undo should have length 2
  }

  "HistoryPolicy.Merge" should "join a step the rules would have separated" in {
    val f = fixture()
    f.typeChar("a")
    f.clock.advance(10_000)

    f.typeChar("b", TransactionMeta.user.withHistory(HistoryPolicy.Merge))

    f.history.state.undo should have length 1
  }

  "HistoryPolicy.Ignore" should "record nothing" in {
    val f = fixture()

    f.typeChar("a", TransactionMeta.user.withHistory(HistoryPolicy.Ignore))

    f.textOf() shouldBe "aHallo"
    f.history.canUndo shouldBe false
  }

  "Origin.Import" should "reset the history" in {
    // §14: "Import setzt History standardmaessig zurueck."
    val f = fixture()
    f.typeChar("a")
    f.clock.advance(10_000)
    f.typeChar("b")
    f.history.state.undo should have length 2

    f.edit(TransactionMeta(Origin.Import))(_.spliceText(f.text, 0, 0, "importiert"): Unit)

    f.history.canUndo shouldBe false
  }

  it should "be a normal change when the caller says so" in {
    // "... fachlich gewuenschte Einfuegung importierter Fragmente ist eine normale Aenderung."
    val f = fixture(HistoryConfig(resetOnImport = false))
    f.typeChar("a")

    f.edit(TransactionMeta(Origin.Import))(_.spliceText(f.text, 0, 0, "x"): Unit)

    f.history.canUndo shouldBe true
  }

  "Origin.History" should "never be recorded" in {
    val f = fixture()

    f.edit(TransactionMeta.history)(_.spliceText(f.text, 0, 0, "x"): Unit)

    f.history.canUndo shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // Commands
  // ---------------------------------------------------------------------------------------

  "The commands" should "undo and redo through a dispatch" in {
    // Der Weg aus §12: `editor.register(Undo) { (tx, _) => history.undo(tx) }`.
    val f = fixture()
    f.typeChar("X")

    f.session.dispatch(HistoryCommands.Undo).map(_.wasHandled) shouldBe Right(true)
    f.textOf() shouldBe "Hallo"

    f.session.dispatch(HistoryCommands.Redo).map(_.wasHandled) shouldBe Right(true)
    f.textOf() shouldBe "XHallo"
  }

  it should "not record the restore they caused" in {
    // Der Dispatch traegt `Origin.User` -- die History erkennt ihre eigene Wiederherstellung am
    // Dokument, das sie gerade eingesetzt hat, nicht an der Herkunft.
    val f = fixture()
    f.typeChar("X")

    f.session.dispatch(HistoryCommands.Undo): Unit

    f.history.canUndo shouldBe false
    f.history.canRedo shouldBe true
  }

  it should "pass when there is nothing to undo" in {
    val f = fixture()

    f.session.dispatch(HistoryCommands.Undo).map(_.wasHandled) shouldBe Right(false)
  }

  // ---------------------------------------------------------------------------------------
  // Ausdrueckliche Gruppen -- der Vertrag, den die CompositionSession benutzen wird
  // ---------------------------------------------------------------------------------------

  "An explicit group" should "collapse into a single step" in {
    // §14: "Alle vorlaeufigen Aenderungen einer CompositionSession verschmelzen zu genau einem
    // Eintrag mit dem Zustand vor Composition-Beginn."
    val f = fixture()

    f.history.beginGroup(Some("composition"))
    f.typeChar("n")
    f.clock.advance(10_000)
    f.typeChar("i")
    f.clock.advance(10_000)
    f.edit()(_.spliceText(f.text, 0, 2, "に"): Unit)
    f.history.endGroup()

    f.history.state.undo should have length 1
    f.history.state.undo.head.label shouldBe Some("composition")

    f.history.undo() shouldBe Right(true)
    f.textOf() shouldBe "Hallo"
  }

  it should "create nothing when the group changed nothing" in {
    // "... abgebrochene Composition ohne Inhaltsaenderung erzeugt keinen Eintrag."
    val f = fixture()

    f.history.beginGroup(Some("abgebrochen"))
    f.caretAt(f.text, 2)
    f.history.endGroup()

    f.history.canUndo shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // Was die History nicht enthaelt
  // ---------------------------------------------------------------------------------------

  "The history" should "live outside the session state" in {
    // §14: "ViewState, DOM, Uploads und rekursiv die History selbst werden nicht in
    // History-Snapshots aufgenommen." Waere sie ein StateField, stuende sie in jedem Snapshot.
    val f = fixture()
    f.typeChar("a")

    f.session.state.fields.fields shouldBe empty
    f.history.state.undo.head.before.document.node(f.text) shouldBe
      Some(TextNode(f.text, "Hallo"))
  }

  it should "leave a restored snapshot untouched by transforms" in {
    // Woran der zweite Erkennungsweg haengt: ein gespeicherter Snapshot ist ein bereits
    // veroeffentlichter und damit normalisierter Stand. Liefe ein Transform darauf noch einmal
    // an, waere das wiederhergestellte Dokument ein anderes Objekt.
    val f = fixture()
    f.typeChar("a")
    val stored = f.history.state.undo.head.before.document

    f.history.undo() shouldBe Right(true)

    f.session.document should be theSameInstanceAs stored
  }

  it should "refuse a second installation" in {
    // Eine History ist veraenderlich und gehoert genau einer Sitzung. Sie zweimal zu
    // installieren waere ein Aufrufvertragsfehler -- der Kern faengt ihn ab und gibt gar keine
    // halb installierte Sitzung heraus (§13).
    val f = fixture()

    val second = ExtensionResolver
      .resolve(Vector(TestNodes, f.history))
      .getOrElse(fail("Extensions nicht aufloesbar"))

    val outcome = EditorSession.create(
      Document.unsafe(second.schema, NodeId("r"), Vector(RootNode(NodeId("r"), Vector.empty))),
      second,
      second.sessionConfig()
    )

    outcome match
      case Left(Vector(error: ExtensionError.InstallationFailed)) =>
        error.message should include("bereits installiert")
      case other => fail(s"unerwartet: $other")
  }
}
