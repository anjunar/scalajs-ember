package ember.editor.core

import ember.editor.foreign.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Typisierte Sitzungsfelder und ihre Reducer (P04, Architektur §9). */
final class StateFieldSpec extends AnyFlatSpec with Matchers {

  private val schema = Schema.unsafe(RootNode, TextNode, CaptionNode)

  private def id(value: String): NodeId = NodeId(value)

  private val root = id("root")

  private val document: Document = Document.unsafe(
    schema,
    root,
    Vector(RootNode(root, Vector(id("t1"))), TextNode(id("t1"), "Hallo"))
  )

  /** Ein Feld, das Dokumentaenderungen ueberdauert. */
  private object Draft extends StateField[String]:
    val name    = "draft"
    val initial = ""

  /** Ein Feld, das nach jeder Dokumentaenderung zurueckfaellt.
    *
    * Stellvertreter fuer `TypingMarks` (P12): ein am Caret gemerkter Formatierungswunsch, der nach
    * einer Aenderung nicht mehr stimmt.
    */
  private object Sticky extends StateField[Int]:
    val name                      = "sticky"
    val initial                   = 0
    override val onDocumentChange = DocumentChangePolicy.Reset

  /** Ein abgeleitetes Feld: der Reducer rechnet es aus dem Kandidaten aus.
    *
    * Stellvertreter fuer `EncodedFieldValue` (P19b).
    */
  private object Length extends StateField[Int]:
    val name    = "length"
    val initial = 0
    override def reduce(current: Int, candidate: CommitCandidate): Either[EditorError, Int] =
      Right(candidate.document.nodes.collect { case text: TextNode => text.text.length }.sum)

  /** Ein Feld, das ablehnen kann. */
  private object Bounded extends StateField[Int]:
    val name    = "bounded"
    val initial = 0
    override def reduce(current: Int, candidate: CommitCandidate): Either[EditorError, Int] =
      val total = candidate.document.nodes.collect { case text: TextNode => text.text.length }.sum
      if total > 8 then
        Left(
          Violation
            .NodeRejected(candidate.document.rootId, s"$total ist zu lang.", DiagnosticPath.Root)
        )
      else Right(total)

  private def session(fields: StateField[?]*): EditorSession =
    EditorSession.create(document, SessionConfig(fields = fields.toVector))

  // ---------------------------------------------------------------------------------------

  "A state field" should "start at its initial value" in {
    session(Draft, Sticky).state.fields(Draft) shouldBe ""
    session(Draft, Sticky).state.fields(Sticky) shouldBe 0
  }

  it should "report its initial value even when the session does not carry it" in {
    // Ein Feld eines nicht installierten Moduls liest sich als `initial`, statt zu werfen.
    session().state.fields(Draft) shouldBe ""
  }

  it should "be readable and writable through the transaction" in {
    val editor = session(Draft)

    editor.update { tx =>
      tx.field(Draft) shouldBe ""
      tx.setField(Draft, "unbestaetigt")
    }

    editor.state.fields(Draft) shouldBe "unbestaetigt"
  }

  it should "advance the session revision but not the document one" in {
    val editor = session(Draft)
    val commit = editor.update(_.setField(Draft, "x")).getOrElse(fail("abgewiesen"))

    commit.isNoOp shouldBe false
    commit.documentChanged shouldBe false
    commit.current.revision.value shouldBe 1
    commit.current.documentRevision.value shouldBe 0
  }

  // ---------------------------------------------------------------------------------------
  // Verhalten bei Dokumentaenderung
  // ---------------------------------------------------------------------------------------

  "A Keep field" should "survive a document change" in {
    val editor = session(Draft)

    editor.update(_.setField(Draft, "bleibt"))
    editor.update(_.spliceText(id("t1"), 0, 0, "X"))

    editor.state.fields(Draft) shouldBe "bleibt"
  }

  "A Reset field" should "fall back once the document changes" in {
    val editor = session(Sticky)

    editor.update(_.setField(Sticky, 42))
    editor.state.fields(Sticky) shouldBe 42

    editor.update(_.spliceText(id("t1"), 0, 0, "X"))
    editor.state.fields(Sticky) shouldBe 0
  }

