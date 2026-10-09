package ember.editor.standard

import ember.editor.clipboard.*
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.{Future, Promise}

final class AsyncClipboardSpec extends AsyncFlatSpec with Matchers:
  "An asynchronous cut" should "wait for confirmation before deleting" in {
    val f = new ClipboardFixture()
    f.select(0, 5)
    val confirmation = Promise[Either[ClipboardError, Unit]]()
    val port         = new AsyncClipboardPort:
      def write(data: ClipboardData) = confirmation.future
    val completion = f.service.cutAsync(port)
    f.text shouldBe "Hello world"
    confirmation.success(Right(()))
    completion.map { result =>
      result.isRight shouldBe true
      f.text shouldBe " world"
    }
  }
  it should "keep content when the asynchronous API rejects" in {
    val f = new ClipboardFixture()
    f.select(0, 5)
    val port = new AsyncClipboardPort:
      def write(data: ClipboardData) = Future.failed(new RuntimeException("permission denied"))
    f.service.cutAsync(port).map { result =>
      result.isLeft shouldBe true
      f.text shouldBe "Hello world"
    }
  }
  it should "keep content when calling the asynchronous API throws" in {
    val f = new ClipboardFixture()
    f.select(0, 5)
    val port = new AsyncClipboardPort:
      def write(data: ClipboardData): Future[Either[ClipboardError, Unit]] =
        throw new RuntimeException("unavailable")
    f.service.cutAsync(port).map { result =>
      result.isLeft shouldBe true
      f.text shouldBe "Hello world"
    }
  }
  it should "report a document conflict after a delayed success" in {
    val f = new ClipboardFixture()
    f.select(0, 5)
    val completed = Promise[Either[ClipboardError, Unit]]()
    val port      = new AsyncClipboardPort:
      def write(data: ClipboardData) = completed.future
    val result = f.service.cutAsync(port)
    f.caret(11)
    f.paste("!")
    completed.success(Right(()))
    result.map { outcome =>
      outcome.isLeft shouldBe true
      f.text shouldBe "Hello world!"
    }
  }
