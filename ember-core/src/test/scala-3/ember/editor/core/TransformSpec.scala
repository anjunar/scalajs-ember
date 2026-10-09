package ember.editor.core

import ember.editor.foreign.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

/** Normalisierung bis zum Fixpunkt (P05, Architektur §10, Schritt 4). */
final class TransformSpec extends AnyFlatSpec with Matchers {

  private val schema = Schema.unsafe(RootNode, TextNode, CaptionNode)

  private def id(value: String): NodeId = NodeId(value)

  private val root = id("root")

  private def documentWith(text: String): Document = Document.unsafe(
    schema,
    root,
    Vector(
      RootNode(root, Vector(id("c1"))),
      CaptionNode.of(id("c1"), Vector(id("t1"))),
      TextNode(id("t1"), text)
    )
  )

  private val document = documentWith("hallo")

  private def session(transforms: Transform[?]*): EditorSession =
    EditorSession.create(document, SessionConfig(transforms = transforms.toVector))

  /** Ein idempotenter Transform: schneidet fuehrende Leerzeichen weg.
    *
    * Idempotent, weil ein zweiter Lauf nichts mehr findet. Genau das ist die Voraussetzung dafuer,
    * dass die Fixpunktschleife ueberhaupt zur Ruhe kommt.
    */
  private object TrimLeading extends Transform[TextNode]:
    val name                                                   = "trim-leading"
    val nodeType                                               = TextNode
    def transform(node: TextNode, scope: TransformScope): Unit =
      val trimmed = node.text.dropWhile(_ == ' ')
      if trimmed != node.text then
        scope.spliceText(node.id, 0, node.text.length - trimmed.length, ""): Unit

  /** Ein Transform, der nie zur Ruhe kommt: er haengt bei jedem Lauf etwas an. */
  private object NeverSettles extends Transform[TextNode]:
    val name                                                   = "never-settles"
    val nodeType                                               = TextNode
    def transform(node: TextNode, scope: TransformScope): Unit =
      scope.spliceText(node.id, node.text.length, 0, "!"): Unit

  /** Zwei Transforms, die einander die Arbeit zurueckdrehen. */
  private object AddBang extends Transform[TextNode]:
    val name                                                   = "add-bang"
    val nodeType                                               = TextNode
    def transform(node: TextNode, scope: TransformScope): Unit =
      if !node.text.endsWith("!") then scope.spliceText(node.id, node.text.length, 0, "!"): Unit

  private object RemoveBang extends Transform[TextNode]:
    val name                                                   = "remove-bang"
    val nodeType                                               = TextNode
    def transform(node: TextNode, scope: TransformScope): Unit =
      if node.text.endsWith("!") then scope.spliceText(node.id, node.text.length - 1, 1, ""): Unit

  /** Ein Transform, der scheitert. */
  private object RemovesTheRoot extends Transform[TextNode]:
    val name                                                   = "removes-the-root"
    val nodeType                                               = TextNode
    def transform(node: TextNode, scope: TransformScope): Unit = scope.remove(root): Unit

  private def recorder(
      log: mutable.ArrayBuffer[String],
      label: String,
      transformPhase: TransformPhase
  ): Transform[?] =
    new Transform[TextNode]:
      val name                                                   = label
      val nodeType                                               = TextNode
      override val phase                                         = transformPhase
      def transform(node: TextNode, scope: TransformScope): Unit = log += label

  // ---------------------------------------------------------------------------------------
  // Der Regelfall
  // ---------------------------------------------------------------------------------------

