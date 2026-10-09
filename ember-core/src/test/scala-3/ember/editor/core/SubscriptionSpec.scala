package ember.editor.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

/** Benachrichtigung, Fehlerisolation und die Warteschlange (P04, Architektur §10, Schritt 7). */
final class SubscriptionSpec extends AnyFlatSpec with Matchers {

  private val schema = Schema.unsafe(RootNode, TextNode)

  private def id(value: String): NodeId = NodeId(value)

  private val root = id("root")

  private val document: Document = Document.unsafe(
    schema,
    root,
    Vector(RootNode(root, Vector(id("t1"))), TextNode(id("t1"), "Hallo"))
  )

  private def session(errorSink: EditorError => Unit = _ => ()): EditorSession =
    EditorSession.create(document, SessionConfig(errorSink = errorSink))

  private def touch(editor: EditorSession, text: String): Unit =
    editor.update(_.spliceText(id("t1"), 0, 0, text)): Unit

  // ---------------------------------------------------------------------------------------
  // Registrieren und Abmelden
  // ---------------------------------------------------------------------------------------

  "A commit listener" should "receive every published commit" in {
    val editor   = session()
    val received = mutable.ArrayBuffer.empty[Long]
    editor.onCommit(commit => received += commit.current.revision.value)

    touch(editor, "A")
    touch(editor, "B")

    received.toVector shouldBe Vector(1L, 2L)
  }

  it should "stop after its subscription is disposed" in {
    val editor       = session()
    var count        = 0
    val subscription = editor.onCommit(_ => count += 1)

    touch(editor, "A")
    subscription.dispose()
    touch(editor, "B")

    count shouldBe 1
    subscription.isActive shouldBe false
  }

  it should "tolerate being disposed twice" in {
    val editor       = session()
    val subscription = editor.onCommit(_ => ())

    subscription.dispose()
    subscription.dispose()

    subscription.isActive shouldBe false
  }

