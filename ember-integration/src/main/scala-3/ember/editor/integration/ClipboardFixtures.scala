package ember.editor.integration

import ember.editor.clipboard.*
import ember.editor.browser.*
import ember.editor.browsersupport.*
import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.history.History
import ember.editor.link.*
import ember.editor.list.*
import ember.editor.code.*
import ember.editor.image.*
import ember.editor.standard.*
import ember.editor.ui.{DocumentView, ViewSupport}
import ui.core.render.DomCursor
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** Browser fixtures use the actual clipboard/drop event adapters. Exported model reads are
  * assertions; copy/cut/paste itself is driven through browser events.
  */
@JSExportTopLevel("clipboardFixtures")
object ClipboardFixtures:
  private var mounted = Map.empty[String, Fixture]
  private class Fixture(host: dom.Element, key: String):
    val generator = NodeIdGenerator.sequential(s"$key-g")
    val history   = new History()
    val holder    = new CompositionHolder()
    val gate      = new Extension:
      val id                  = ExtensionId("clipboard.fixture.gate")
      override def contribute =
        ExtensionContributions(preCommitRules = Vector(BrowserInputController.busyRule(holder)))
    val resolved = ExtensionResolver
      .resolve(
        Vector(
          RichText(generator),
          new ClipboardExtension(generator),
          history,
          gate,
          LinkExtension(generator),
          ListExtension(generator),
          CodeExtension(generator),
          ImageExtension(generator, MediaUrlPolicy.default),
          WidgetExtension
        )
      )
      .toOption
      .get
    val session = EditorSession
      .create(
        Document.unsafe(
          resolved.schema,
          NodeId("root"),
          Vector(
            RootNode(NodeId("root"), Vector(NodeId("p0"), NodeId("p1"), NodeId("p2"))),
            ParagraphNode(NodeId("p0"), Vector(NodeId("t0"), NodeId("t1"))),
            TextNode(NodeId("t0"), "Hello "),
            TextNode(NodeId("t1"), "world", MarkSet.of(StandardMarks.Strong)),
            ParagraphNode(NodeId("p1"), Vector(NodeId("t2"))),
            TextNode(NodeId("t2"), "Second line"),
            ParagraphNode(NodeId("p2"), Vector(NodeId("widget"))),
            WidgetNode(NodeId("widget"), "Native control")
          )
        ),
        resolved,
        resolved.sessionConfig()
      )
      .toOption
      .get
    val codec = new ClipboardCodec(
      "standard/1",
      resolved.schema,
      StandardJsonSupport.everything(),
      ImageSupport.everything,
      StandardHtmlImport.everything(LinkUrlPolicy.default, MediaUrlPolicy.default)
    )
    val view = DocumentView.mount(
      session,
      DomCursor.root(host),
      ViewSupport.of(WidgetView) ++ ImageSupport.views
    )
    val port  = SelectionPort.attachTo(session, view, host)
    val input = BrowserInputController.attachTo(
      session,
      view,
      port,
      EditorBindings.everything,
      EditorBindings.everythingKeyboard,
      semantics = Some(ImageSupport.everything)
    )
    holder.bind(input)
    HistoryBindings.groupCompositions(input, history)
    var outcomes  = Vector.empty[String]
    var fileCount = 0
    val clipboard = new BrowserClipboardController(
      session,
      port,
      input,
      codec,
      result =>
        outcomes :+= result
          .fold(error => s"error:${error.message}", notes => s"ok:${notes.mkString(";")}"),
      intent => { fileCount += intent.files.length; Right(()) }
    )
    def dispose(): Unit =
      clipboard.dispose()
      input.dispose()
      port.dispose()
      view.dispose()
      session.dispose()
      holder.release()

  @JSExport def mount(host: dom.Element, key: String = "a"): Unit =
    mounted.get(key).foreach(_.dispose())
    mounted += key -> new Fixture(host, key)
  @JSExport def dispose(): Unit =
    mounted.values.foreach(_.dispose())
    mounted = Map.empty
  @JSExport def text(key: String = "a"): String =
    mounted(key).codec.text(mounted(key).session.document)
  @JSExport def log(key: String = "a"): String    = mounted(key).outcomes.mkString("|")
  @JSExport def fileCount(key: String = "a"): Int = mounted(key).fileCount
  @JSExport def state(key: String = "a"): String  = mounted(key).input.state.toString
  @JSExport def readonly(value: Boolean, key: String = "a"): Unit =
    mounted(key).input.setMode(if value then EditorMode.ReadOnly else EditorMode.Editable)
  @JSExport def select(a: String, from: Int, b: String, until: Int, key: String = "a"): Unit =
    val f = mounted(key)
    f.session.update(
      _.select(
        RangeSelection(Point.textBefore(NodeId(a), from), Point.textBefore(NodeId(b), until))
      ): Unit
    )
    f.port.sync(WriteIntent.Explicit)
  @JSExport def selectNode(id: String, key: String = "a"): Unit =
    val f = mounted(key)
    f.session.update(_.select(NodeSelection(Set(NodeId(id)))): Unit)
    f.port.sync(WriteIntent.Explicit)
  @JSExport def undo(key: String = "a"): Boolean =
    mounted(key).history.undo().toOption.contains(true)
  @JSExport def edit(id: String, offset: Int, value: String, key: String = "a"): Boolean =
    mounted(key).session.update(_.spliceText(NodeId(id), offset, 0, value): Unit).isRight
  @JSExport def rootChildren(key: String = "a"): js.Array[String] =
    js.Array(mounted(key).session.document.childrenOf(NodeId("root")).map(_.value)*)