  "A transform" should "normalise before anything is published" in {
    // §3.2: Invarianten werden hergestellt, bevor etwas sichtbar wird -- nicht in einer
    // Listener-Kaskade danach. Der Beobachter sieht deshalb nie den unnormalisierten Stand.
    val editor = session(TrimLeading)
    val seen   = mutable.ArrayBuffer.empty[String]
    editor.onCommit(commit =>
      seen += commit.current.document.node(id("t1")).get.asInstanceOf[TextNode].text
    )

    editor.update(_.spliceText(id("t1"), 0, 0, "   "))

    seen.toVector shouldBe Vector("hallo")
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "hallo"))
  }

  it should "leave a document that is already in normal form alone" in {
    // Ein Transform, der nichts findet, darf keinen Commit erzeugen -- sonst haette jede
    // Sitzung eine Revision mehr, sobald irgendein Modul eine Regel registriert.
    val editor = session(TrimLeading)

    editor.update(_ => ()).map(_.isNoOp) shouldBe Right(true)
    editor.state.revision.value shouldBe 0
  }

  it should "not run at all when nothing changed" in {
    val editor = session(NeverSettles)

    // NeverSettles wuerde jeden angefassten Knoten veraendern. Da nichts schmutzig ist, gibt es
    // auch nichts zu verarbeiten -- die Dirty-Menge ist leer.
    editor.update(_ => ()).map(_.isNoOp) shouldBe Right(true)
  }

  it should "only visit nodes the transaction actually touched" in {
    val visited = mutable.ArrayBuffer.empty[String]
    val spy     = new Transform[TextNode]:
      val name                                                   = "spy"
      val nodeType                                               = TextNode
      def transform(node: TextNode, scope: TransformScope): Unit = visited += node.id.value

    val wide = Document.unsafe(
      schema,
      root,
      Vector(
        RootNode(root, Vector(id("t1"), id("t2"))),
        TextNode(id("t1"), "eins"),
        TextNode(id("t2"), "zwei")
      )
    )
    val editor = EditorSession.create(wide, SessionConfig(transforms = Vector(spy)))

    editor.update(_.spliceText(id("t1"), 0, 0, "X"))

    visited.toVector shouldBe Vector("t1")
  }

  // ---------------------------------------------------------------------------------------
  // Reihenfolge
  // ---------------------------------------------------------------------------------------

  "Phases" should "run Early, then Normalize, then Late" in {
    val log    = mutable.ArrayBuffer.empty[String]
    val editor = session(
      recorder(log, "spaet", TransformPhase.Late),
      recorder(log, "normal", TransformPhase.Normalize),
      recorder(log, "frueh", TransformPhase.Early)
    )

    editor.update(_.spliceText(id("t1"), 0, 0, "X"))

    log.toVector shouldBe Vector("frueh", "normal", "spaet")
  }

  "Within one phase" should "keep registration order" in {
    val log    = mutable.ArrayBuffer.empty[String]
    val editor = session(
      recorder(log, "erster", TransformPhase.Normalize),
      recorder(log, "zweiter", TransformPhase.Normalize)
    )

    editor.update(_.spliceText(id("t1"), 0, 0, "X"))

    log.toVector shouldBe Vector("erster", "zweiter")
  }

  "Nodes" should "be visited deepest first" in {
    // §3.4: Blaetter vor absichtlich schmutzigen Elementen. Ein Transform, der einen Textlauf
    // normalisiert, soll fertig sein, bevor der Elternblock ueber seine Kinder urteilt.
    val visited = mutable.ArrayBuffer.empty[String]
    val editor  = EditorSession.create(document, SessionConfig(transforms = Vector(spy(visited))))

    editor.update(_.insert(id("c1"), 1, TextNode(id("t2"), "neu")))

    // t2 (Tiefe 2) vor c1 (Tiefe 1). `root` fehlt hier mit Absicht -- siehe naechster Test.
    visited.toVector shouldBe Vector("t2", "c1")
  }

  it should "skip ancestors that were merely touched" in {
    // §3.4: "Nur zur Traversierung markierte Vorfahren sind keine gleichwertigen
    // Transform-Kandidaten." Beim Einfuegen unter c1 liegt root nur in `touchedAncestors` --
    // es hat sich nicht geaendert. Wuerde es trotzdem besucht, liefe bei jedem Tastendruck der
    // gesamte Pfad bis zur Wurzel durch die Normalisierung.
    val visited = mutable.ArrayBuffer.empty[String]
    val editor  = EditorSession.create(document, SessionConfig(transforms = Vector(spy(visited))))

    editor.update(_.insert(id("c1"), 1, TextNode(id("t2"), "neu")))

    visited should not contain "root"
  }

  it should "visit the root last when it changed itself" in {
    val visited = mutable.ArrayBuffer.empty[String]
    val editor  = EditorSession.create(document, SessionConfig(transforms = Vector(spy(visited))))

    // Diesmal aendert sich die Kindliste der Wurzel selbst; sie ist damit echter Kandidat.
    editor.update(_.insert(root, 1, TextNode(id("t2"), "neu")))

    visited.toVector shouldBe Vector("t2", "root")
  }

  /** Ein Transform, der jeden angefassten Knoten bloss protokolliert. */
  private def spy(visited: mutable.ArrayBuffer[String]): Transform[EditorNode] =
    new Transform[EditorNode]:
      val name                                                     = "jeder"
      val nodeType                                                 = AnyNodeType
      def transform(node: EditorNode, scope: TransformScope): Unit = visited += node.id.value

  /** Ein Deskriptor, der jeden Knoten annimmt -- nur fuer den Reihenfolgetest. */
  private object AnyNodeType extends NodeType[EditorNode]:
    val typeId                                          = NodeTypeId("test.any/1")
    def project(node: EditorNode): Option[EditorNode]   = Some(node)
    def rekey(node: EditorNode, id: NodeId): EditorNode = node

  // ---------------------------------------------------------------------------------------
  // Fixpunkt und Budget
  // ---------------------------------------------------------------------------------------

  "The fixpoint loop" should "keep running until nothing changes any more" in {
    // Ein Transform, dessen Ergebnis den naechsten Lauf ausloest: hier entsteht durch das
    // Abschneiden ein neuer fuehrender Leerraum, den erst die naechste Runde findet.
    val editor = session(TrimLeading)

    editor.update(_.spliceText(id("t1"), 0, 0, "  a  "))

    // "  a  hallo" -> "a  hallo": der Rest bleibt, weil er nicht mehr fuehrend ist.
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "a  hallo"))
  }

  "A transform that never settles" should "fail the transaction, not truncate it" in {
    // §10: Das Budget ist ausdruecklich kein stilles Abschneiden der Normalisierung. Ein halb
    // normalisiertes Dokument zu veroeffentlichen waere schlimmer als gar keines.
    val editor = EditorSession.create(
      document,
      SessionConfig(
        transforms = Vector(NeverSettles),
        transformBudget = TransformBudget(maxRounds = 4)
      )
    )

    val outcome = editor.update(_.spliceText(id("t1"), 0, 0, "X"))

    outcome shouldBe Left(UpdateError.TransformBudgetExhausted(4, Vector("never-settles")))
    editor.state.revision.value shouldBe 0
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "hallo"))
  }

  it should "name every transform involved" in {
    // Ohne die Namen bliebe nur das Symptom. Bei zwei Regeln, die einander zurueckdrehen, ist
    // keine von beiden fuer sich genommen auffaellig -- erst das Paar ist der Befund.
    val editor = EditorSession.create(
      document,
      SessionConfig(
        transforms = Vector(AddBang, RemoveBang),
        transformBudget = TransformBudget(maxRounds = 3)
      )
    )

    editor.update(_.spliceText(id("t1"), 0, 0, "X")) shouldBe
      Left(UpdateError.TransformBudgetExhausted(3, Vector("add-bang", "remove-bang")))
  }

  "A failing transform" should "discard the transaction" in {
    val editor  = session(RemovesTheRoot)
    val outcome = editor.update(_.spliceText(id("t1"), 0, 0, "X"))

    outcome shouldBe Left(UpdateError.OperationFailed(OperationError.RootIsImmovable(root)))
    editor.state.revision.value shouldBe 0
  }

  it should "keep later transforms from running" in {
    val reached = mutable.ArrayBuffer.empty[String]
    val editor  = session(
      RemovesTheRoot,
      recorder(reached, "danach", TransformPhase.Late)
    )

    editor.update(_.spliceText(id("t1"), 0, 0, "X")).isLeft shouldBe true
    reached shouldBe empty
  }

  // ---------------------------------------------------------------------------------------
  // Reihenfolge im Commit
  // ---------------------------------------------------------------------------------------

  "Pre-commit rules" should "judge the normalised candidate, not the raw draft" in {
    // §10: Transforms laufen vor Regeln und Reducern. Eine Regel, die vorher urteilt, urteilt
    // ueber einen Zwischenstand, den es nie geben wird.
    val seen = mutable.ArrayBuffer.empty[String]
    val spy  = PreCommitRule("spion") { candidate =>
      seen += candidate.document.node(id("t1")).get.asInstanceOf[TextNode].text
      None
    }

    val editor = EditorSession.create(
      document,
      SessionConfig(transforms = Vector(TrimLeading), preCommitRules = Vector(spy))
    )

    editor.update(_.spliceText(id("t1"), 0, 0, "   "))

    seen.toVector shouldBe Vector("hallo")
  }

  "A transform" should "not run when the body already failed" in {
    val reached = mutable.ArrayBuffer.empty[String]
    val editor  = session(recorder(reached, "nie", TransformPhase.Normalize))

    editor.update { tx =>
      tx.spliceText(id("t1"), 0, 0, "X")
      tx.remove(root)
    }.isLeft shouldBe true

    reached shouldBe empty
  }
}
