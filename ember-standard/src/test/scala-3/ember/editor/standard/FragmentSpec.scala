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
