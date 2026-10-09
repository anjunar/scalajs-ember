package ember.editor.standard

import ember.editor.clipboard.*
import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.history.History
import ember.editor.link.*
import ember.editor.list.*
import ember.editor.code.*
import ember.editor.image.*
import ember.editor.html.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class ClipboardSpec extends AnyFlatSpec with Matchers:
  "Clipboard format preference" should "choose validated internal content before HTML and text" in {
    val f = new ClipboardFixture("<p><strong>Hello</strong> world</p>")
    f.select(0, 5)
    val copied = f.service.copy().toOption.get
    copied.formats.keySet shouldBe Set(
      ClipboardMime.Internal,
      ClipboardMime.Html,
      ClipboardMime.Text
    )
    val decoded = f.codec.decode(copied).toOption.get
    decoded.mime shouldBe ClipboardMime.Internal
    decoded.fragment.document.inDocumentOrder
      .collect { case t: TextNode => t }
      .next()
      .marks shouldBe MarkSet.of(StandardMarks.Strong)
    copied.formats(ClipboardMime.Html) should include("<strong>Hello</strong>")
    copied.formats(ClipboardMime.Html) should not include "data-ember"
  }
  it should "fall back after incompatible profiles and corrupt internal data" in {
    val f = new ClipboardFixture()
    f.select(0, 5)
    val internal = f.service.copy().toOption.get.formats(ClipboardMime.Internal)
    Vector(
      "{bad",
      internal.replace("standard/1", "foreign/1"),
      internal.replace("\"version\":1", "\"version\":99")
    ).foreach { bad =>
      val decoded = f.codec
        .decode(
          ClipboardData(
            Map(
              ClipboardMime.Internal -> bad,
              ClipboardMime.Html     -> "<p><em>safe</em></p>",
              ClipboardMime.Text     -> "plain"
            )
          )
        )
        .toOption
        .get
      decoded.mime shouldBe ClipboardMime.Html
      decoded.diagnostics should not be empty
      f.codec.text(decoded.fragment.document) shouldBe "safe"
    }
  }
  it should "reject unsupported data and oversized plain text without editing" in {
    val f = new ClipboardFixture()
    f.caret(3)
    f.service.paste(ClipboardData(Map("application/unknown" -> "x"))).isLeft shouldBe true
    f.paste("x" * (f.codec.maxSourceChars + 1)).isLeft shouldBe true
    f.paste("\n" * 10000).isLeft shouldBe true
    f.text shouldBe "Hello world"
  }
  it should "leave the replaced selection intact when a commit rule rejects insertion" in {
    val rule = PreCommitRule("clipboard-limit") { candidate =>
      Option.when(candidate.document.nodes.exists {
        case t: TextNode => t.text.contains("FORBIDDEN"); case _ => false
      })(
        Violation.NodeRejected(
          candidate.document.rootId,
          "Rejected fixture content",
          DiagnosticPath.Root
        )
      )
    }
    val f = new ClipboardFixture(rules = Vector(rule))
    f.select(0, 5)
    val before = f.session.state
    f.paste("FORBIDDEN").isLeft shouldBe true
    f.session.state shouldBe before
    f.history.undo() shouldBe Right(false)
  }
  "Plain paste" should "fit a single open paragraph into text" in {
    val f = new ClipboardFixture()
    f.caret(5)
    f.paste("!").isRight shouldBe true
    f.text shouldBe "Hello! world"
    f.session.document.childrenOf(NodeId("root")) should have size 1
  }
  it should "preserve all lines, CRLF and trailing empty lines" in {
    val f = new ClipboardFixture()
    f.caret(5)
    f.paste("A\r\nB\n").isRight shouldBe true
    f.text shouldBe "HelloA\nB\n world"
  }
  it should "keep a single pasted run inside its heading, list or link context" in {
    Vector("<h2>Hello</h2>", "<ul><li>Hello</li></ul>", "<p><a href='/x'>Hello</a></p>").foreach {
      source =>
        val f          = new ClipboardFixture(source)
        val containers = f.session.document.inDocumentOrder.collect { case e: ElementNode =>
          e.id
        }.toVector
        f.caret(2)
        f.paste("X").isRight shouldBe true
        f.text shouldBe "HeXllo"
        f.session.document.inDocumentOrder.collect { case e: ElementNode =>
          e.id
        }.toVector shouldBe containers
    }
  }
  it should "join the trailing context to the last pasted block at every boundary" in {
    Vector(0, 5, 11).foreach { offset =>
      val f = new ClipboardFixture()
      f.caret(offset)
      f.paste("A\nB").isRight shouldBe true
      f.text shouldBe "Hello world".take(offset) + "A\nB" + "Hello world".drop(offset)
      val range = f.session.selection.get.asInstanceOf[RangeSelection]
      range.isCollapsed shouldBe true
      range.focus.offset shouldBe 1
      f.history.undo() shouldBe Right(true)
      f.text shouldBe "Hello world"
    }
  }
  it should "replace a backward range and keep the caret at the inserted end" in {
    val f = new ClipboardFixture()
    f.select(11, 6)
    f.paste("you").isRight shouldBe true
    f.text shouldBe "Hello you"
    f.session.selection.collect { case r: RangeSelection => r.focus.offset } shouldBe Some(9)
  }
  it should "form a separate undo step" in {
    val f = new ClipboardFixture()
    f.caret(5)
    f.session.dispatch(RichText.InsertText, "X").isRight shouldBe true
    f.paste("Y").isRight shouldBe true
    f.history.undo() shouldBe Right(true)
    f.text shouldBe "HelloX world"
    f.history.undo() shouldBe Right(true)
    f.text shouldBe "Hello world"
  }
  "Copy and paste" should "reserve copied IDs while splitting the destination" in {
    val generator = new NodeIdGenerator:
      def next(isTaken: NodeId => Boolean): NodeId =
        Iterator.from(1).map(i => NodeId(s"reused$i")).find(id => !isTaken(id)).get
    Vector("X", "A\nB").foreach { value =>
      val f = new ClipboardFixture(generator = generator)
      f.caret(5)
      f.paste(value).isRight shouldBe true
      f.text shouldBe s"Hello${value} world"
    }
  }
  it should "remap every inserted ID" in {
    val f = new ClipboardFixture("<p><strong>Hello</strong> world</p>")
    f.select(0, 5)
    val data     = f.service.copy().toOption.get
    val original = f.runs.head.id
    f.session.update(_.select(RangeSelection.caret(Point.textBefore(f.runs.last.id, 6))))
    f.service.paste(data).isRight shouldBe true
    f.text shouldBe "Hello worldHello"
    f.runs.last.id should not be original
    f.runs.last.marks shouldBe MarkSet.of(StandardMarks.Strong)
  }
  "Cut" should "leave the document untouched when writing fails" in {
    val f = new ClipboardFixture()
    f.select(0, 5)
    val port = new ClipboardPort:
      def read()                     = Left(ClipboardError("unavailable"))
      def write(data: ClipboardData) = Left(ClipboardError("denied"))
    f.service.cut(port).isLeft shouldBe true
    f.text shouldBe "Hello world"
  }
  it should "delete exactly once after successful writing" in {
    val f = new ClipboardFixture()
    f.select(0, 5)
    val pending = f.service.prepareCut().toOption.get
    pending.confirm(Right(())).isRight shouldBe true
    pending.confirm(Right(())).isLeft shouldBe true
    f.text shouldBe " world"
    f.history.undo() shouldBe Right(true)
    f.text shouldBe "Hello world"
  }
  it should "refuse a completion after editing, even if Undo restored the content" in {
    val f = new ClipboardFixture()
    f.select(0, 5)
    val pending = f.service.prepareCut().toOption.get
    f.caret(11)
    f.session.dispatch(RichText.InsertText, "X")
    f.history.undo()
    pending.confirm(Right(())).isLeft shouldBe true
    f.text shouldBe "Hello world"
  }
  it should "revalidate the original range after selection-only movement" in {
    val f = new ClipboardFixture()
    f.select(0, 5)
    val pending = f.service.prepareCut().toOption.get
    f.caret(11)
    pending.confirm(Right(())).isRight shouldBe true
    f.text shouldBe " world"
  }
  it should "refuse completion after dispose" in {
    val f = new ClipboardFixture()
    f.select(0, 5)
    val pending = f.service.prepareCut().toOption.get
    f.session.dispose()
    pending.confirm(Right(())).isLeft shouldBe true
  }
