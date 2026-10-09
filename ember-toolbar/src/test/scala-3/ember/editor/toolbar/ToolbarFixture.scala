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