  it should "come back cancelled from a disposed session" in {
    val editor = session()
    editor.dispose()

    editor.onCommit(_ => ()).isActive shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // Fehlerisolation
  // ---------------------------------------------------------------------------------------

  "A throwing listener" should "not stop the others" in {
    // §10, Schritt 7: Ein fehlerhafter Listener wird gemeldet und verhindert die uebrigen
    // Benachrichtigungen nicht. Ein einziger kaputter Beobachter darf nicht den ganzen Editor
    // blind machen.
    val reported = mutable.ArrayBuffer.empty[EditorError]
    val editor   = session(reported += _)
    var reached  = false

    editor.onCommit(_ => throw new RuntimeException("kaputt"))
    editor.onCommit(_ => reached = true)

    touch(editor, "A")

    reached shouldBe true
    reported should have size 1
    reported.head.message should include("kaputt")
  }

  it should "not roll back a commit that was already published" in {
    // Der Zustand war veroeffentlicht, bevor irgendein Listener lief. Ihn wegen eines
    // Beobachterfehlers halb zurueckzudrehen waere schlimmer als der Fehler selbst
    // (P04, Risiken).
    val editor = session()
    editor.onCommit(_ => throw new RuntimeException("kaputt"))

    touch(editor, "A")

    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "AHallo"))
    editor.state.revision.value shouldBe 1
  }

  it should "reach every registered error sink" in {
    val fromConfig = mutable.ArrayBuffer.empty[EditorError]
    val fromHook   = mutable.ArrayBuffer.empty[EditorError]
    val editor     = session(fromConfig += _)

    editor.onError(fromHook += _)
    editor.onCommit(_ => throw new RuntimeException("kaputt"))
    touch(editor, "A")

    fromConfig should have size 1
    fromHook should have size 1
  }

  // ---------------------------------------------------------------------------------------
  // Schnappschuss der Listenerliste
  // ---------------------------------------------------------------------------------------

  "Subscribing during a notification" should "take effect only from the next round" in {
    val editor = session()
    var late   = 0

    editor.onCommit(_ => editor.onCommit(_ => late += 1))

    touch(editor, "A")
    late shouldBe 0

    touch(editor, "B")
    late should be > 0
  }

  "Disposing the session from a listener" should "stop the remaining ones" in {
    // Weitere Konsumenten einer bereits entsorgten Sitzung zu bedienen waere schlechter, als
    // sie zu uebergehen.
    val editor = session()
    var second = false

    editor.onCommit(_ => editor.dispose())
    editor.onCommit(_ => second = true)

    touch(editor, "A")

    second shouldBe false
    editor.isDisposed shouldBe true
  }

  // ---------------------------------------------------------------------------------------
  // Warteschlange
  // ---------------------------------------------------------------------------------------

  "enqueueUpdate from a listener" should "run after the notification phase is complete" in {
    // §10, Akzeptanz: Ein Folgeupdate beginnt erst, wenn die Benachrichtigung durch ist. Der
    // zweite Listener muss also noch den urspruenglichen Commit sehen.
    val editor = session()
    val seen   = mutable.ArrayBuffer.empty[String]

    editor.onCommit { commit =>
      if commit.current.revision.value == 1 then
        editor.enqueueUpdate(_.spliceText(id("t1"), 0, 0, "Q"))
      seen += s"erster@${commit.current.revision.value}"
    }
    editor.onCommit(commit => seen += s"zweiter@${commit.current.revision.value}")

    touch(editor, "A")

    seen.toVector shouldBe Vector("erster@1", "zweiter@1", "erster@2", "zweiter@2")
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "QAHallo"))
  }

  it should "keep the queue in FIFO order" in {
    val editor = session()
    val order  = mutable.ArrayBuffer.empty[String]

    editor.onCommit { commit =>
      if commit.current.revision.value == 1 then
        editor.enqueueUpdate { tx => order += "erste"; tx.spliceText(id("t1"), 0, 0, "1") }
        editor.enqueueUpdate { tx => order += "zweite"; tx.spliceText(id("t1"), 0, 0, "2") }
    }

    touch(editor, "A")

    order.toVector shouldBe Vector("erste", "zweite")
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "21AHallo"))
  }

  it should "run immediately when called outside a transaction" in {
    val editor = session()

    editor.enqueueUpdate(_.spliceText(id("t1"), 0, 0, "X"))

    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "XHallo"))
  }

  it should "report the failure of a queued update to the error sink" in {
    // Niemand haelt hier ein Either -- ohne den Sink verschwaende der Fehler spurlos.
    val reported = mutable.ArrayBuffer.empty[EditorError]
    val editor   = session(reported += _)

    editor.onCommit { commit =>
      if commit.current.revision.value == 1 then editor.enqueueUpdate(_.remove(root))
    }

    touch(editor, "A")

    reported.map(_.message) should contain(
      UpdateError.OperationFailed(OperationError.RootIsImmovable(root)).message
    )
  }

  it should "be refused once the session is disposed" in {
    val reported = mutable.ArrayBuffer.empty[EditorError]
    val editor   = session(reported += _)
    editor.dispose()

    editor.enqueueUpdate(_.spliceText(id("t1"), 0, 0, "X"))

    reported.toVector shouldBe Vector(UpdateError.SessionDisposed)
  }

  "A nested update from a listener" should "be rejected rather than silently queued" in {
    // Der Unterschied zu enqueueUpdate ist Absicht: `update` behauptet, sofort zu committen,
    // und das kann es hier nicht. Wer das will, sagt es mit enqueueUpdate.
    val editor = session()
    var inner  = Option.empty[Either[UpdateError, Commit]]

    editor.onCommit { commit =>
      if commit.current.revision.value == 1 then
        inner = Some(editor.update(_.spliceText(id("t1"), 0, 0, "Q")))
    }

    touch(editor, "A")

    inner shouldBe Some(Left(UpdateError.NestedUpdate))
  }
}
