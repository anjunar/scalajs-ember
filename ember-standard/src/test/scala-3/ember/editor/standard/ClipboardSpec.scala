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

private[standard] final class ClipboardFixture(
    source: String = "<p>Hello world</p>",
    rules: Vector[PreCommitRule] = Vector.empty,
    val generator: NodeIdGenerator = NodeIdGenerator.sequential("g")
):
  val history    = new History()
  val extensions = ExtensionResolver
    .resolve(
      Vector(
        RichText(generator),
        history,
        new ClipboardExtension(generator),
        LinkExtension(generator),
        ListExtension(generator),
        CodeExtension(generator),
        ImageExtension(generator, MediaUrlPolicy.default)
      )
    )
    .toOption
    .get
  val schema   = extensions.schema
  val html     = StandardHtmlImport.everything(LinkUrlPolicy.default, MediaUrlPolicy.default)
  val document =
    HtmlImport.imported(source, schema, html, generator, NodeId("root")).toOption.get.document
  val session = EditorSession
    .create(document, extensions, extensions.sessionConfig().copy(preCommitRules = rules))
    .toOption
    .get
  val codec = new ClipboardCodec(
    "standard/1",
    schema,
    StandardJsonSupport.everything(),
    ImageSupport.everything,
    html
  )
  val service                = new ClipboardService(session, codec)
  def runs: Vector[TextNode] = session.document.inDocumentOrder.collect { case t: TextNode =>
    t
  }.toVector
  def text: String                        = codec.text(session.document)
  def select(from: Int, until: Int): Unit = session
    .update(
      _.select(
        RangeSelection(Point.textBefore(runs.head.id, from), Point.textBefore(runs.head.id, until))
      ): Unit
    )
    .toOption
    .get: Unit
  def caret(offset: Int): Unit                                       = select(offset, offset)
  def paste(value: String): Either[ClipboardError, ClipboardDecoded] =
    service.paste(ClipboardData(Map(ClipboardMime.Text -> value)))

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

