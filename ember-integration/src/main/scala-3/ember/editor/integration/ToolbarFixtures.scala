package ember.editor.integration

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.link.*
import ember.editor.image.*
import ember.editor.history.*
import ember.editor.clipboard.*
import ember.editor.browser.*
import ember.editor.browsersupport.*
import ember.editor.toolbar.*
import ember.editor.forms.*
import ember.editor.standard.*
import ember.editor.ui.DocumentView
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import ui.core.component.Runtime
import ui.core.render.DomCursor

/** A separate, usable P27 page, also driven by the browser accessibility tests. */
@JSExportTopLevel("toolbarFixtures")
object ToolbarFixtures:
  private var current = Option.empty[Fixture]
  private class Fixture(
      editor: dom.Element,
      bar: dom.Element,
      dialogs: dom.Element,
      files: dom.HTMLInputElement
  ):
    val gen     = NodeIdGenerator.sequential("toolbar")
    val history = new History()
    val holder  = new CompositionHolder()
    val gate    = new Extension:
      val id                  = ExtensionId("toolbar.fixture.gate")
      override def contribute =
        ExtensionContributions(preCommitRules = Vector(BrowserInputController.busyRule(holder)))
    val extensions = ExtensionResolver
      .resolve(
        Vector(
          RichText(gen),
          LinkExtension(gen),
          ImageExtension(gen),
          new ClipboardExtension(gen),
          history,
          gate
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
    val session = EditorSession.create(initial, extensions, extensions.sessionConfig()).toOption.get
    val view    = DocumentView.mount(session, DomCursor.root(editor), ImageSupport.views)
    val selection = SelectionPort.attachTo(session, view, editor)
    val input     = BrowserInputController.attachTo(
      session,
      view,
      selection,
      EditorBindings.everything,
      EditorBindings.everythingKeyboard,
      semantics = Some(ImageSupport.everything)
    )
    holder.bind(input)
    val compositionHistory = HistoryBindings.groupCompositions(input, history)
    def editable = input.mode == EditorMode.Editable && input.state == ControllerState.Ready
    val service  = new EditorDialogService(session, gen, () => editable)
    var toolbar: EditorToolbar          = null
    def announce(message: String): Unit = if toolbar != null then toolbar.announce(message)
    val host   = new EditorDialogHost(service, selection, dialogs, announce)
    val link   = new LinkDialog(service, host)
    val upload = new MediaService[dom.File]:
      def upload(file: dom.File, token: MediaCancellation): Future[MediaReference] =
        val result = Promise[MediaReference]()
        val xhr    = new dom.XMLHttpRequest()
        val cancel = token.onCancel(() => {
          xhr.abort(); result.tryFailure(new IllegalStateException("Upload abgebrochen")); ()
        })
        xhr.open("POST", "/media/upload")
        xhr.onload = _ =>
          cancel.dispose()
          if xhr.status == 200 then
            val src = js.JSON.parse(xhr.responseText).src.asInstanceOf[String]
            MediaUrlPolicy.default
              .parse(src)
              .fold(
                error => result.tryFailure(new IllegalArgumentException(error.message)),
                url => result.trySuccess(MediaReference(url))
              ): Unit
          else result.tryFailure(new IllegalArgumentException("Upload fehlgeschlagen")): Unit
        xhr.onerror = _ => {
          cancel.dispose(); result.tryFailure(new IllegalStateException("Netzwerkfehler")); ()
        }
        xhr.upload.onprogress = event =>
          if event.lengthComputable then token.reportProgress(event.loaded / event.total)
        val body = new dom.FormData()
        body.append("file", file)
        xhr.send(body)
        result.future
    val media = new MediaCoordinator(
      session,
      upload,
      gen,
      availability = () => if editable then MediaAvailability.Ready else MediaAvailability.ReadOnly,
      previews = BrowserMediaPreviews.in(selection.scope.window.get),
      changed = statuses =>
        statuses.lastOption.foreach { status =>
          status.phase match
            case MediaPhase.Uploading         => announce("Bild wird hochgeladen …")
            case MediaPhase.Inserted(_)       => announce("Bild eingefügt.")
            case MediaPhase.Failed(reason)    => announce(reason)
            case MediaPhase.Discarded(reason) => announce(reason)
            case _                            => ()
        }
    )
    var pendingFile                               = Option.empty[(MediaTarget, String)]
    val fileChange: js.Function1[dom.Event, Unit] = _ =>
      val pending = pendingFile
      pendingFile = None
      pending.foreach { (target, alt) =>
        if files.files != null && files.files.length > 0 then
          val file = files.files(0)
          MediaFilePolicy()
            .validate(Vector(file))
            .flatMap(_ => media.upload(file, target, alt))
            .left
            .foreach(error => announce(error.message))
      }
      files.value = ""
    files.addEventListener("change", fileChange)
    val image = new ImageDialog(
      service,
      host,
      Some((target, alt) =>
        for
          range       <- service.resolve(target)
          mediaTarget <- media.capture(range)
        yield
          pendingFile = Some(mediaTarget -> alt)
          files.click()
      )
    )
    def enabled = CommandState(editable && session.selection.exists(_.isInstanceOf[RangeSelection]))
    val actions = Vector(
      ToolbarAction.command(
        "bold",
        "Fett",
        session,
        RichText.ToggleMark,
        StandardMarks.Strong,
        () => ToolbarState.mark(session.state, StandardMarks.Strong, editable)
      ),
      ToolbarAction.command(
        "italic",
        "Kursiv",
        session,
        RichText.ToggleMark,
        StandardMarks.Emphasis,
        () => ToolbarState.mark(session.state, StandardMarks.Emphasis, editable)
      ),
      ToolbarAction.command(
        "undo",
        "Rückgängig",
        session,
        HistoryCommands.Undo,
        (),
        () => CommandState(editable && history.canUndo)
      ),
      ToolbarAction.command(
        "redo",
        "Wiederholen",
        session,
        HistoryCommands.Redo,
        (),
        () => CommandState(editable && history.canRedo)
      ),
      ToolbarAction(
        "link",
        "Link",
        () =>
          enabled.copy(enabled = enabled.enabled && session.selection.exists {
            case r: RangeSelection => !r.isCollapsed || Links.isLinked(session.document, r.focus)
            case _                 => false
          }),
        () => link.open()
      ),
      ToolbarAction(
        "image",
        "Bild",
        () => CommandState(editable && (enabled.enabled || service.selectedImage.nonEmpty)),
        () => image.open()
      )
    )
    toolbar = new EditorToolbar(session, selection, actions)
    Runtime.mount(toolbar, DomCursor.root(bar))
    val composition     = input.onComposition(_ => toolbar.refresh())
    def dispose(): Unit =
      host.dispose()
      service.dispose()
      files.removeEventListener("change", fileChange)
      pendingFile = None
      media.dispose()
      composition.dispose()
      Runtime.unmount(toolbar)
      input.dispose()
      selection.dispose()
      view.dispose()
      session.dispose()
      holder.release()

  @JSExport def mount(
      editor: dom.Element,
      bar: dom.Element,
      dialogs: dom.Element,
      files: dom.HTMLInputElement
  ): Unit =
    dispose()
    current = Some(new Fixture(editor, bar, dialogs, files))
  @JSExport def dispose(): Unit =
    current.foreach(_.dispose())
    current = None
  @JSExport def select(a: Int, b: Int): Unit =
    val f    = current.get
    val runs = f.session.document.inDocumentOrder.collect { case t: TextNode => t }.toVector
    f.session.update(
      _.select(RangeSelection(Point.textBefore(runs.head.id, a), Point.textBefore(runs.last.id, b)))
    )
    f.selection.sync(WriteIntent.Explicit): Unit
  @JSExport def readonly(value: Boolean): Unit =
    val f = current.get
    f.input.setMode(if value then EditorMode.ReadOnly else EditorMode.Editable)
    f.toolbar.refresh()
  @JSExport def edit(count: Int = 1): Unit =
    val f = current.get
    (1 to count).foreach(_ => f.session.update(_.spliceText(NodeId("t"), 0, 0, "X")))
  @JSExport def removeTarget(): Unit = current.get.session.update(_.remove(NodeId("p"))): Unit
  @JSExport def selectImage(): Unit  =
    val f     = current.get
    val image = f.session.document.nodes.collectFirst { case image: ImageNode => image }.get
    f.session.update(_.select(NodeSelection(Set(image.id))))
    f.selection.sync(WriteIntent.Explicit): Unit
  @JSExport def replaceDocument(): Unit =
    current.get.session.update(_.restore(current.get.initial, None)): Unit
  @JSExport def text(): String = current.get.session.document.inDocumentOrder.collect {
    case t: TextNode => t.text
  }.mkString
  @JSExport def undo(): Boolean     = current.get.history.undo().toOption.contains(true)
  @JSExport def openLink(): Boolean = current.get.link.open().isRight
  @JSExport def invalidate(): Unit  = current.get.service.invalidate()
