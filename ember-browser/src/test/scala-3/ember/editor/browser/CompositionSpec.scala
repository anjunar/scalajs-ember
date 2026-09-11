package ember.editor.browser

import ember.editor.core.*
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The composition protocol's rules, as rules (P23).
  *
  * ==What is here==
  *
  * The parts that are decisions about values: which area a composition owns, which transactions
  * are refused while it runs, what happens to the ones that wait. Those carry the reasoning; an
  * engine would only make them harder to read.
  *
  * ==What is not==
  *
  * Everything an IME actually does. §15.3's own warning applies to the tests as much as to the
  * code -- "Ein willkuerlicher Timeout ohne reproduzierten Browserfall ist kein
  * Abschlussprotokoll" -- and a synthetic `compositionstart` is not an IME. The engine-level
  * behaviour is in `composition.spec.mjs` and `mutation-race.spec.mjs`, and the real acceptance
  * is a person with a keyboard: `manual-ime.md`.
  */
final class CompositionSpec extends AnyFlatSpec with Matchers {

  /** `root > [p0 "Hallo", p1 "Welt", p2 "Drei"]`, with the composition gate installed.
    *
    * The gate goes in as an extension's pre-commit rule, which is how a real editor installs it:
    * §10's step 5 is where it has to run, and a session's rules are fixed when it is built.
    */
  private final class Fixture:
    val generator: NodeIdGenerator = NodeIdGenerator.sequential("n")

    /** The composition the gate should refuse for, settable per test. */
    var running: Option[Long] = None

    private object Gate extends Extension:
      val id: ExtensionId = ExtensionId("ember.test.composition-gate")
      override def contribute: ExtensionContributions =
        ExtensionContributions(preCommitRules = Vector(CompositionGate.rule(() => running)))

    private val resolved = ExtensionResolver
      .resolve(Vector(RichText(generator), Gate))
      .getOrElse(throw new AssertionError("Extensions nicht aufloesbar"))

    val session: EditorSession = EditorSession
      .create(
        Document.unsafe(
          resolved.schema,
          NodeId("root"),
          Vector(
            RootNode(NodeId("root"), Vector(NodeId("p0"), NodeId("p1"), NodeId("p2"))),
            ParagraphNode(NodeId("p0"), Vector(NodeId("t0"))),
            TextNode(NodeId("t0"), "Hallo"),
            ParagraphNode(NodeId("p1"), Vector(NodeId("t1"))),
            TextNode(NodeId("t1"), "Welt"),
            ParagraphNode(NodeId("p2"), Vector(NodeId("t2"))),
            TextNode(NodeId("t2"), "Drei")
          )
        ),
        resolved,
        resolved.sessionConfig()
      )
      .getOrElse(throw new AssertionError("Sitzung nicht erzeugbar"))

    def document: Document = session.document

    def caret(node: String, offset: Int): Option[Selection] =
      Some(RangeSelection.caret(Point.textBefore(NodeId(node), offset)))

    def range(from: (String, Int), to: (String, Int)): Option[Selection] =
      Some(
        RangeSelection(
          Point.textBefore(NodeId(from._1), from._2),
          Point.textBefore(NodeId(to._1), to._2)
        )
      )

  private def blocks(region: ProtectedRegion): Vector[String] = region match
    case ProtectedRegion.Blocks(nodes) => nodes.map(_.value)
    case ProtectedRegion.WholeHost     => Vector("<host>")

  // ---------------------------------------------------------------------------------------
  // Der geschuetzte Bereich (§15.3)
  // ---------------------------------------------------------------------------------------

  "A caret" should "protect at least its own block" in {
    // §15.3: "Bei einer kollabierten Range ist dies mindestens der aktive Block." Not the run:
    // an IME replacement reaches past the leaf it started in.
    val fixture = new Fixture

    blocks(CompositionRegion.of(fixture.document, fixture.caret("t1", 2))) shouldBe Vector("p1")
  }

  "A range across blocks" should "protect every block it touches" in {
    // "Ein blockuebergreifender Start darf nicht als Ein-Leaf-Fall behandelt werden." Including
    // the ones in between, which neither endpoint names.
    val fixture = new Fixture

    blocks(CompositionRegion.of(fixture.document, fixture.range(("t0", 1), ("t2", 1)))) shouldBe
      Vector("p0", "p1", "p2")
  }

  it should "not care which end the user dragged from" in {
    val fixture = new Fixture

    blocks(CompositionRegion.of(fixture.document, fixture.range(("t2", 1), ("t0", 1)))) shouldBe
      Vector("p0", "p1", "p2")
  }

