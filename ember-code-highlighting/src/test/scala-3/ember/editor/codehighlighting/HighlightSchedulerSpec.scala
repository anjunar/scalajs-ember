package ember.editor.codehighlighting

import ember.editor.code.*
import ember.editor.core.*
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

/** Which blocks are asked for, which answers are shown, and that none of it is the document (X02).
  *
  * {{{
  * root
  *   p0   paragraph   t0  "Intro"
  *   c0   code scala  tc  "val x = 1"
  *   c1   code        tn  "no language"
  * }}}
  */
final class HighlightSchedulerSpec extends AnyFlatSpec with Matchers {

  private final class RecordingSink extends HighlightSink:
    val shown   = mutable.ArrayBuffer.empty[HighlightResult]
    val cleared = mutable.ArrayBuffer.empty[NodeId]
    var ready   = true

    def show(result: HighlightResult): Boolean =
      if ready then shown += result
      ready

    def clear(block: NodeId): Unit = cleared += block

  /** A worker that answers when told to -- or answers a question that has been withdrawn. */
  private final class DeferredHighlighter extends Highlighter:
    private val local = new LocalHighlighter()
    val requests      = mutable.ArrayBuffer.empty[(HighlightRequest, HighlightResult => Unit)]
    val cancelled     = mutable.ArrayBuffer.empty[HighlightRequest]

    def highlight(request: HighlightRequest, reply: HighlightResult => Unit): Subscription =
      requests += request -> reply
      Subscription(() => cancelled += request)

    def forget(block: NodeId): Unit = local.forget(block)

    def answer(index: Int): Unit =
      val (request, reply) = requests(index)
      reply(local.compute(request))

  private def open(): EditorSession =
    val generator = NodeIdGenerator.sequential("g")
    val resolved  = ExtensionResolver
      .resolve(Vector(RichText(generator), CodeExtension(generator)))
      .getOrElse(fail("extensions"))
    val root = NodeId("root")

    EditorSession
      .create(
        Document.unsafe(
          resolved.schema,
          root,
          Vector(
            RootNode(root, Vector(NodeId("p0"), NodeId("c0"), NodeId("c1"))),
            ParagraphNode(NodeId("p0"), Vector(NodeId("t0"))),
            TextNode(NodeId("t0"), "Intro"),
            CodeBlockNode(NodeId("c0"), Vector(NodeId("tc")), CodeInfo.of("scala")),
            TextNode(NodeId("tc"), "val x = 1"),
            CodeBlockNode(NodeId("c1"), Vector(NodeId("tn"))),
            TextNode(NodeId("tn"), "no language")
          )
        ),
        resolved,
        resolved.sessionConfig(errorSink = error => fail(error.render))
      )
      .getOrElse(fail("session"))

  private def type_(session: EditorSession, node: String, at: Int, text: String): Unit =
    session.update(_.spliceText(NodeId(node), at, 0, text): Unit) match
      case Right(_)    => ()
      case Left(error) => fail(error.render)

  private def caretIn(session: EditorSession, node: String): Unit =
    session.update(_.select(RangeSelection.caret(Point.textBefore(NodeId(node), 0))): Unit): Unit

  private val c0 = NodeId("c0")
  private val c1 = NodeId("c1")

  "Starting" should "ask for every code block and show what has a language" in {
    val session   = open()
    val sink      = new RecordingSink
    val scheduler = new HighlightScheduler(session, new LocalHighlighter(), sink)

    scheduler.start()
    scheduler.dirtyBlocks shouldBe Set(c0, c1)
    scheduler.flush()

    sink.shown.map(_.block) shouldBe Vector(c0)
    sink.shown.head.tokens.map(token => "val x = 1".substring(token.start, token.end)) should
      contain("val")
    scheduler.isDirty shouldBe false
  }

  "Typing" should "ask again for the block it happened in, and only for that one" in {
    val session   = open()
    val sink      = new RecordingSink
    val scheduler = new HighlightScheduler(session, new LocalHighlighter(), sink)
    scheduler.start()
    scheduler.flush()

    type_(session, "t0", 5, "!")
    scheduler.isDirty shouldBe false

    type_(session, "tc", 9, "0")
    scheduler.dirtyBlocks shouldBe Set(c0)
    scheduler.flush()

    sink.shown.last.text shouldBe "val x = 10"
  }

  it should "wake its owner once something became dirty" in {
    val session   = open()
    var wakes     = 0
    val scheduler =
      new HighlightScheduler(session, new LocalHighlighter(), new RecordingSink, () => wakes += 1)
    scheduler.start()
    scheduler.flush()
    val before = wakes

    type_(session, "t0", 0, "x")
    wakes shouldBe before

    type_(session, "tc", 0, "x")
    wakes shouldBe before + 1
  }