  it should "not fall back on a pure selection change" in {
    // Der Unterschied ist der Punkt der Sache: ein Formatierungswunsch am Caret ueberlebt das
    // blosse Bewegen des Cursors nicht, aber er soll auch nicht davon zurueckgesetzt werden --
    // §9 knuepft die Policy ausdruecklich an den Dokumentwechsel.
    val editor = session(Sticky)

    editor.update(_.setField(Sticky, 42))
    editor.update(_.select(RangeSelection.caret(Point.textBefore(id("t1"), 1))))

    editor.state.fields(Sticky) shouldBe 42
  }

  it should "still accept a value set in the very transaction that changes the document" in {
    // Die Policy ueberspringt Felder, die diese Transaktion selbst gesetzt hat. Ohne diese
    // Ausnahme koennte eine Aenderung ihren eigenen Folgewert nie setzen -- der Reset wuerde
    // ihn unmittelbar danach wieder einkassieren, und Reset-Felder waeren praktisch unbenutzbar.
    val editor = session(Sticky)

    editor.update { tx =>
      tx.spliceText(id("t1"), 0, 0, "X")
      tx.setField(Sticky, 7)
    }

    editor.state.fields(Sticky) shouldBe 7
  }

  // ---------------------------------------------------------------------------------------
  // Reducer
  // ---------------------------------------------------------------------------------------

  "A reducer" should "derive its value from the finished candidate" in {
    val editor = session(Length)

    editor.state.fields(Length) shouldBe 0
    editor.update(_.spliceText(id("t1"), 0, 0, "XX"))
    editor.state.fields(Length) shouldBe 7
  }

  it should "reject the whole transaction with a typed error" in {
    // §10, Schritt 5: Auch ein Reducer kann ablehnen, und dann ist die ganze Tx verworfen.
    val editor = session(Bounded)

    val outcome = editor.update(_.spliceText(id("t1"), 5, 0, "zu lang"))

    outcome.left.map(_.message) shouldBe Left("Feld `bounded`: 12 ist zu lang.")
    editor.state.revision.value shouldBe 0
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "Hallo"))
    editor.state.fields(Bounded) shouldBe 0
  }

  it should "leave no partially reduced fields behind" in {
    // Length steht vor Bounded; sein Ergebnis darf die abgelehnte Tx nicht ueberleben.
    val editor  = EditorSession.create(document, SessionConfig(fields = Vector(Length, Bounded)))
    val outcome = editor.update(_.spliceText(id("t1"), 5, 0, "zu lang"))

    outcome.isLeft shouldBe true
    editor.state.fields(Length) shouldBe 0
  }

  it should "run even when only the selection changed" in {
    // Der Kandidat ist auch dann fertig gerechnet -- ein abgeleiteter Formularwert muss zu
    // jeder veroeffentlichten Revision passen, nicht nur zu den dokumentaendernden.
    val editor = session(Length)
    editor.update(_.select(RangeSelection.caret(Point.textBefore(id("t1"), 1))))

    editor.state.fields(Length) shouldBe 5
  }

  // ---------------------------------------------------------------------------------------
  // Speicher
  // ---------------------------------------------------------------------------------------

  "The field store" should "hand back exactly the declared type" in {
    // Kein Cast an der Aufrufstelle, keine oeffentliche Map[String, Any] (§8.1). Dass die
    // folgenden Zeilen ueberhaupt compilieren, ist die eigentliche Zusicherung.
    val fields: StateFields = StateFields.initial(Vector(Draft, Sticky))
    val text: String        = fields(Draft)
    val number: Int         = fields(Sticky)

    text shouldBe ""
    number shouldBe 0
  }

  it should "distinguish fields by identity, not by name" in {
    object Twin extends StateField[String]:
      val name    = "draft"
      val initial = "anders"

    val fields = StateFields.initial(Vector(Draft, Twin))

    fields(Draft) shouldBe ""
    fields(Twin) shouldBe "anders"
  }

  it should "compare by value" in {
    StateFields.initial(Vector(Draft)) shouldBe StateFields.initial(Vector(Draft))
    StateFields.initial(Vector(Draft)) should not be StateFields.initial(Vector(Draft, Sticky))
  }
}
