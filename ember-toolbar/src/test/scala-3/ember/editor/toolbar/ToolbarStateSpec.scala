package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.link.*
import ember.editor.image.*
import ember.editor.history.*
import ember.editor.clipboard.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

private class ToolbarFixture:
  val gen        = NodeIdGenerator.sequential("toolbar")
  val history    = new History()
  val extensions = ExtensionResolver
    .resolve(
      Vector(
        RichText(gen),
        LinkExtension(gen),
        ImageExtension(gen),
        new ClipboardExtension(gen),
        history
      )
    )
    .toOption
    .get
  val initial = Document.unsafe(
    extensions.schema,
    NodeId("root"),
    Vector(
      RootNode(NodeId("root"), Vector(NodeId("p"))),
      ParagraphNode(NodeId("p"), Vector(NodeId("t"))),
      TextNode(NodeId("t"), "Hello world")
    )
  )
  val session  = EditorSession.create(initial, extensions, extensions.sessionConfig()).toOption.get
  var editable = true
  val service  = new EditorDialogService(session, gen, () => editable)
  def select(a: Int = 0, b: Int = 5): Unit =
    session.update(
      _.select(RangeSelection(Point.textBefore(NodeId("t"), a), Point.textBefore(NodeId("t"), b)))
    ): Unit
  def capture() = service.capture().toOption.get
  def text      = session.document.inDocumentOrder.collect { case t: TextNode => t.text }.mkString
  def edit(): Unit = session.update(_.spliceText(NodeId("t"), 0, 0, "x")): Unit

class ToolbarStateSpec extends AnyFlatSpec with Matchers:
  "Toolbar state" should "read explicit stored marks at a caret" in {
    val f = new ToolbarFixture
    f.select(2, 2)
    f.session.dispatch(RichText.ToggleMark, StandardMarks.Strong)
    ToolbarState.mark(f.session.state, StandardMarks.Strong, true) shouldBe CommandState(
      true,
      Some("true")
    )
    ToolbarState.mark(f.session.state, StandardMarks.Strong, false).enabled shouldBe false
  }
  it should "report mixed range marks and no selection" in {
    val f = new ToolbarFixture
    ToolbarState.mark(f.session.state, StandardMarks.Strong, true).enabled shouldBe false
    f.select()
    f.session.dispatch(RichText.ToggleMark, StandardMarks.Strong)
    val runs = f.session.document.inDocumentOrder.collect { case t: TextNode => t }.toVector
    f.session.update(
      _.select(
        RangeSelection(
          Point.textBefore(runs.head.id, 0),
          Point.textBefore(runs.last.id, runs.last.text.length)
        )
      )
    )
    ToolbarState.mark(f.session.state, StandardMarks.Strong, true).pressed shouldBe Some("mixed")
  }
  "Dialog targets" should "map both backward endpoints through an edit" in {
    val f = new ToolbarFixture
    f.select(5, 0)
    val target = f.capture()
    f.edit()
    f.service.setLink(target, "https://example.org", "Title").isRight shouldBe true
    f.text shouldBe "xHello world"
    f.session.document.nodes.collect { case link: LinkNode => link }.size shouldBe 1
    f.service.setLink(target, "/again", "").isLeft shouldBe true
  }
  it should "reject a deleted target" in {
    val f = new ToolbarFixture
    f.select()
    val target = f.capture()
    f.session.update(_.remove(NodeId("p")))
    val before = f.session.document
    f.service.setLink(target, "/link", "").isLeft shouldBe true
    f.session.document shouldBe before
  }
  it should "reject expired mapping retention" in {
    val f = new ToolbarFixture
    f.select()
    val target = f.capture()
    (1 to 80).foreach(_ => f.edit())
    f.service.resolve(target).isLeft shouldBe true
  }
  it should "invalidate a same-id document replacement" in {
    val f = new ToolbarFixture
    f.select()
    f.edit()
    val target = f.capture()
    f.session.update(_.restore(f.initial, None))
    f.service.resolve(target).isLeft shouldBe true
  }
  it should "invalidate on history restore" in {
    val f = new ToolbarFixture
    f.select()
    f.edit()
    val target = f.capture()
    f.session.dispatch(HistoryCommands.Undo)
    f.service.resolve(target).isLeft shouldBe true
  }
  it should "reject foreign, superseded and cancelled targets" in {
    val f     = new ToolbarFixture
    val other = new ToolbarFixture
    f.select()
    val first = f.capture()
    other.service.resolve(first).isLeft shouldBe true
    val second = f.capture()
    f.service.resolve(first).isLeft shouldBe true
    f.service.cancel(second)
    f.service.resolve(second).isLeft shouldBe true
  }
  it should "reject edits after readonly transition without consuming the target" in {
    val f = new ToolbarFixture
    f.select()
    val target = f.capture()
    f.editable = false
    f.service.setLink(target, "/link", "").isLeft shouldBe true
    f.text shouldBe "Hello world"
    f.service.capture().isLeft shouldBe true
    f.editable = true
    f.service.setLink(target, "/link", "").isRight shouldBe true
  }
  it should "validate URLs before replacing any text and allow correcting an error" in {
    val f = new ToolbarFixture
    f.select()
    val target = f.capture()
    f.service.insertImage(target, "javascript:bad", "").isLeft shouldBe true
    f.text shouldBe "Hello world"
    f.service.insertImage(target, "/image.png", "").isRight shouldBe true
    f.text shouldBe " world"
    f.session.document.nodes.collect { case i: ImageNode => i.alt }.toVector shouldBe Vector("")
    f.session.dispatch(HistoryCommands.Undo)
    f.text shouldBe "Hello world"
  }
  it should "keep failed commands atomic" in {
    val f = new ToolbarFixture
    f.select(2, 2)
    val target = f.capture()
    val before = f.session.state
    f.service.setLink(target, "/link", "").isLeft shouldBe true
    f.session.state shouldBe before
    f.service.resolve(target).isRight shouldBe true
  }
  it should "edit an image alt without changing its identity or durable metadata" in {
    val f = new ToolbarFixture
    f.select(2, 2)
    val image = ImageNode(
      NodeId("image"),
      MediaReference(
        MediaUrlPolicy.default.parse("/image.png").toOption.get,
        Some(MediaId("durable"))
      ),
      "before"
    )
    f.session.dispatch(ImageCommands.InsertImage, image)
    f.session.update(_.select(NodeSelection(Set(image.id))))
    val target = f.capture()
    f.service.insertImage(target, "/image.png", "").isRight shouldBe true
    f.session.document.node(image.id) shouldBe Some(image.copy(alt = ""))
    f.session.dispatch(HistoryCommands.Undo)
    f.session.document.node(image.id) shouldBe Some(image)
  }
  it should "reject a removed image even when its parent boundary survives" in {
    val f = new ToolbarFixture
    f.select(2, 2)
    f.service.insertImage(f.capture(), "/image.png", "").isRight shouldBe true
    val image = f.session.document.nodes.collectFirst { case image: ImageNode => image }.get
    f.session.update(_.select(NodeSelection(Set(image.id))))
    val target = f.capture()
    f.session.update(_.remove(image.id))
    f.service.insertImage(target, "/image.png", "changed").isLeft shouldBe true
  }
  it should "dispose without changing content" in {
    val f = new ToolbarFixture
    f.select()
    val target = f.capture()
    f.service.dispose()
    f.service.resolve(target).isLeft shouldBe true
    f.service.capture().isLeft shouldBe true
    f.text shouldBe "Hello world"
  }
