package ember.editor.forms

import ember.editor.core.*
import ember.editor.json.*
import ember.editor.markdown.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The form field contracts of §16 (P19b).
  *
  * ==Why this suite is headless==
  *
  * §16's HTML is a "Strukturillustration"; what it actually specifies is ownership, staleness and
  * atomicity. Those are properties of the binding, not of a DOM, and testing them here means
  * testing the rule rather than one rendering of it.
  *
  * What a browser has to answer -- is the textarea named, focusable and submittable without
  * JavaScript -- is in the browser gate, where it belongs.
  *
  * ==Why the node types are local==
  *
  * `ember-forms` depends on the core, `markdown` and `json`; §6 gives it no rich-text profile.
  * Building its own block type here is the check that it needs none -- the same argument as the
  * local `BlockNode` in `ember-image` and the local `Box` in `MarkdownSourceMapSpec`.
  */
final class EditorFieldSpec extends AnyFlatSpec with Matchers {

  private val root = NodeId("root")

  private val rules: MarkdownSupport =
    MarkdownSupport.of(TestRules.block, TestRules.text, TestRules.loud)

  private def markdownField(loss: LossPolicy = LossPolicy.Strict): EditorField =
    EditorFields.markdown("body", rules, NodeIdGenerator.sequential("m"), loss = loss)

  private def jsonField: EditorField =
    EditorFields.json("body", TestRules.jsonSupport)