final class FragmentSpec extends AnyFlatSpec with Matchers:
  "A fragment" should "preserve cropped text, marks and backward selection order" in {
    val f         = new ClipboardFixture("<p><strong>Hello</strong> world</p>")
    val selection =
      RangeSelection(Point.textBefore(f.runs.last.id, 3), Point.textBefore(f.runs.head.id, 2))
    val slice = DocumentFragment.extract(f.document, selection).toOption.get
    f.codec.text(slice.document) shouldBe "llo wo"
    slice.openStart shouldBe 1
    slice.openEnd shouldBe 1
    slice.document.inDocumentOrder.collect { case t: TextNode => t }.next().marks shouldBe MarkSet
      .of(StandardMarks.Strong)
  }
  it should "keep links around an image and its alt text in HTML" in {
    val f     = new ClipboardFixture("<p><a href='/x'><img src='/image.png' alt='example'></a></p>")
    val image = f.document.inDocumentOrder.collectFirst { case i: ImageNode => i }.get
    val fragment = DocumentFragment.extract(f.document, NodeSelection(Set(image.id))).toOption.get
    val encoded  = f.codec.encode(fragment).toOption.get
    encoded.formats(ClipboardMime.Html) should include("<a href=\"/x\"")
    encoded.formats(ClipboardMime.Html) should include("alt=\"example\"")
    f.codec.decode(encoded).isRight shouldBe true
  }
  it should "keep the selected middle list item and partial endpoints" in {
    val f        = new ClipboardFixture("<ul><li>ABC</li><li>DEF</li><li>GHI</li></ul>")
    val fragment = DocumentFragment
      .extract(
        f.document,
        RangeSelection(Point.textBefore(f.runs.head.id, 1), Point.textBefore(f.runs.last.id, 2))
      )
      .toOption
      .get
    f.codec.text(fragment.document) shouldBe "BC\nDEF\nGH"
    fragment.document.inDocumentOrder.count(_.isInstanceOf[ListItemNode]) shouldBe 3
    f.codec.decode(f.codec.encode(fragment).toOption.get).isRight shouldBe true
  }
  it should "reject invalid open depths" in {
    val f = new ClipboardFixture()
    DocumentFragment.create(f.document, 9, 0).isLeft shouldBe true
    DocumentFragment.create(f.document, -1, 0).isLeft shouldBe true
  }
  it should "paste a selected linked image inline without losing its wrapper" in {
    val source =
      new ClipboardFixture("<p><a href='/x'><img src='/image.png' alt='example'></a></p>")
    val image    = source.document.inDocumentOrder.collectFirst { case i: ImageNode => i }.get
    val fragment =
      DocumentFragment.extract(source.document, NodeSelection(Set(image.id))).toOption.get
    val target = new ClipboardFixture()
    target.caret(5)
    target.service.paste(source.codec.encode(fragment).toOption.get).isRight shouldBe true
    target.text shouldBe "Hello\uFFFC world"
    target.session.document.childrenOf(target.document.rootId) should have size 1
    target.session.document.inDocumentOrder.count(_.isInstanceOf[LinkNode]) shouldBe 1
  }
  "Structured paste" should "join open list boundaries inside a matching list" in {
    val source   = new ClipboardFixture("<ul><li>ABC</li><li>DEF</li></ul>")
    val fragment = DocumentFragment
      .extract(
        source.document,
        RangeSelection(
          Point.textBefore(source.runs.head.id, 1),
          Point.textBefore(source.runs.last.id, 2)
        )
      )
      .toOption
      .get
    val target = new ClipboardFixture("<ul><li>hello</li></ul>")
    target.caret(2)
    target.service.paste(source.codec.encode(fragment).toOption.get).isRight shouldBe true
    target.text shouldBe "heBC\nDEllo"
    target.session.document.inDocumentOrder.count(_.isInstanceOf[ListNode]) shouldBe 1
  }
  "Internal move" should "preserve IDs for whole nodes" in {
    val f     = new ClipboardFixture("<p>A</p><p>B</p><p>C</p>")
    val root  = f.document.rootId
    val first = f.document.childrenOf(root).head
    val ids   = f.document.subtreeOf(first).toSet
    f.session
      .dispatch(ClipboardCommands.meta("drag-move"))(
        ClipboardCommands.Move,
        FragmentMove(NodeSelection(Set(first)), Point.childrenBefore(root, 3))
      )
      .isRight shouldBe true
    f.text shouldBe "B\nC\nA"
    f.session.document.subtreeOf(first).toSet shouldBe ids
  }
  it should "map a partial-text target after removing the source" in {
    val f   = new ClipboardFixture("<p>ABC DEF</p>")
    val run = f.runs.head.id
    f.session
      .dispatch(ClipboardCommands.meta("drag-move"))(
        ClipboardCommands.Move,
        FragmentMove(
          RangeSelection(Point.textBefore(run, 0), Point.textBefore(run, 3)),
          Point.textBefore(run, 7)
        )
      )
      .isRight shouldBe true
    f.text shouldBe " DEFABC"
  }
  it should "preserve a whole block at a text destination" in {
    val f       = new ClipboardFixture("<p>A</p><p>BC</p>")
    val first   = f.document.childrenOf(f.document.rootId).head
    val subtree = f.document.subtreeOf(first).toSet
    f.session
      .dispatch(
        ClipboardCommands.Move,
        FragmentMove(NodeSelection(Set(first)), Point.textBefore(f.runs.last.id, 1))
      )
      .isRight shouldBe true
    f.text shouldBe "B\nA\nC"
    f.session.document.subtreeOf(first).toSet shouldBe subtree
  }
  it should "preserve an image ID when moving into a text run" in {
    val f     = new ClipboardFixture("<p><img src='/image.png'>ABC</p>")
    val image = f.document.inDocumentOrder.collectFirst { case i: ImageNode => i }.get
    f.session
      .dispatch(
        ClipboardCommands.Move,
        FragmentMove(NodeSelection(Set(image.id)), Point.textBefore(f.runs.head.id, 2))
      )
      .isRight shouldBe true
    f.text shouldBe "AB\uFFFCC"
    f.session.document.node(image.id) shouldBe Some(image)
  }
  it should "reject an invalid destination atomically" in {
    val f      = new ClipboardFixture()
    val before = f.session.state
    f.session
      .dispatch(
        ClipboardCommands.Move,
        FragmentMove(NodeSelection(Set(f.runs.head.id)), Point.textBefore(NodeId("missing"), 1))
      )
      .isLeft shouldBe true
    f.session.state shouldBe before
  }
  it should "do nothing for a destination inside the moved range" in {
    val f      = new ClipboardFixture()
    val run    = f.runs.head.id
    val before = f.session.document
    f.session
      .dispatch(ClipboardCommands.meta("drag-move"))(
        ClipboardCommands.Move,
        FragmentMove(
          RangeSelection(Point.textBefore(run, 0), Point.textBefore(run, 5)),
          Point.textBefore(run, 3)
        )
      )
      .isRight shouldBe true
    f.session.document shouldBe before
  }
