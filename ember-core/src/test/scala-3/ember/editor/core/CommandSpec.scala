package ember.editor.core

import ember.editor.foreign.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

/** Typisierte Commands, Prioritaeten und Dispatch (P05, Architektur §12). */
final class CommandSpec extends AnyFlatSpec with Matchers {

  private val schema = Schema.unsafe(RootNode, TextNode, CaptionNode)

  private def id(value: String): NodeId = NodeId(value)

  private val root = id("root")

  private val document: Document = Document.unsafe(
    schema,
    root,
    Vector(RootNode(root, Vector(id("t1"))), TextNode(id("t1"), "Hallo"))
  )

  private val Shout  = EditorCommand.unit("shout")
  private val Insert = EditorCommand.of[String]("insert")

  private def session(registrations: CommandRegistration[?]*): EditorSession =
    EditorSession.create(document, SessionConfig(commands = registrations.toVector))

  private def relaxed(registrations: CommandRegistration[?]*): EditorSession =
    EditorSession.create(
      document,
      SessionConfig(strictCommands = false, commands = registrations.toVector)
    )

  private def prepend(text: String): CommandRegistration[Unit] =
    CommandRegistration(Shout) { (scope, _) =>
      scope.spliceText(id("t1"), 0, 0, text)
      CommandResult.Handled
    }

  // ---------------------------------------------------------------------------------------
  // Identitaet und Payload
  // ---------------------------------------------------------------------------------------

  "A command" should "be looked up by object identity, never by name" in {
    // §12: Zwei Commands mit gleichem Namen sind verschiedene Commands. Ein String-Dispatch
    // haette hier eine Kollision zwischen zwei Modulen erzeugt, die voneinander nichts wissen.
    val mine   = EditorCommand.unit("bold")
    val theirs = EditorCommand.unit("bold")

    val editor = EditorSession.create(
      document,
      SessionConfig(commands = Vector(CommandRegistration(mine)((_, _) => CommandResult.Handled)))
    )

    editor.dispatch(mine).map(_.wasHandled) shouldBe Right(true)
    editor.dispatch(theirs).map(_.wasHandled) shouldBe Right(false)
  }

  it should "carry its payload to the handler" in {
    val seen   = mutable.ArrayBuffer.empty[String]
    val editor = session(CommandRegistration(Insert) { (_, payload) =>
      seen += payload
      CommandResult.Handled
    })

    editor.dispatch(Insert, "Welt")

    seen.toVector shouldBe Vector("Welt")
  }

  // ---------------------------------------------------------------------------------------
  // Reihenfolge und Abbruch
  // ---------------------------------------------------------------------------------------

  "Handlers" should "run from Critical down to Fallback" in {
    val order  = mutable.ArrayBuffer.empty[String]
    val editor = session(
      CommandRegistration(
        Shout,
        CommandPriority.Fallback,
        (_, _) => { order += "fallback"; CommandResult.Pass }
      ),
      CommandRegistration(
        Shout,
        CommandPriority.Critical,
        (_, _) => { order += "critical"; CommandResult.Pass }
      ),
      CommandRegistration(
        Shout,
        CommandPriority.Normal,
        (_, _) => { order += "normal"; CommandResult.Pass }
      )
    )

    editor.dispatch(Shout)

    order.toVector shouldBe Vector("critical", "normal", "fallback")
  }

  it should "keep registration order within one priority" in {
    val order  = mutable.ArrayBuffer.empty[String]
    val editor = session(
      CommandRegistration(Shout)((_, _) => { order += "erster"; CommandResult.Pass }),
      CommandRegistration(Shout)((_, _) => { order += "zweiter"; CommandResult.Pass })
    )

    editor.dispatch(Shout)

    order.toVector shouldBe Vector("erster", "zweiter")
  }

  it should "stop at the first Handled" in {
    val order  = mutable.ArrayBuffer.empty[String]
    val editor = session(
      CommandRegistration(
        Shout,
        CommandPriority.High,
        (_, _) => { order += "hoch"; CommandResult.Handled }
      ),
      CommandRegistration(
        Shout,
        CommandPriority.Low,
        (_, _) => { order += "niedrig"; CommandResult.Pass }
      )
    )

    editor.dispatch(Shout).map(_.result) shouldBe Right(CommandResult.Handled)
    order.toVector shouldBe Vector("hoch")
  }

  it should "report Pass when nobody is responsible" in {
    session().dispatch(Shout).map(_.wasHandled) shouldBe Right(false)
  }

  // ---------------------------------------------------------------------------------------
  // Der Pass-Vertrag
  // ---------------------------------------------------------------------------------------

  "A handler that mutates and passes" should "be diagnosed" in {
    // §12: Pass muss nebenwirkungsfrei sein. Wer aendert und trotzdem weiterreicht, hinterlaesst
    // einen Zustand, mit dem der naechste Handler nicht rechnet -- und der Fehler zeigt sich
    // weit entfernt von seiner Ursache.
    val editor = session(CommandRegistration(Shout) { (scope, _) =>
      scope.spliceText(id("t1"), 0, 0, "heimlich")
      CommandResult.Pass
    })

    val thrown = intercept[EditorContractViolation](editor.dispatch(Shout))

    thrown.getMessage should include("shout")
    thrown.getMessage should include("Pass")
    editor.state.revision.value shouldBe 0
  }

