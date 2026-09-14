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

final class MediaCoordinatorSpec extends AsyncFlatSpec with Matchers:
  "An upload" should "retain text until success and create a separate undo step" in {
    val f  = new MediaFixture
    val id = f.upload(a = 1, b = 4)
    f.text shouldBe "Hello"
    f.images shouldBe empty
    f.requests.head._2.reportProgress(.4)
    f.state(id).progress shouldBe .4
    f.success()
    f.done(id).map { status =>
      status.phase shouldBe MediaPhase.Inserted(f.images.head.id)
      status.progress shouldBe 1.0
      f.requests.head._2.isCancelled shouldBe false
      var lateAbort = false
      f.requests.head._2.onCancel(() => lateAbort = true)
      lateAbort shouldBe false
      f.text shouldBe "Ho"
      f.images.head.alt shouldBe "description"
      f.revoked shouldBe Vector("blob:a")
      f.history.undo() shouldBe Right(true)
      f.text shouldBe "Hello"
      f.images shouldBe empty
    }
  }
  it should "track an insertion through more edits than mapping retention" in {
    val f  = new MediaFixture
    val id = f.upload()
    (0 until 80).foreach(_ => f.session.update(_.spliceText(NodeId("t"), 0, 0, "x")))
    f.success()
    f.done(id).map { _ =>
      val p = f.session.document.childrenOf(NodeId("p"))
      f.session.document.node(p.head).get.asInstanceOf[TextNode].text shouldBe "x" * 80 + "He"
      f.images should have size 1
    }
  }
  it should "leave a selected range intact on asynchronous failure" in {
    val f  = new MediaFixture
    val id = f.upload(a = 0, b = 5)
    f.requests.head._1.failure(new IllegalStateException("upload rejected"))
    f.done(id).map { status =>
      status.phase shouldBe MediaPhase.Failed("upload rejected")
      f.text shouldBe "Hello"
      f.revoked shouldBe Vector("blob:a")
    }
  }
  it should "contain a synchronous service failure and release its preview" in {
    val f  = new MediaFixture
    val id = f.upload("throw")
    f.done(id).map { status =>
      status.phase shouldBe MediaPhase.Failed("sync failure")
      f.revoked shouldBe Vector("blob:throw")
    }
  }
  it should "cancel once and ignore late completion and progress" in {
    val f       = new MediaFixture
    val id      = f.upload()
    var aborted = 0
    f.requests.head._2.onCancel(() => aborted += 1)
    f.coordinator.cancel(id)
    f.coordinator.cancel(id)
    f.success()
    f.requests.head._2.reportProgress(1)
    f.done(id).map { status =>
      status.phase shouldBe MediaPhase.Cancelled
      aborted shouldBe 1
      f.images shouldBe empty
      f.revoked shouldBe Vector("blob:a")
    }
  }
  it should "discard a deleted target without falling back to another position" in {
    val f  = new MediaFixture
    val id = f.upload()
    f.session.update(_.remove(NodeId("p")))
    f.success()
    f.done(id).map { status =>
      status.phase shouldBe a[MediaPhase.Discarded]
      f.images shouldBe empty
    }
  }
  it should "invalidate a restored document even when node IDs survive" in {
    val f           = new MediaFixture
    val id          = f.upload()
    val replacement = f.document
      .applyOperation(Operation.SpliceText(NodeId("t"), 0, 5, "Other"))
      .toOption
      .get
      .document
    f.session.update(_.restore(replacement, None))
    f.success()
    f.done(id).map { status =>
      status.phase shouldBe a[MediaPhase.Discarded]
      f.images shouldBe empty
      f.text shouldBe "Other"
    }
  }
  it should "invalidate pending completion after Undo" in {
    val f = new MediaFixture
    f.session.update(_.spliceText(NodeId("t"), 0, 0, "x"))
    val id = f.upload()
    f.history.undo()
    f.success()
    f.done(id).map { status =>
      status.phase shouldBe a[MediaPhase.Discarded]
      f.images shouldBe empty
    }
  }
  it should "refuse to delete selected text changed while uploading" in {
    val f  = new MediaFixture
    val id = f.upload(a = 1, b = 4)
    f.session.update(_.spliceText(NodeId("t"), 2, 0, "NEW"))
    f.success()
    f.done(id).map { status =>
      status.phase shouldBe a[MediaPhase.Discarded]
      f.text shouldBe "HeNEWllo"
    }
  }
  for busy <- Vector(MediaAvailability.SourceBusy, MediaAvailability.CompositionBusy) do
    it should s"wait during $busy and revalidate exactly once on resume" in {
      val f = new MediaFixture
      f.mode = busy
      val id = f.upload()
      f.success()
      f.awaitPhase(id)(_ == MediaPhase.Waiting(busy)).flatMap { _ =>
        f.images shouldBe empty
        f.mode = MediaAvailability.Ready
        f.coordinator.resume()
        f.coordinator.resume()
        f.done(id).map { _ => f.images should have size 1 }
      }
    }
  it should "insert parallel completions independently and enforce capacity" in {
    val f = new MediaFixture
    val a = f.upload("a", 1, 1)
    val b = f.upload("b", 4, 4)
    f.coordinator.upload("c", f.target(), "").isLeft shouldBe true
    f.success(1)
    f.done(b).flatMap { _ =>
      f.success(0)
      f.done(a).map { _ =>
        f.images should have size 2
        f.requests(0)._1.trySuccess(f.reference()) shouldBe false
        f.coordinator.resume()
        f.images should have size 2
      }
    }
  }
  it should "cancel all resources on dispose and refuse reuse" in {
    val f  = new MediaFixture
    val id = f.upload()
    f.coordinator.dispose()
    f.coordinator.dispose()
    f.session.dispose()
    f.success()
    f.done(id).map { _ =>
      f.images shouldBe empty
      f.requests.head._2.isCancelled shouldBe true
      f.revoked shouldBe Vector("blob:a")
      f.coordinator
        .capture(RangeSelection.caret(Point.textBefore(NodeId("t"), 0)))
        .isLeft shouldBe true
    }
  }
  "An external reference" should "skip uploading and retain an explicitly empty alt" in {
    val f  = new MediaFixture
    val id = f.coordinator
      .insert(f.reference("https://example.test/image.png"), f.target(), "")
      .toOption
      .get
    f.done(id).map { _ =>
      f.requests shouldBe empty
      f.images.head.alt shouldBe ""
      f.images.head.src.value shouldBe "https://example.test/image.png"
    }
  }
  it should "reject a reference accepted only by a weaker URL policy" in {
    val f    = new MediaFixture
    val weak = MediaReference(
      MediaUrlPolicy(schemes = Set("http")).parse("http://example.test/a.png").toOption.get
    )
    val id = f.coordinator.insert(weak, f.target(), "").toOption.get
    f.done(id).map { status =>
      status.phase shouldBe a[MediaPhase.Failed]
      f.images shouldBe empty
    }
  }
  "A captured picker target" should "refuse changed selection content before uploading" in {
    val f      = new MediaFixture
    val target = f.target(0, 5)
    f.session.update(_.spliceText(NodeId("t"), 2, 0, "new"))
    f.coordinator.upload("a", target, "").isLeft shouldBe true
    f.requests shouldBe empty
  }
  it should "refuse another coordinator's target" in {
    val f     = new MediaFixture
    val other = new MediaFixture
    f.coordinator.upload("a", other.target(), "").isLeft shouldBe true
    f.requests shouldBe empty
  }
  "A waiting upload" should "be discarded after a document replacement before resume" in {
    val f = new MediaFixture
    f.mode = MediaAvailability.SourceBusy
    val id = f.upload()
    f.success()
    f.awaitPhase(id)(_ == MediaPhase.Waiting(MediaAvailability.SourceBusy)).flatMap { _ =>
      f.coordinator.invalidate()
      f.mode = MediaAvailability.Ready
      f.coordinator.resume()
      f.done(id).map { status =>
        status.phase shouldBe a[MediaPhase.Discarded]
        f.images shouldBe empty
        f.revoked shouldBe Vector("blob:a")
      }
    }
  }
  it should "release resources if the session is disposed before completion" in {
    val f  = new MediaFixture
    val id = f.upload()
    f.session.dispose()
    f.success()
    f.done(id).map { status =>
      status.phase shouldBe a[MediaPhase.Discarded]
      f.revoked shouldBe Vector("blob:a")
      f.images shouldBe empty
    }
  }
  "Media limits" should "reject readonly uploads before invoking a service" in {
    val f = new MediaFixture
    f.mode = MediaAvailability.ReadOnly
    f.coordinator.upload("a", f.target(), "").isLeft shouldBe true
    f.requests shouldBe empty
  }
  it should "bound retained terminal statuses" in {
    val f = new MediaFixture
    (0 until 5).foreach(_ => f.coordinator.insert(f.reference(), f.target(), ""))
    f.coordinator.statuses should have size 3
    f.images should have size 5
  }