  "A composition without a selection" should "protect the whole host" in {
    // §15.3's escape hatch: "Reicht eine lokale Schutzgrenze nicht, wird der ganze Editing-Host
    // geschuetzt." Not knowing where the composition is, is exactly such a case -- and guessing
    // a narrower area is the one mistake that costs text.
    val fixture = new Fixture

    CompositionRegion.of(fixture.document, None) shouldBe ProtectedRegion.WholeHost
  }

  "A node selection" should "protect the blocks of its nodes" in {
    val fixture = new Fixture
    val nodes   = NodeSelection(Set(NodeId("t0"), NodeId("t2")))

    blocks(CompositionRegion.of(fixture.document, Some(nodes))) shouldBe Vector("p0", "p2")
  }

  "The root" should "have no block of its own" in {
    // A composition that claims the root claims everything, and saying so is more honest than
    // protecting "the root's block".
    val fixture = new Fixture

    CompositionRegion.blockOf(fixture.document, NodeId("root")) shouldBe None
    CompositionRegion.blockOf(fixture.document, NodeId("t1")) shouldBe Some(NodeId("p1"))
    CompositionRegion.blockOf(fixture.document, NodeId("p1")) shouldBe Some(NodeId("p1"))
  }

  // ---------------------------------------------------------------------------------------
  // Das Commit-Gate (§15.3)
  // ---------------------------------------------------------------------------------------

  private def edit(fixture: Fixture, meta: TransactionMeta): Either[String, Unit] =
    fixture.session
      .update(meta)(_.spliceText(NodeId("t0"), 0, 0, "X"): Unit)
      .left
      .map(_.render)
      .map(_ => ())

  private def textOf(fixture: Fixture, node: String): String =
    fixture.session.document.node(NodeId(node)).collect { case run: TextNode => run.text }.getOrElse("")

  "An independent change" should "be refused while a composition runs" in {
    // §15.3: "Waehrend Composition werden alle unabhaengigen Dokumenttransaktionen, auch
    // ausserhalb des geschuetzten Bereichs, vor Commit als CompositionBusy abgewiesen."
    val fixture = new Fixture
    fixture.running = Some(7)

    edit(fixture, TransactionMeta()).left.map(_.contains("Composition")) shouldBe Left(true)
    // Refused '''before''' commit: the document never saw it.
    textOf(fixture, "t0") shouldBe "Hallo"
  }

  it should "be refused even outside the protected area" in {
    // The part that surprises: the reason is not the DOM but the history. §14 undoes a group
    // whole, and an independent commit inside the composition's group would go with it.
    val fixture = new Fixture
    fixture.running = Some(7)

    fixture.session
      .update(TransactionMeta())(_.spliceText(NodeId("t2"), 0, 0, "X"): Unit)
      .isLeft shouldBe true
  }

  it should "let the composition's own transactions through" in {
    // "Erlaubt bleiben zugehoerige native Composition-Updates sowie reine Selection-/View-/
    // Effect-Aenderungen."
    val fixture = new Fixture
    fixture.running = Some(7)

    edit(fixture, CompositionGate.meta("composition-input")) shouldBe Right(())
    textOf(fixture, "t0") shouldBe "XHallo"
  }

  it should "let a selection change through" in {
    val fixture = new Fixture
    fixture.running = Some(7)

    fixture.session
      .update(_.select(RangeSelection.caret(Point.textBefore(NodeId("t1"), 1))): Unit)
      .isRight shouldBe true
  }

  it should "allow everything again once the composition ends" in {
    val fixture = new Fixture
    fixture.running = Some(7)
    edit(fixture, TransactionMeta()).isLeft shouldBe true

    fixture.running = None

    edit(fixture, TransactionMeta()) shouldBe Right(())
    textOf(fixture, "t0") shouldBe "XHallo"
  }

  it should "use the tag the rich-text profile also reads" in {
    // Two modules that never meet have to agree on the word; §8.2's deferred merge reads the
    // same one.
    CompositionGate.meta("x").tags should contain(CompositionGate.Tag)
    CompositionGate.Tag shouldBe TransactionMeta.CompositionTag
  }

  // ---------------------------------------------------------------------------------------
  // Die Warteschlange (§15.3)
  // ---------------------------------------------------------------------------------------

  private def intent(label: String, marks: Boolean = false)(using fixture: Fixture): DeferredIntent =
    DeferredIntent(
      label,
      Option.when(marks)(Bookmark(Point.textBefore(NodeId("t0"), 2), fixture.session.state.revision)),
      (session, point) =>
        session.update(_.spliceText(NodeId("t0"), 0, 0, label.take(1)): Unit).map(_ => ())
    )

  private val busy = CompositionBusy(1, DiagnosticPath.Root)

  "A deferred intent" should "run when the queue is released" in {
    given fixture: Fixture = new Fixture
    val queue             = new DeferredIntentQueue()

    queue.offer(intent("Alpha"), busy) shouldBe Right(())
    queue.size shouldBe 1

    queue.release(fixture.session) shouldBe Vector(IntentOutcome.Applied("Alpha"))
    queue.isEmpty shouldBe true
  }

