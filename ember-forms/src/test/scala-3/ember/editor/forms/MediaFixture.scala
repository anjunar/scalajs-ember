package ember.editor.forms

import ember.editor.core.*
import ember.editor.clipboard.*
import ember.editor.image.*
import ember.editor.richtext.*
import ember.editor.history.History
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.{ExecutionContext, Future, Promise}

private final class MediaFixture(using ExecutionContext):
  val generator = NodeIdGenerator.sequential("m")
  val history   = new History()
  val resolved  = ExtensionResolver
    .resolve(
      Vector(
        RichText(generator),
        new ClipboardExtension(generator),
        ImageExtension(generator),
        history
      )
    )
    .toOption
    .get
  val document = Document.unsafe(
    resolved.schema,
    NodeId("root"),
    Vector(
      RootNode(NodeId("root"), Vector(NodeId("p"))),
      ParagraphNode(NodeId("p"), Vector(NodeId("t"))),
      TextNode(NodeId("t"), "Hello")
    )
  )
  val session  = EditorSession.create(document, resolved, resolved.sessionConfig()).toOption.get
  var mode     = MediaAvailability.Ready
  var requests = Vector.empty[(Promise[MediaReference], MediaCancellation)]
  var revoked  = Vector.empty[String]
  var watchers = Vector.empty[Vector[MediaStatus] => Unit]
  val service  = new MediaService[String]:
    def upload(file: String, token: MediaCancellation): Future[MediaReference] =
      if file == "throw" then throw new IllegalStateException("sync failure")
      val p = Promise[MediaReference]()
      requests :+= p -> token
      p.future
  val previews = new MediaPreviews[String]:
    def create(file: String)      = Some(s"blob:$file")
    def revoke(url: String): Unit = revoked :+= url
  val coordinator = new MediaCoordinator(
    session,
    service,
    generator,
    availability = () => mode,
    previews = previews,
    changed = statuses => watchers.foreach(_(statuses)),
    maxPending = 2,
    retainedResults = 3
  )
  def target(a: Int = 2, b: Int = 2) = coordinator
    .capture(RangeSelection(Point.textBefore(NodeId("t"), a), Point.textBefore(NodeId("t"), b)))
    .toOption
    .get
  def upload(file: String = "a", a: Int = 2, b: Int = 2): Long =
    coordinator.upload(file, target(a, b), "description").toOption.get
  def reference(path: String = "/assets/image.png") =
    MediaReference(MediaUrlPolicy.default.parse(path).toOption.get, Some(MediaId("fixture")))
  def success(index: Int = 0): Unit = requests(index)._1.success(reference()): Unit
  def state(id: Long)               = coordinator.statuses.find(_.id == id).get
  def awaitPhase(id: Long)(predicate: MediaPhase => Boolean): Future[MediaStatus] =
    val promise                            = Promise[MediaStatus]()
    val check: Vector[MediaStatus] => Unit = statuses =>
      statuses.find(s => s.id == id && predicate(s.phase)).foreach(promise.trySuccess)
    watchers :+= check
    check(coordinator.statuses)
    promise.future
  def done(id: Long): Future[MediaStatus] = awaitPhase(id) {
    case MediaPhase.Uploading | MediaPhase.Waiting(_) => false
    case _                                            => true
  }
  def images = session.document.nodes.collect { case image: ImageNode => image }.toVector
  def text = session.document.inDocumentOrder.collect { case text: TextNode => text.text }.mkString
