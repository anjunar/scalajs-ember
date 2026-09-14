package ember.editor.integration

import ember.editor.browser.*
import ember.editor.browsersupport.*
import ember.editor.clipboard.*
import ember.editor.core.*
import ember.editor.forms.*
import ember.editor.history.History
import ember.editor.image.*
import ember.editor.richtext.*
import ember.editor.link.*
import ember.editor.list.*
import ember.editor.code.*
import ember.editor.standard.*
import ember.editor.json.DocumentJson
import ember.editor.ui.DocumentView
import ui.core.render.{DomCursor, SsrCursor}
import ui.core.component.Runtime
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

@JSExportTopLevel("mediaFixtures")
object MediaFixtures:
  private def resolved(
      generator: NodeIdGenerator,
      field: EditorField,
      extra: Vector[Extension] = Vector.empty
  ) =
    ExtensionResolver
      .resolve(
        Vector(
          RichText(generator),
          new ClipboardExtension(generator),
          ImageExtension(generator),
          LinkExtension(generator),
          ListExtension(generator),
          CodeExtension(generator),
          FormFieldExtension(field)
        ) ++ extra
      )
      .toOption
      .get
  private def field(generator: NodeIdGenerator) =
    EditorFields.markdown("body", MarkdownSupports.everything(), generator)

  /** No-JS server path: submitted source is decoded before using the ordinary image command. */
  @JSExport def append(source: String, src: String, alt: String): String =
    val gen        = NodeIdGenerator.sequential("server")
    val f          = field(gen)
    val extensions = resolved(gen, f)
    val outcome    = for
      doc    <- f.codec.decode(source, extensions.schema, NodeId("root"))
      url    <- MediaUrlPolicy.default.parse(src)
      result <-
        val session = EditorSession.create(doc, extensions, extensions.sessionConfig()).toOption.get
        try
          session
            .update { tx =>
              // The server accepts the semantic position "append a paragraph", never DOM offsets.
              val p = gen.nextFor(tx.document)
              tx.insert(
                doc.rootId,
                tx.document.childrenOf(doc.rootId).size,
                ParagraphNode(p, Vector.empty)
              ): Unit
              tx.select(RangeSelection.caret(Point.childrenBefore(p, 0))): Unit
              // This Strict Markdown application uses the content-addressed URL as its
              // durable reference. A separate MediaId is not representable in CommonMark.
              tx.dispatch(
                ImageCommands.InsertImage,
                ImageNode(gen.nextFor(tx.document), MediaReference(url), alt)
              ): Unit
            }
            .flatMap(_ => f.codec.encode(session.document))
        finally session.dispose()
    yield result
    outcome.fold(error => throw new IllegalArgumentException(error.render), identity)

  @JSExport def renderSource(source: String): String =
    val gen        = NodeIdGenerator.sequential("ssr-media")
    val f          = field(gen)
    val extensions = resolved(gen, f)
    val doc        = Document.unsafe(
      extensions.schema,
      NodeId("root"),
      Vector(RootNode(NodeId("root"), Vector.empty))
    )
    val session = EditorSession.create(doc, extensions, extensions.sessionConfig()).toOption.get
    val binding = new EditorFormBinding(session, f)
    binding.enterSource()
    binding.editDraft(source)
    val view   = new EditorFieldView(binding, session, ImageSupport.views, "Inhalt")
    val cursor = new SsrCursor()
    Runtime.mount(view, cursor)
    val html = cursor.collectHtml()
    Runtime.unmount(view)
    session.dispose()
    html

  private var current: Option[Fixture] = None
  private class Fixture(host: dom.Element, fileInput: dom.HTMLInputElement, controlled: Boolean):
    val gen       = NodeIdGenerator.sequential("upload")
    val formField = field(gen)
    val history   = new History()
    val holder    = new CompositionHolder()
    val gate      = new Extension:
      val id                  = ExtensionId("media.fixture.gate")
      override def contribute =
        ExtensionContributions(preCommitRules = Vector(BrowserInputController.busyRule(holder)))
    val extensions = resolved(gen, formField, Vector(history, gate))
    val initial    = Document.unsafe(
      extensions.schema,
      NodeId("root"),
      Vector(
        RootNode(NodeId("root"), Vector(NodeId("p"))),
        ParagraphNode(NodeId("p"), Vector(NodeId("t"))),
        TextNode(NodeId("t"), "Hello world")
      )
    )
    val session = EditorSession.create(initial, extensions, extensions.sessionConfig()).toOption.get
    val binding = new EditorFormBinding(session, formField)
    val view    = DocumentView.mount(session, DomCursor.root(host), ImageSupport.views)
    val selection = SelectionPort.attachTo(session, view, host)
    val input     = BrowserInputController.attachTo(
      session,
      view,
      selection,
      EditorBindings.everything,
      EditorBindings.everythingKeyboard,
      semantics = Some(ImageSupport.everything)
    )
    holder.bind(input)
    HistoryBindings.groupCompositions(input, history)
    var requests = Vector.empty[Promise[MediaReference]]
    var tokens   = Vector.empty[MediaCancellation]
    var aborted  = 0
    var urls     = Vector.empty[String]
    var revoked  = Vector.empty[String]
    var errors   = Vector.empty[String]
    val service  = new MediaService[dom.File]:
      def upload(file: dom.File, token: MediaCancellation): Future[MediaReference] =
        val promise = Promise[MediaReference]()
        requests :+= promise
        tokens :+= token
        token.onCancel(() => aborted += 1)
        if !controlled then
          val xhr    = new dom.XMLHttpRequest()
          val cancel = token.onCancel(() => {
            xhr.abort(); promise.tryFailure(new IllegalStateException("aborted")); ()
          })
          xhr.open("POST", "/media/upload")
          xhr.onload = _ =>
            cancel.dispose()
            try
              if xhr.status != 200 then throw new IllegalArgumentException(xhr.responseText)
              val wire = js.JSON.parse(xhr.responseText)
              val url  = MediaUrlPolicy.default.parse(wire.src.asInstanceOf[String]).toOption.get
              promise.trySuccess(MediaReference(url)): Unit
            catch case NonFatal(error) => promise.tryFailure(error): Unit
          xhr.onerror = _ => {
            cancel.dispose(); promise.tryFailure(new IllegalStateException("network failure")); ()
          }
          xhr.upload.onprogress = event =>
            if event.lengthComputable then token.reportProgress(event.loaded / event.total)
          val body = new dom.FormData()
          body.append("file", file)
          xhr.send(body)
        promise.future
    val browserPreviews = BrowserMediaPreviews.in(selection.scope.window.get)
    val previews        = new MediaPreviews[dom.File]:
      def create(file: dom.File): Option[String] =
        val result = browserPreviews.create(file)
        urls ++= result
        result
      def revoke(url: String): Unit =
        revoked :+= url
        browserPreviews.revoke(url)
    val coordinator = new MediaCoordinator(
      session,
      service,
      gen,
      previews = previews,
      availability = () =>
        if binding.mode == FieldMode.Source then MediaAvailability.SourceBusy
        else if input.state != ControllerState.Ready then MediaAvailability.CompositionBusy
        else if input.mode != EditorMode.Editable then MediaAvailability.ReadOnly
        else MediaAvailability.Ready
    )
    val picker = new BrowserMediaPicker(
      fileInput,
      session,
      selection,
      coordinator,
      () =>
        selection.scope.ownerDocument
          .getElementById("media-alt")
          .asInstanceOf[dom.HTMLInputElement]
          .value,
      report = result => result.left.foreach(error => errors :+= error.message)
    )
    val codec = new ClipboardCodec(
      "standard/1",
      extensions.schema,
      StandardJsonSupport.everything(),
      ImageSupport.everything,
      StandardHtmlImport.everything(LinkUrlPolicy.default, MediaUrlPolicy.default)
    )
    val clipboard = new BrowserClipboardController(
      session,
      selection,
      input,
      codec,
      result => result.left.foreach(error => errors :+= error.message),
      picker.receive
    )
    val composition =
      input.onComposition(_ => if input.state == ControllerState.Ready then coordinator.resume())
    def dispose(): Unit =
      composition.dispose()
      clipboard.dispose()
      picker.dispose()
      coordinator.dispose()
      input.dispose()
      selection.dispose()
      view.dispose()
      session.dispose()
      holder.release()

  @JSExport def mount(
      host: dom.Element,
      input: dom.HTMLInputElement,
      controlled: Boolean = true
  ): Unit =
    dispose()
    current = Some(new Fixture(host, input, controlled))
  @JSExport def dispose(): Unit              = current.foreach(_.dispose())
  @JSExport def open(): Boolean              = current.get.picker.open().isRight
  @JSExport def select(a: Int, b: Int): Unit =
    val f = current.get
    f.session.update(
      _.select(RangeSelection(Point.textBefore(NodeId("t"), a), Point.textBefore(NodeId("t"), b)))
    )
    f.selection.sync(WriteIntent.Explicit)
  @JSExport def complete(
      index: Int,
      src: String = "/media/assets/fixture.png",
      keepId: Boolean = false
  ): Boolean =
    current.get
      .requests(index)
      .trySuccess(
        MediaReference(
          MediaUrlPolicy.default.copy(schemes = Set("http", "https")).parse(src).toOption.get,
          if keepId then Some(MediaId("fixture")) else None
        )
      )
  @JSExport def fail(index: Int): Boolean =
    current.get.requests(index).tryFailure(new IllegalStateException("upload rejected"))
  @JSExport def progress(index: Int, value: Double): Unit =
    current.get.tokens(index).reportProgress(value)
  @JSExport def cancel(id: Double): Unit        = current.get.coordinator.cancel(id.toLong)
  @JSExport def count(): Int                    = current.get.requests.size
  @JSExport def aborts(): Int                   = current.get.aborted
  @JSExport def revoked(): Int                  = current.get.revoked.size
  @JSExport def previews(): js.Array[String]    = js.Array(current.get.urls*)
  @JSExport def statuses(): js.Array[js.Object] = js.Array(
    current.get.coordinator.statuses.map(s =>
      js.Dynamic.literal(
        id = s.id.toDouble,
        phase = s.phase.toString,
        progress = s.progress,
        preview = s.preview.orNull
      )
    )*
  )
  @JSExport def value(): String = current.get.binding.submitValue
  @JSExport def json(): String  = DocumentJson
    .encodeToString(current.get.session.document, StandardJsonSupport.everything())
    .toOption
    .get
  @JSExport def text(): String = current.get.session.document.inDocumentOrder.collect {
    case t: TextNode => t.text
  }.mkString
  @JSExport def imageCount(): Int =
    current.get.session.document.nodes.count(_.isInstanceOf[ImageNode])
  @JSExport def errors(): String = current.get.errors.mkString("|")
  @JSExport def undo(): Boolean  = current.get.history.undo().toOption.contains(true)
  @JSExport def edit(): Unit     =
    current.get.session.update(_.spliceText(NodeId("t"), 0, 0, "X")): Unit
  @JSExport def removeTarget(): Unit = current.get.session.update(_.remove(NodeId("p"))): Unit
  @JSExport def source(value: String): Unit =
    current.get.binding.enterSource()
    current.get.binding.editDraft(value)
  @JSExport def leaveSource(apply: Boolean): Unit =
    if apply then current.get.binding.applyDraft(): Unit else current.get.binding.discardDraft()
    current.get.coordinator.resume()
  @JSExport def external(src: String, alt: String): Boolean =
    val f = current.get
    (for
      url    <- MediaUrlPolicy.default.parse(src)
      target <- f.coordinator.capture(f.session.selection.get.asInstanceOf[RangeSelection])
      id     <- f.coordinator.insert(MediaReference(url), target, alt)
    yield id).isRight