  it should "run in the order it arrived" in {
    given fixture: Fixture = new Fixture
    val queue             = new DeferredIntentQueue()

    queue.offer(intent("Alpha"), busy): Unit
    queue.offer(intent("Beta"), busy): Unit

    queue.release(fixture.session).map(_.toString) shouldBe
      Vector(IntentOutcome.Applied("Alpha").toString, IntentOutcome.Applied("Beta").toString)
  }

  it should "be refused once it is full" in {
    // §15.3 says "begrenzte Queue", and the failure mode is why: a composition that never ends
    // would otherwise collect work without limit and apply all of it minutes later.
    given fixture: Fixture = new Fixture
    val queue             = new DeferredIntentQueue(limit = 1)

    queue.offer(intent("Alpha"), busy) shouldBe Right(())
    queue.offer(intent("Beta"), busy) shouldBe Left(busy)
    queue.size shouldBe 1
  }

  it should "report an expired bookmark instead of guessing" in {
    // §11: an insertion may not take the replacement boundary. An upload whose target is gone
    // must not land somewhere else.
    given fixture: Fixture = new Fixture
    val queue             = new DeferredIntentQueue()
    val stale = DeferredIntent(
      "Upload",
      Some(Bookmark(Point.textBefore(NodeId("t0"), 2), Revision(99))),
      (session, _) => Right(())
    )

    queue.offer(stale, busy): Unit

    queue.release(fixture.session) shouldBe Vector(IntentOutcome.Expired("Upload"))
  }

  it should "report a rejection with its reason" in {
    given fixture: Fixture = new Fixture
    val queue             = new DeferredIntentQueue()
    val doomed = DeferredIntent(
      "Kaputt",
      None,
      (session, _) => session.update(_.remove(NodeId("root")): Unit).map(_ => ())
    )

    queue.offer(doomed, busy): Unit

    queue.release(fixture.session).head should matchPattern {
      case IntentOutcome.Rejected("Kaputt", _) =>
    }
  }

  it should "be droppable without running" in {
    given fixture: Fixture = new Fixture
    val queue             = new DeferredIntentQueue()

    queue.offer(intent("Alpha"), busy): Unit
    queue.clear() shouldBe Vector("Alpha")
    queue.isEmpty shouldBe true
    // Nothing ran.
    fixture.session.document.node(NodeId("t0")).collect { case run: TextNode => run.text } shouldBe
      Some("Hallo")
  }

  // ---------------------------------------------------------------------------------------
  // Die Sitzung
  // ---------------------------------------------------------------------------------------

  "A session" should "carry what §15.3 asks it to" in {
    // "Die laufende Composition besitzt eine Session-ID, Ausgangsrevision, gemappte Selection und
    // die letzte erfasste native Eingabe."
    val fixture = new Fixture
    val open =
      CompositionSession.start(fixture.document, fixture.caret("t1", 2), Revision(5))

    open.baseRevision shouldBe Revision(5)
    open.selection shouldBe fixture.caret("t1", 2)
    open.captured shouldBe None
    open.protectedNodes shouldBe Vector(NodeId("p1"))
  }

  it should "get a new id every time" in {
    // A late event belongs to the composition it names, not to whichever one is running now --
    // which is the shape of most of the browser bugs §15.2 lists.
    val fixture = new Fixture
    val first   = CompositionSession.start(fixture.document, None, Revision.initial)
    val second  = CompositionSession.start(fixture.document, None, Revision.initial)

    first.id should not be second.id
  }

  it should "remember the last text it took" in {
    val fixture = new Fixture
    val open = CompositionSession
      .start(fixture.document, fixture.caret("t1", 2), Revision.initial)
      .withCapture(NodeId("t1"), "私")

    open.alreadyCaptured(NodeId("t1"), "私") shouldBe true
    open.alreadyCaptured(NodeId("t1"), "わ") shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // Recovery (§15.4)
  // ---------------------------------------------------------------------------------------

  "A repair" should "say what it did" in {
    RecoveryOutcome.Clean.render should include("stimmt")
    RecoveryOutcome.Repaired(Vector(NodeId("p1")), 1).render should include("p1")
    RecoveryOutcome
      .Exhausted(Vector(HydrationProblem.TextMismatch(NodeId("t1"), "a", "b")))
      .render should include("t1")
  }

  it should "allow exactly one retry by default" in {
    // §15.4: "Reparatur hat einen begrenzten Wiederholungsversuch" -- singular, because a rebuild
    // is itself a mutation and a failing repair produces the same records again.
    RecoveryController.DefaultLimit shouldBe 1
  }
}