  "A late answer" should "be ignored once a newer request exists" in {
    // X02: "veraltetes Worker-Ergebnis ignoriert".
    val session     = open()
    val sink        = new RecordingSink
    val highlighter = new DeferredHighlighter
    val scheduler   = new HighlightScheduler(session, highlighter, sink)
    scheduler.start()
    scheduler.flush()
    highlighter.requests.clear()

    type_(session, "tc", 9, "0")
    scheduler.flush()
    type_(session, "tc", 10, "0")
    scheduler.flush()

    highlighter.cancelled.map(_.text) should contain("val x = 10")

    highlighter.answer(1)
    highlighter.answer(0)

    sink.shown.map(_.text) shouldBe Vector("val x = 100")
    scheduler.staleResults shouldBe 1
  }

  it should "be ignored when the text changed before anyone asked again" in {
    val session     = open()
    val sink        = new RecordingSink
    val highlighter = new DeferredHighlighter
    val scheduler   = new HighlightScheduler(session, highlighter, sink)
    scheduler.start()
    scheduler.flush()
    val scalaRequest = highlighter.requests.indexWhere(_._1.block == c0)

    type_(session, "tc", 0, "x")
    highlighter.answer(scalaRequest)

    sink.shown shouldBe empty
    scheduler.staleResults shouldBe 1
    scheduler.dirtyBlocks shouldBe Set(c0)
  }

  "A view that cannot show a result yet" should "get the block again with the next flush" in {
    // The browser is ahead of the model during a composition, so the text node is not the text the
    // result was made for (§15.3). Nothing is painted, and nothing is forgotten either.
    val session   = open()
    val sink      = new RecordingSink
    val scheduler = new HighlightScheduler(session, new LocalHighlighter(), sink)
    sink.ready = false
    scheduler.start()
    scheduler.flush()

    scheduler.dirtyBlocks shouldBe Set(c0)

    sink.ready = true
    scheduler.flush()
    sink.shown.map(_.block) shouldBe Vector(c0)
  }

  "A block that stops being code" should "lose its colours" in {
    val session   = open()
    val sink      = new RecordingSink
    val scheduler = new HighlightScheduler(session, new LocalHighlighter(), sink)
    scheduler.start()
    scheduler.flush()

    caretIn(session, "tc")
    session.dispatch(CodeCommands.ToggleCodeBlock, CodeInfo.empty) shouldBe a[Right[?, ?]]
    scheduler.flush()

    sink.cleared should contain(c0)
    scheduler.shownBlocks should not contain c0
  }

  "A language nobody knows" should "clear what the old language painted" in {
    val session   = open()
    val sink      = new RecordingSink
    val scheduler = new HighlightScheduler(session, new LocalHighlighter(), sink)
    scheduler.start()
    scheduler.flush()

    caretIn(session, "tc")
    session.dispatch(CodeCommands.SetCodeInfo, CodeInfo.of("cobol")) shouldBe a[Right[?, ?]]
    scheduler.flush()

    sink.cleared should contain(c0)
  }

  "Disposing" should "clear everything shown and cancel what is pending" in {
    val session     = open()
    val sink        = new RecordingSink
    val highlighter = new DeferredHighlighter
    val scheduler   = new HighlightScheduler(session, highlighter, sink)
    scheduler.start()
    scheduler.flush()
    highlighter.answer(highlighter.requests.indexWhere(_._1.block == c0))
    type_(session, "tc", 0, "x")
    scheduler.flush()

    scheduler.dispose()

    sink.cleared should contain(c0)
    highlighter.cancelled.map(_.text) should contain("xval x = 1")
    type_(session, "tc", 0, "y")
    scheduler.isDirty shouldBe false
  }

  "Highlighting" should "never touch the document or its revision" in {
    // X02: "Highlighting ausblenden aendert kein Dokument/History." Stronger than a round trip
    // through JSON or Markdown: the document is the same object before and after, so every format
    // writes what it wrote before.
    val session   = open()
    val document  = session.document
    val revision  = session.state.revision
    val scheduler = new HighlightScheduler(session, new LocalHighlighter(), new RecordingSink)

    scheduler.start()
    scheduler.flush()
    scheduler.invalidateAll()
    scheduler.flush()
    scheduler.dispose()

    session.document should be theSameInstanceAs document
    session.state.revision shouldBe revision
  }
}