  it should "also be caught when only the selection changed" in {
    val editor = session(CommandRegistration(Shout) { (scope, _) =>
      scope.select(RangeSelection.caret(Point.textBefore(id("t1"), 1)))
      CommandResult.Pass
    })

    intercept[EditorContractViolation](editor.dispatch(Shout))
  }

  it should "stay silent once the check is switched off" in {
    // In Produktion abschaltbar: der Vertrag gilt unveraendert, wird nur nicht mehr ueberwacht.
    val editor = relaxed(CommandRegistration(Shout) { (scope, _) =>
      scope.spliceText(id("t1"), 0, 0, "X")
      CommandResult.Pass
    })

    editor.dispatch(Shout).map(_.wasHandled) shouldBe Right(false)
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "XHallo"))
  }

  // ---------------------------------------------------------------------------------------
  // Dispatch und Transaktionen
  // ---------------------------------------------------------------------------------------

  "Dispatch outside a transaction" should "open exactly one" in {
    // §12: genau eine Transaktion, nicht eine pro Handler. Zwei Aenderungen, ein Commit. Der
    // erste Handler bricht dabei absichtlich den Pass-Vertrag, deshalb die entspannte Sitzung --
    // hier interessiert nur, dass beide im selben Entwurf landen.
    val editor = relaxed(
      CommandRegistration(
        Shout,
        CommandPriority.High,
        (scope, _) => { scope.spliceText(id("t1"), 0, 0, "A"); CommandResult.Pass }
      ),
      CommandRegistration(
        Shout,
        CommandPriority.Low,
        (scope, _) => { scope.spliceText(id("t1"), 0, 0, "B"); CommandResult.Handled }
      )
    )
    val seen = mutable.ArrayBuffer.empty[Long]
    editor.onCommit(commit => seen += commit.current.revision.value)

    editor.dispatch(Shout)

    seen.toVector shouldBe Vector(1L)
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "BAHallo"))
  }

  "tx.dispatch" should "use the running transaction" in {
    val editor = session(prepend("C"))
    val seen   = mutable.ArrayBuffer.empty[Long]
    editor.onCommit(commit => seen += commit.current.revision.value)

    editor.update { tx =>
      tx.spliceText(id("t1"), 0, 0, "B")
      tx.dispatch(Shout) shouldBe CommandResult.Handled
    }

    seen.toVector shouldBe Vector(1L)
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "CBHallo"))
  }

  "A handler failure" should "discard the whole dispatch" in {
    // §12: Fehler verwerfen den gesamten Dispatch-Commit.
    val editor = session(CommandRegistration(Shout) { (scope, _) =>
      scope.spliceText(id("t1"), 0, 0, "A")
      scope.remove(root)
      CommandResult.Handled
    })

    editor.dispatch(Shout) shouldBe Left(
      UpdateError.OperationFailed(OperationError.RootIsImmovable(root))
    )
    editor.state.revision.value shouldBe 0
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "Hallo"))
  }

  it should "keep later handlers from running" in {
    val reached = mutable.ArrayBuffer.empty[String]
    val editor  = session(
      CommandRegistration(
        Shout,
        CommandPriority.High,
        (scope, _) => { scope.remove(root); CommandResult.Pass }
      ),
      CommandRegistration(
        Shout,
        CommandPriority.Low,
        (_, _) => { reached += "niedrig"; CommandResult.Handled }
      )
    )

    editor.dispatch(Shout).isLeft shouldBe true
    reached shouldBe empty
  }

  "A dispatch that changes nothing" should "still report Handled" in {
    // Uebernommen und veraendert sind zwei verschiedene Fragen. Ein Handler, der prueft und
    // feststellt, dass nichts zu tun ist, hat die Absicht trotzdem bearbeitet.
    val editor  = session(CommandRegistration(Shout)((_, _) => CommandResult.Handled))
    val outcome = editor.dispatch(Shout).getOrElse(fail("abgewiesen"))

    outcome.wasHandled shouldBe true
    outcome.commit.isNoOp shouldBe true
  }

  // ---------------------------------------------------------------------------------------
  // Registrierung zur Laufzeit
  // ---------------------------------------------------------------------------------------

  "A runtime registration" should "take effect and be revocable" in {
    val editor       = session()
    val subscription = editor.register(Shout) { (scope, _) =>
      scope.spliceText(id("t1"), 0, 0, "Z")
      CommandResult.Handled
    }

    editor.dispatch(Shout).map(_.wasHandled) shouldBe Right(true)

    subscription.dispose()
    editor.dispatch(Shout).map(_.wasHandled) shouldBe Right(false)
  }

  it should "tolerate being revoked twice" in {
    val editor       = session()
    val subscription = editor.register(Shout)((_, _) => CommandResult.Handled)

    subscription.dispose()
    subscription.dispose()

    editor.dispatch(Shout).map(_.wasHandled) shouldBe Right(false)
  }

  it should "only affect the next dispatch, never the running one" in {
    // §12: Die laufende Handlerliste ist ein Schnappschuss.
    val editor = session()
    var late   = 0

    editor.register(Shout) { (_, _) =>
      editor.register(Shout)((_, _) => { late += 1; CommandResult.Handled })
      CommandResult.Pass
    }

    editor.dispatch(Shout)
    late shouldBe 0

    editor.dispatch(Shout)
    late shouldBe 1
  }
}
