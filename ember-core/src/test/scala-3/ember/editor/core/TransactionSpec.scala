package ember.editor.core

import ember.editor.foreign.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Die Commit-Grenze: Atomaritaet, Lebensdauer, Revisionen (P04, Architektur §§9–10). */
final class TransactionSpec extends AnyFlatSpec with Matchers {

  private val schema = Schema.unsafe(RootNode, TextNode, CaptionNode, StickerNode)

  private def id(value: String): NodeId = NodeId(value)

  private val root = id("root")

  private val document: Document = Document.unsafe(
    schema,
    root,
    Vector(
      RootNode(root, Vector(id("c1"), id("t3"))),
      CaptionNode.of(id("c1"), Vector(id("t1"), id("t2"))),
      TextNode(id("t1"), "Hallo"),
      TextNode(id("t2"), "Welt"),
      TextNode(id("t3"), "Ende")
    )
  )

  private def session(config: SessionConfig = SessionConfig()): EditorSession =
    EditorSession.create(document, config)

  private def committed(outcome: Either[UpdateError, Commit]): Commit = outcome match
    case Right(commit) => commit
    case Left(error)   => fail(s"Commit abgewiesen: ${error.render}")

  // ---------------------------------------------------------------------------------------
  // Ein Commit, mehrere Operationen
  // ---------------------------------------------------------------------------------------