  /** A session over `root > box > "text"`, with the field installed. */
  private def open(
      field: EditorField,
      text: String = "Hallo",
      marks: MarkSet = MarkSet.empty
  ): EditorSession =
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
            TextNode(NodeId("t"), text, marks)
          )
        ),
        resolved,
        resolved.sessionConfig()
      )
      .getOrElse(fail("Sitzung nicht erzeugbar"))

  private def bind(
      field: EditorField,
      session: EditorSession,
      intents: IntentPolicy = IntentPolicy.Defer,
      submits: SubmitPolicy = SubmitPolicy.ImportDraft
  ): EditorFormBinding = new EditorFormBinding(session, field, intents, submits)

  // ---------------------------------------------------------------------------------------
  // Der Formwert
  // ---------------------------------------------------------------------------------------

  "The submit value" should "be the encoded document" in {
    val field = markdownField()
    bind(field, open(field)).submitValue.trim shouldBe "Hallo"
  }

  it should "follow every commit synchronously" in {
    // §16: "Im Rich-Modus wird der Submit-Wert nach jedem Dokumentcommit synchron aktualisiert."
    // Das ist der Vertrag, der einen veralteten Payload bei Enter oder `requestSubmit`
    // ausschliesst -- eine verzoegerte Vorschau waere davon unabhaengig.
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session)

    session.update(
      _.replace(NodeId("t"), TextNode(NodeId("t"), "Geaendert")): Unit
    ) should matchPattern { case Right(_) => }

    binding.submitValue.trim shouldBe "Geaendert"
  }

  it should "be the empty value for an empty JSON field, not an empty string" in {
    // Ein Server, der `""` bekommt, kann ein leeres Dokument nicht von einem fehlenden Feld
    // unterscheiden. Der Payload eines leeren Dokuments kann es.
    val field   = jsonField
    val binding = bind(field, open(field))

    binding.emptyValue should not be ""
    binding.emptyValue should include("ember.core.root/1")
  }

  "The field name" should "be what a server sees" in {
    // §16: genau ein erfolgreiches benanntes Formularfeld je Editor.
    val field = markdownField()
    bind(field, open(field)).fieldName shouldBe "body"
  }

  // ---------------------------------------------------------------------------------------
  // Die Formatgrenze, vor dem Commit
  // ---------------------------------------------------------------------------------------

  "An unrepresentable change" should "be rejected before the commit" in {
    // Der Fall, den §16 als Beispiel nennt: "Ein `ToggleUnderline` in einem
    // Strict-CommonMark-Feld kann daher keinen kanonischen Zustand erzeugen, dessen Formwert
    // veraltet bleibt." Hier ist `Quiet` die Mark ohne Markdown-Schreibweise.
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session)

    val before = binding.submitValue

    val outcome = session.update(
      _.replace(NodeId("t"), TextNode(NodeId("t"), "Hallo", MarkSet.of(Quiet))): Unit
    )

    outcome should matchPattern { case Left(UpdateError.FieldRejected(_, _)) => }
    binding.submitValue shouldBe before
  }

  it should "leave the document untouched" in {
    // "nicht nur UI deaktivieren" (P19b, Tests): das Dokument darf den Stand gar nicht erst
    // annehmen, sonst waere der Formwert veraltet statt der Commit abgewiesen.
    val field   = markdownField()
    val session = open(field)
    bind(field, session): Unit

    session.update(
      _.replace(NodeId("t"), TextNode(NodeId("t"), "Hallo", MarkSet.of(Quiet))): Unit
    ): Unit

    session.document.node(NodeId("t")) should matchPattern {
      case Some(TextNode(_, "Hallo", marks)) if marks.isEmpty =>
    }
  }

  it should "leave the history untouched" in {
    val field   = markdownField()
    val session = open(field)
    val before  = session.state.revision

    session.update(
      _.replace(NodeId("t"), TextNode(NodeId("t"), "Hallo", MarkSet.of(Quiet))): Unit
    ): Unit

    session.state.revision shouldBe before
  }

  it should "go through once the field allows a lossy conversion" in {
    // §16: "bzw. eine explizite verlustbehaftete Konvertierung mit Diagnose." Ausdruecklich
    // gewaehlt, nicht stillschweigend.
    val field   = markdownField(LossPolicy.AllowLossy)
    val session = open(field)

    session.update(
      _.replace(NodeId("t"), TextNode(NodeId("t"), "Hallo", MarkSet.of(Quiet))): Unit
    ) should matchPattern { case Right(_) => }
  }

  it should "go through in a JSON field, which loses nothing" in {
    val field   = jsonField
    val session = open(field)

    session.update(
      _.replace(NodeId("t"), TextNode(NodeId("t"), "Hallo", MarkSet.of(Quiet))): Unit
    ) should matchPattern { case Right(_) => }
  }

  "The encoded value" should "carry the revision it belongs to" in {
    // §16: "Die anschliessende Formprojektion uebernimmt diesen bereits geprueften Wert
    // derselben Revision." Ein String allein kann nicht sagen, ob er aktuell ist.
    val field   = markdownField()
    val session = open(field)

    session.update(_.replace(NodeId("t"), TextNode(NodeId("t"), "Neu")): Unit): Unit

    val value = field.valueFor(session.state).getOrElse(fail("kein Wert"))
    value.isFor(session.state) shouldBe true
    value.revision shouldBe Some(session.state.revision)
  }

  it should "not be persisted into the history" in {
    // §16: "Das abgeleitete Feld wird nicht persistiert und bei Undo neu berechnet; History
    // speichert keine veralteten Codec-Ergebnisse." Ein gecachtes Encode-Ergebnis in einem
    // Snapshot waere ein Cache, den niemand invalidiert.
    markdownField().onHistoryRestore shouldBe HistoryRestorePolicy.Recompute
  }

  // ---------------------------------------------------------------------------------------
  // Der Source-Draft
  // ---------------------------------------------------------------------------------------

  "Entering source mode" should "open a draft from the current value" in {
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session)

    val draft = binding.enterSource().getOrElse(fail("kein Draft"))

    draft.text.trim shouldBe "Hallo"
    draft.baseline shouldBe session.state.documentRevision
    binding.mode shouldBe FieldMode.Source
  }

  it should "refuse when the document has no representation" in {
    // §16: "Ein Wechsel zu Source darf unbekannte Nodes niemals still entfernen." Ein Draft,
    // der etwas nicht enthaelt, loeschte es beim Zurueckschreiben.
    val field   = markdownField(LossPolicy.AllowLossy)
    val session = open(field, marks = MarkSet.of(Quiet))

    val strict  = markdownField()
    val binding = bind(strict, session)

    binding.enterSource() should matchPattern { case Left(_) => }
    binding.mode shouldBe FieldMode.Rich
  }

  "A draft" should "own the submit value while it is open" in {
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session)

    binding.enterSource(): Unit
    binding.editDraft("# Ganz anders")

    binding.submitValue shouldBe "# Ganz anders"
  }

  it should "not be overwritten by a document change" in {
    // §16: "Document→Form-Projektion darf diesen Draft nicht ueberschreiben." Der Kern des
    // ganzen Modus -- was jemand getippt hat, verschwindet nicht, weil sich anderswo etwas tut.
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session)

    binding.enterSource(): Unit
    binding.editDraft("Mein Text")

    session.update(_.replace(NodeId("t"), TextNode(NodeId("t"), "Fremd")): Unit): Unit

    binding.submitValue shouldBe "Mein Text"
  }

  it should "keep the textarea selection" in {
    val field   = markdownField()
    val binding = bind(field, open(field))

    binding.enterSource(Some(SourceSelection(2, 5))): Unit
    binding.pendingDraft.flatMap(_.selection) shouldBe Some(SourceSelection(2, 5))
  }

  "Applying a draft" should "import it and return to the rich mode" in {
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session)

    binding.enterSource(): Unit
    binding.editDraft("Ganz neuer Text")
    binding.applyDraft() shouldBe Right(())

    binding.mode shouldBe FieldMode.Rich
    binding.pendingDraft shouldBe None
    binding.submitValue.trim shouldBe "Ganz neuer Text"
  }

  it should "be one transaction, not two" in {
    // §16: "Wechsel/Submit importiert den Draft gegen seine Baseline '''atomar'''." Zwei
    // Transaktionen waeren zwei History-Stufen, und ein Undo naehme den halben Import zurueck.
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session)

    val before = session.state.revision
    binding.enterSource(): Unit
    binding.editDraft("Neu")
    binding.applyDraft(): Unit

    session.state.revision.value shouldBe (before.value + 1)
  }

  it should "refuse a stale draft and keep the text" in {
    // §16: "Decode-/Konfliktfehler erhalten den sichtbaren String." Der Draft wurde gegen eine
    // Revision geschrieben, die es nicht mehr gibt -- ihn trotzdem anzuwenden verwuerfe still,
    // was das Dokument inzwischen bewegt hat.
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session)

    binding.enterSource(): Unit
    binding.editDraft("Mein Text")
    session.update(_.replace(NodeId("t"), TextNode(NodeId("t"), "Fremd")): Unit): Unit

    binding.applyDraft() should matchPattern { case Left(FieldError.StaleDraft(_, _)) => }
    binding.submitValue shouldBe "Mein Text"
    binding.mode shouldBe FieldMode.Source
  }

  it should "refuse an unrepresentable result and keep the text" in {
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session)

    binding.enterSource(): Unit
    // `[[` ist kein gueltiges Markdown-Konstrukt fuer dieses winzige Regelwerk: ein Trenner hat
    // keine Regel, also weist der Codec ihn ab.
    binding.editDraft("---")

    binding.applyDraft() should matchPattern { case Left(_) => }
    binding.submitValue shouldBe "---"
    binding.mode shouldBe FieldMode.Source
  }

  "Discarding a draft" should "be an explicit action" in {
    // §16: "Ein Reset/Verwerfen ist eine ausdrueckliche Formaktion." Nie implizit, nie als
    // Nebeneffekt einer Dokumentaenderung.
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session)

    binding.enterSource(): Unit
    binding.editDraft("Wird verworfen")
    binding.discardDraft()

    binding.mode shouldBe FieldMode.Rich
    binding.submitValue.trim shouldBe "Hallo"
  }

  // ---------------------------------------------------------------------------------------
  // Fremde Aenderungen waehrend der Bearbeitung
  // ---------------------------------------------------------------------------------------

  "An independent change" should "run immediately in the rich mode" in {
    val field   = markdownField()
    val binding = bind(field, open(field))

    binding.request("upload")(42) shouldBe IntentOutcome.Applied(42)
  }

  it should "be deferred while the source is being edited" in {
    // §16 nennt Upload-Completion als den Fall: eine Datei wird fertig, waehrend jemand den
    // Quelltext bearbeitet, und sie anzuwenden ueberschriebe den Entwurf.
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session, IntentPolicy.Defer)

    binding.enterSource(): Unit
    binding.editDraft("Mein Text")

    var ran = false
    binding.request("upload") { ran = true } shouldBe IntentOutcome.Deferred
    ran shouldBe false
    binding.deferred shouldBe Vector("upload")
  }

  it should "run after the draft lands, against the imported document" in {
    // "erst danach werden wartende Intents neu validiert" (§16). Deshalb ist der zurueckgestellte
    // Intent ein Thunk und kein Wert: er sieht das importierte Dokument, nicht das alte.
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session, IntentPolicy.Defer)

    binding.enterSource(): Unit
    binding.editDraft("Importiert")

    var seen: Option[String] = None
    // Der Import baut ein 'neues' Dokument mit frischen IDs -- §16 sagt es fuer die Identitaet
    // ausdruecklich: sie ueberlebt eine Bearbeitung, nicht einen Export und Re-Import. Also
    // fragt der Intent nach dem Text und nicht nach einer ID.
    binding.request("lesen") {
      seen = session.document.inDocumentOrder.collectFirst { case run: TextNode => run.text }
    }: Unit

    binding.applyDraft() shouldBe Right(())

    seen shouldBe Some("Importiert")
    binding.deferred shouldBe empty
  }

  it should "run after an explicit discard too" in {
    val field   = markdownField()
    val binding = bind(field, open(field), IntentPolicy.Defer)

    binding.enterSource(): Unit
    var ran = false
    binding.request("upload") { ran = true }: Unit
    binding.discardDraft()

    ran shouldBe true
  }

  it should "be refused with SourceBusy under the other policy" in {
    val field   = markdownField()
    val binding = bind(field, open(field), IntentPolicy.Reject)

    binding.enterSource(): Unit

    binding.request("upload")(()) should matchPattern {
      case IntentOutcome.Refused(FieldError.SourceBusy("upload")) =>
    }
  }

  // ---------------------------------------------------------------------------------------
  // Submit
  // ---------------------------------------------------------------------------------------

  "Submitting" should "send the document value in the rich mode" in {
    val field   = markdownField()
    val binding = bind(field, open(field))

    binding.valueForSubmit().map(_.trim) shouldBe Right("Hallo")
  }

  it should "import an open draft first" in {
    // §16: "Wechsel/Submit importiert den Draft gegen seine Baseline atomar." Der Server
    // bekommt ein Dokument, keinen unbestaetigten String.
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session, submits = SubmitPolicy.ImportDraft)

    binding.enterSource(): Unit
    binding.editDraft("Vor dem Senden")

    binding.valueForSubmit().map(_.trim) shouldBe Right("Vor dem Senden")
    binding.mode shouldBe FieldMode.Rich
    session.document.inDocumentOrder.collectFirst { case run: TextNode => run.text } shouldBe
      Some("Vor dem Senden")
  }

  it should "block when the import fails, and keep the text" in {
    val field   = markdownField()
    val session = open(field)
    val binding = bind(field, session, submits = SubmitPolicy.ImportDraft)

    binding.enterSource(): Unit
    binding.editDraft("---")

    binding.valueForSubmit() should matchPattern { case Left(_) => }
    binding.submitValue shouldBe "---"
  }

  it should "refuse outright under the other policy" in {
    val field   = markdownField()
    val binding = bind(field, open(field), submits = SubmitPolicy.RefuseWhileDirty)

    binding.enterSource(): Unit
    binding.editDraft("Unbestaetigt")

    binding.valueForSubmit() should matchPattern { case Left(FieldError.SourceBusy(_)) => }
  }

  // ---------------------------------------------------------------------------------------
  // Unicode und Escaping
  // ---------------------------------------------------------------------------------------

  "A value" should "carry text a form has to escape" in {
    // P19b, Tests: "ein FormData-Feld, Unicode/Escaping". Was hier zaehlt, ist dass der Wert
    // unveraendert durchkommt -- das HTML-Escaping ist Sache der Textarea (P19a).
    val tricky  = "Ein & Zeichen, ein </textarea> und ein Emoji 🚀"
    val field   = jsonField
    val session = open(field, text = tricky)
    val binding = bind(field, session)

    binding.submitValue should include("\\u0026")
  }

  it should "round-trip such text through a JSON draft" in {
    val tricky  = "Ein & Zeichen, ein </textarea> und ein Emoji 🚀"
    val field   = jsonField
    val session = open(field, text = tricky)
    val binding = bind(field, session)

    binding.enterSource(): Unit
    binding.applyDraft() shouldBe Right(())

    session.document.inDocumentOrder.collectFirst { case run: TextNode => run.text } shouldBe
      Some(tricky)
  }

  it should "round-trip a leading newline, which HTML would otherwise eat" in {
    // §16 nennt es ausdruecklich fuer die Textarea; hier ist die Modellseite davon.
    val field   = jsonField
    val session = open(field, text = "\nMit fuehrendem Umbruch")
    val binding = bind(field, session)

    binding.enterSource(): Unit
    binding.applyDraft() shouldBe Right(())

    session.document.node(NodeId("t")) should matchPattern {
      case Some(TextNode(_, "\nMit fuehrendem Umbruch", _)) =>
    }
  }
}