  "A transaction" should "turn several operations into a single commit" in {
    val editor = session()

    val commit = committed(editor.update { tx =>
      tx.spliceText(id("t1"), 5, 0, "!")
      tx.insert(root, 2, TextNode(id("t4"), "Neu"))
      tx.move(id("t3"), id("c1"), 0)
    })

    commit.previous.revision.value shouldBe 0
    commit.current.revision.value shouldBe 1
    commit.changes.created shouldBe Set(id("t4"))
    commit.changes.moved shouldBe Set(id("t3"))
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "Hallo!"))
    editor.document.childrenOf(id("c1")) shouldBe Vector(id("t3"), id("t1"), id("t2"))
  }

  it should "never expose an intermediate state to an observer" in {
    // §10, Akzeptanz: keine Zwischenzustaende sichtbar. Der Listener laeuft erst, wenn alles
    // durch ist -- er darf niemals das Dokument nach der ersten von drei Operationen sehen.
    val editor = session()
    var seen   = Vector.empty[String]

    editor.onCommit(commit => seen = seen :+ commit.current.document.node(id("t1")).get.toString)

    editor.update { tx =>
      tx.spliceText(id("t1"), 5, 0, "!")
      tx.spliceText(id("t1"), 6, 0, "?")
    }

    seen should have size 1
    seen.head should include("Hallo!?")
  }

  it should "keep index and selection on the same revision" in {
    val editor = session()

    val commit = committed(editor.update { tx =>
      tx.select(RangeSelection.caret(Point.textBefore(id("t1"), 2)))
      tx.spliceText(id("t1"), 0, 0, "XX")
    })

    // Die Auswahl im veroeffentlichten Zustand wurde von derselben Operation mitgefuehrt, die
    // das Dokument geaendert hat -- nicht nachtraeglich neu berechnet.
    commit.current.selection shouldBe Some(RangeSelection.caret(Point.textBefore(id("t1"), 4)))
    commit.current.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "XXHallo"))
    commit.current.revision shouldBe commit.current.documentRevision
  }

  // ---------------------------------------------------------------------------------------
  // Atomaritaet
  // ---------------------------------------------------------------------------------------

  "A failing operation" should "discard the whole transaction" in {
    val editor = session()

    val outcome = editor.update { tx =>
      tx.spliceText(id("t1"), 5, 0, "!")
      tx.remove(root)
      tx.spliceText(id("t2"), 0, 0, "nie")
    }

    outcome shouldBe Left(UpdateError.OperationFailed(OperationError.RootIsImmovable(root)))
    editor.state.revision.value shouldBe 0
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "Hallo"))
  }

  it should "latch and refuse further work" in {
    // Die Closure liefert Unit; ein ignoriertes `Either` darf nicht dazu fuehren, dass auf
    // einem kaputten Entwurf weitergearbeitet wird.
    val editor = session()
    var second = Option.empty[Either[UpdateError, Unit]]

    editor.update { tx =>
      tx.remove(root)
      second = Some(tx.spliceText(id("t1"), 0, 0, "x"))
      tx.hasFailed shouldBe true
    }

    second shouldBe Some(Left(UpdateError.OperationFailed(OperationError.RootIsImmovable(root))))
  }

  it should "reject a selection the draft cannot represent" in {
    val editor = session()

    val outcome = editor.update { tx =>
      tx.select(RangeSelection.caret(Point.textBefore(id("t1"), 99)))
    }

    outcome.left.map(_.getClass.getSimpleName) shouldBe Left("InvalidSelection")
    editor.state.revision.value shouldBe 0
  }

  "A throwing body" should "propagate and leave the session untouched" in {
    // Eine geworfene Exception ist ein Programmierfehler und wird nicht in ein `Left`
    // verwandelt. Veroeffentlicht wird ohnehin erst ganz am Ende.
    val editor = session()

    intercept[IllegalStateException] {
      editor.update { tx =>
        tx.spliceText(id("t1"), 0, 0, "x")
        throw new IllegalStateException("Bug im Feature-Modul")
      }
    }

    editor.state.revision.value shouldBe 0
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "Hallo"))
  }

  it should "still leave the session usable afterwards" in {
    val editor = session()

    intercept[IllegalStateException](editor.update(_ => throw new IllegalStateException("x")))

    committed(editor.update(_.spliceText(id("t1"), 0, 0, "y"))).current.revision.value shouldBe 1
  }

  // ---------------------------------------------------------------------------------------
  // Lebensdauer des Handles
  // ---------------------------------------------------------------------------------------

  "An expired transaction handle" should "refuse every access" in {
    // §10: Ein Handle gilt nur innerhalb seiner Closure. Es in einem Future aufzuheben ist ein
    // Programmierfehler -- deshalb wirft es, statt ein Left zu liefern.
    val editor  = session()
    var escaped = Option.empty[Transaction]

    editor.update(tx => escaped = Some(tx))

    val stale = escaped.getOrElse(fail("kein Handle"))
    intercept[EditorContractViolation](stale.document)
    intercept[EditorContractViolation](stale.spliceText(id("t1"), 0, 0, "x"))
    intercept[EditorContractViolation](stale.selection)
  }

  it should "expire even when the body threw" in {
    val editor  = session()
    var escaped = Option.empty[Transaction]

    intercept[IllegalStateException] {
      editor.update { tx =>
        escaped = Some(tx)
        throw new IllegalStateException("x")
      }
    }

    intercept[EditorContractViolation](escaped.get.document)
  }

  // ---------------------------------------------------------------------------------------
  // Reentranz
  // ---------------------------------------------------------------------------------------

  "A nested update" should "be rejected" in {
    val editor = session()
    var inner  = Option.empty[Either[UpdateError, Commit]]

    editor.update { _ =>
      inner = Some(editor.update(_.spliceText(id("t2"), 0, 0, "x")))
    }

    inner shouldBe Some(Left(UpdateError.NestedUpdate))
    editor.document.node(id("t2")) shouldBe Some(TextNode(id("t2"), "Welt"))
  }

  // ---------------------------------------------------------------------------------------
  // Revisionen und No-op
  // ---------------------------------------------------------------------------------------

  "A no-op" should "publish nothing and notify nobody" in {
    val editor  = session()
    var notices = 0
    editor.onCommit(_ => notices += 1)

    // Gleicher Text: das Primitiv erkennt es und liefert einen leeren ChangeSet.
    val commit = committed(editor.update(_.spliceText(id("t1"), 2, 1, "l")))

    commit.isNoOp shouldBe true
    commit.current.revision.value shouldBe 0
    notices shouldBe 0
  }

  it should "also cover a transaction that does nothing at all" in {
    val editor = session()

    committed(editor.update(_ => ())).isNoOp shouldBe true
    editor.state.revision.value shouldBe 0
  }

  "A pure selection change" should "advance the session revision but not the document one" in {
    // §9: getrennte Revisionen, damit ein bewegter Cursor keinen Schreibvorgang ausloest.
    val editor = session()

    val commit =
      committed(editor.update(_.select(RangeSelection.caret(Point.textBefore(id("t1"), 2)))))

    commit.isNoOp shouldBe false
    commit.selectionChanged shouldBe true
    commit.documentChanged shouldBe false
    commit.current.revision.value shouldBe 1
    commit.current.documentRevision.value shouldBe 0
  }

  "A document change" should "advance both revisions" in {
    val editor = session()
    val commit = committed(editor.update(_.spliceText(id("t1"), 0, 0, "X")))

    commit.documentChanged shouldBe true
    commit.current.revision.value shouldBe 1
    commit.current.documentRevision.value shouldBe 1
  }

  "Transaction metadata" should "reach the commit unchanged" in {
    val editor = session()
    val meta   = TransactionMeta.labelled("Bild eingefuegt", Origin.System).taggedWith("media")

    committed(editor.update(meta)(_.spliceText(id("t1"), 0, 0, "X"))).meta shouldBe meta
  }

  // ---------------------------------------------------------------------------------------
  // Snapshots und Bookmarks
  // ---------------------------------------------------------------------------------------

  "An earlier snapshot" should "stay readable and unchanged" in {
    val editor = session()
    val before = editor.state

    editor.update(_.remove(id("c1")))

    before.document.contains(id("t1")) shouldBe true
    before.revision.value shouldBe 0
    editor.document.contains(id("t1")) shouldBe false
  }

  "A bookmark" should "resolve through the session mapping" in {
    val editor   = session()
    val bookmark = Bookmark(Point.textBefore(id("t1"), 3), editor.state.revision)

    editor.update(_.spliceText(id("t1"), 0, 0, "XX"))
    editor.update(_.spliceText(id("t1"), 0, 0, "Y"))

    val mapping = editor.mappingSince(bookmark.revision).getOrElse(fail("Abbildung fehlt"))

    bookmark.resolve(mapping) shouldBe Right(Point.textBefore(id("t1"), 6))
  }

  it should "expire once its target was removed" in {
    val editor   = session()
    val bookmark = Bookmark(Point.textBefore(id("t1"), 3), editor.state.revision)

    editor.update(_.remove(id("t1")))

    val mapping = editor.mappingSince(bookmark.revision).getOrElse(fail("Abbildung fehlt"))
    bookmark.resolve(mapping).isLeft shouldBe true
  }

  it should "expire once its mappings fell out of retention" in {
    // §11: begrenzte Retention, und ein Bookmark darueber hinaus laeuft ausdruecklich ab --
    // statt eine Position zu raten.
    val editor   = session(SessionConfig(mappingRetention = 2))
    val bookmark = Bookmark(Point.textBefore(id("t1"), 0), editor.state.revision)

    (1 to 4).foreach(step => editor.update(_.spliceText(id("t1"), 0, 0, s"$step")))

    editor.mappingSince(bookmark.revision).isLeft shouldBe true
  }

  it should "resolve trivially against the current revision" in {
    val editor = session()

    editor
      .mappingSince(editor.state.revision)
      .map(_.mapping.map(Point.textBefore(id("t1"), 2))) shouldBe
      Right(MappedPoint.Preserved(Point.textBefore(id("t1"), 2)))
  }

  // ---------------------------------------------------------------------------------------
  // Regeln
  // ---------------------------------------------------------------------------------------

  "A pre-commit rule" should "reject the candidate before anything is published" in {
    // §16: Ein Kandidat, der im gewaehlten Format nicht darstellbar ist, darf gar nicht erst
    // durchkommen -- nicht nachtraeglich bemaengelt werden.
    val limit = PreCommitRule("Zeichenlimit") { candidate =>
      val total = candidate.document.nodes.collect { case text: TextNode => text.text.length }.sum
      Option.when(total > 20)(
        Violation.NodeRejected(
          candidate.document.rootId,
          s"$total Zeichen, erlaubt sind 20.",
          DiagnosticPath.Root
        )
      )
    }

    val editor  = session(SessionConfig(preCommitRules = Vector(limit)))
    val outcome = editor.update(_.spliceText(id("t1"), 0, 0, "sehr viel zusaetzlicher Text"))

    outcome.left.map(_.message) shouldBe Left("Regel `Zeichenlimit`: 41 Zeichen, erlaubt sind 20.")
    editor.state.revision.value shouldBe 0
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "Hallo"))
  }

  it should "see the finished candidate, not an intermediate draft" in {
    var seen = Vector.empty[String]
    val spy  = PreCommitRule("Spion") { candidate =>
      seen = seen :+ candidate.document.node(id("t1")).get.asInstanceOf[TextNode].text
      None
    }

    val editor = session(SessionConfig(preCommitRules = Vector(spy)))
    editor.update { tx =>
      tx.spliceText(id("t1"), 5, 0, "!")
      tx.spliceText(id("t1"), 6, 0, "?")
    }

    seen shouldBe Vector("Hallo!?")
  }

  // ---------------------------------------------------------------------------------------
  // Entsorgte Sitzung
  // ---------------------------------------------------------------------------------------

  "A disposed session" should "refuse updates but keep its last state readable" in {
    val editor = session()
    editor.update(_.spliceText(id("t1"), 0, 0, "X"))
    val last = editor.state

    editor.dispose()

    editor.update(_.spliceText(id("t1"), 0, 0, "Y")) shouldBe Left(UpdateError.SessionDisposed)
    editor.state shouldBe last
    editor.isDisposed shouldBe true
  }

  it should "be idempotent to dispose" in {
    val editor = session()
    editor.dispose()
    editor.dispose()
    editor.isDisposed shouldBe true
  }

  // ---------------------------------------------------------------------------------------
  // Einen Stand wiederherstellen (P11, Architektur §14)
  // ---------------------------------------------------------------------------------------

  "restore" should "put back document and selection in one commit" in {
    val editor = session()
    val before = editor.document
    editor.update(_.select(RangeSelection.caret(Point.textBefore(id("t1"), 5)))): Unit

    committed(editor.update(_.spliceText(id("t1"), 5, 0, " du")))
    val commit = committed(editor.update { tx =>
      tx.restore(before, Some(RangeSelection.caret(Point.textBefore(id("t1"), 2))))
    })

    editor.document shouldBe before
    commit.current.selection shouldBe
      Some(RangeSelection.caret(Point.textBefore(id("t1"), 2)))
    commit.documentChanged shouldBe true
  }

  it should "describe the difference as a change set" in {
    // §14 legt die History auf Snapshots fest, nicht auf ein Operationsprotokoll. Der
    // Unterschied muss also ausgerechnet werden -- und das Ergebnis ist genau das, was die
    // Projektion sonst von den Operationen bekommt (§10).
    val editor = session()
    val before = editor.document

    committed(editor.update { tx =>
      tx.spliceText(id("t1"), 5, 0, "!")
      tx.insert(root, 2, TextNode(id("t4"), "Neu"))
      tx.remove(id("t3"))
    })

    val commit = committed(editor.update(_.restore(before, None)))

    commit.changes.created shouldBe Set(id("t3"))
    commit.changes.removed shouldBe Set(id("t4"))
    commit.changes.childListChanged shouldBe Set(root)
    commit.changes.textSplices.keySet shouldBe Set(id("t1"))
  }

  it should "reduce a reverted keystroke to a single splice" in {
    // Der Punkt der Naeherung: ein rueckgaengig gemachter Tastendruck schreibt ein Zeichen,
    // nicht einen Absatz -- derselbe Unterschied, um den es §15.1 geht.
    val editor = session()
    val before = editor.document
    committed(editor.update(_.spliceText(id("t1"), 3, 0, "X")))

    val commit = committed(editor.update(_.restore(before, None)))

    commit.changes.textSplices(id("t1")) shouldBe Vector(TextSplice(3, 1, ""))
  }

  it should "mark points in vanished nodes as displaced" in {
    // Fuer die Auswahl spielt es keine Rolle -- ein Undo setzt sie ausdruecklich. Es zaehlt
    // fuer Bookmarks, die ueber die Wiederherstellung hinweg aufgeloest werden.
    val editor = session()
    val before = editor.document
    committed(editor.update(_.insert(root, 2, TextNode(id("t9"), "Neu"))))

    val commit = committed(editor.update(_.restore(before, None)))

    commit.mapping.removedNodes shouldBe Set(id("t9"))
    commit.mapping.map(Point.textBefore(id("t1"), 99)).isPreserved shouldBe false
    commit.mapping.map(Point.textBefore(id("t1"), 2)).isPreserved shouldBe true
  }

  it should "reject a document from a different schema" in {
    // §13: "Das Schema einer Session ist fest."
    val editor  = session()
    val foreign = Schema.unsafe(RootNode, TextNode)
    val other   = Document.unsafe(foreign, root, Vector(RootNode(root, Vector.empty)))

    editor.update(_.restore(other, None)) shouldBe Left(UpdateError.ForeignSchema)
    editor.document shouldBe document
  }

  it should "reject a selection that the restored state cannot hold" in {
    val editor = session()
    val before = editor.document
    committed(editor.update(_.insert(root, 2, TextNode(id("t9"), "Neu"))))

    val outcome = editor.update(
      _.restore(before, Some(RangeSelection.caret(Point.textBefore(id("t9"), 0))))
    )

    outcome should matchPattern { case Left(_: UpdateError.InvalidSelection) => }
    editor.document.node(id("t9")) shouldBe Some(TextNode(id("t9"), "Neu"))
  }

  it should "be a no-op when nothing differs" in {
    val editor = session()

    val outcome = editor.update(_.restore(editor.document, editor.selection))

    committed(outcome).isNoOp shouldBe true
  }
}
