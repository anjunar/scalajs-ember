package ember.editor.core

import ember.editor.foreign.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

/** Aufloesung, Installation und Aufraeumen von Extensions (P05, Architektur §13). */
final class ExtensionSpec extends AnyFlatSpec with Matchers {

  private def id(value: String): NodeId = NodeId(value)

  private val root = id("root")

  /** Die Basis: die Knotenarten, die der Kern selbst mitbringt. */
  private object CoreNodes extends Extension:
    val id                                          = ExtensionId("core-nodes")
    override def contribute: ExtensionContributions =
      ExtensionContributions(nodeTypes = Vector(RootNode, TextNode))

  private def ext(
      name: String,
      requires: Vector[String] = Vector.empty,
      contributions: ExtensionContributions = ExtensionContributions.empty,
      onInstall: EditorSession => Subscription = _ => Subscription.cancelled
  ): Extension =
    new Extension:
      val id                                          = ExtensionId(name)
      override val dependsOn                          = requires.map(ExtensionId.apply)
      override def contribute: ExtensionContributions = contributions
      override def install(session: EditorSession)    = onInstall(session)

  private def resolved(extensions: Extension*): ResolvedExtensions =
    ExtensionResolver.resolve(extensions.toVector) match
      case Right(value) => value
      case Left(errors) => fail(errors.map(_.render).mkString("nicht aufloesbar:\n", "\n", ""))

  private def errorsOf(extensions: Extension*): Vector[ExtensionError] =
    ExtensionResolver.resolve(extensions.toVector) match
      case Left(errors) => errors
      case Right(_)     => fail("Ungueltige Konfiguration wurde akzeptiert")

  // ---------------------------------------------------------------------------------------
  // Aufloesung
  // ---------------------------------------------------------------------------------------

  "Resolution" should "collect contributions in dependency order" in {
    // §10: Reihenfolge ist Abhaengigkeitsordnung, dann Registrierungsordnung. Sie bestimmt,
    // in welcher Folge Transforms und gleich priorisierte Command-Handler laufen.
    val richText = ext("rich-text", requires = Vector("core-nodes"))
    val lists    = ext("lists", requires = Vector("rich-text"))

    // Absichtlich verkehrt herum angegeben.
    resolved(lists, richText, CoreNodes).order.map(_.value) shouldBe
      Vector("core-nodes", "rich-text", "lists")
  }

  it should "keep the given order among independent extensions" in {
    resolved(CoreNodes, ext("a"), ext("b")).order.map(_.value) shouldBe
      Vector("core-nodes", "a", "b")
  }

  it should "reject a duplicate extension" in {
    errorsOf(CoreNodes, ext("twice"), ext("twice")) shouldBe
      Vector(ExtensionError.DuplicateExtension(ExtensionId("twice")))
  }

  it should "reject a missing dependency" in {
    errorsOf(CoreNodes, ext("lists", requires = Vector("rich-text"))) shouldBe
      Vector(ExtensionError.MissingDependency(ExtensionId("lists"), ExtensionId("rich-text")))
  }

  it should "reject a dependency cycle" in {
    val errors = errorsOf(
      CoreNodes,
      ext("a", requires = Vector("b")),
      ext("b", requires = Vector("a"))
    )

    errors shouldBe Vector(
      ExtensionError.DependencyCycle(Vector(ExtensionId("a"), ExtensionId("b")))
    )
  }

  it should "reject two extensions claiming the same wire name" in {
    val errors = errorsOf(
      CoreNodes,
      ext("mine", contributions = ExtensionContributions(nodeTypes = Vector(CaptionNode))),
      ext("theirs", contributions = ExtensionContributions(nodeTypes = Vector(CaptionNode)))
    )

    errors shouldBe Vector(ExtensionError.DuplicateNodeType(CaptionNode.typeId))
  }

  it should "find every fault before anything is built" in {
    // §13, Akzeptanz: Aufloesungsfehler entstehen vor der Installation. Es gibt keine
    // Reihenfolge, in der eine dieser Verletzungen erst zur Laufzeit auffiele.
    val installed = mutable.ArrayBuffer.empty[String]
    val faulty    = ext(
      "faulty",
      requires = Vector("fehlt"),
      onInstall = _ => { installed += "faulty"; Subscription.cancelled }
    )

    ExtensionResolver.resolve(Vector(CoreNodes, faulty)).isLeft shouldBe true
    installed shouldBe empty
  }

  // ---------------------------------------------------------------------------------------
  // Schema und Ersetzung
  // ---------------------------------------------------------------------------------------

  "The resolved schema" should "contain every contributed node type" in {
    val schema = resolved(
      CoreNodes,
      ext("caption", contributions = ExtensionContributions(nodeTypes = Vector(CaptionNode)))
    ).schema

    schema.knows(CaptionNode.typeId) shouldBe true
    schema.knows(RootNode.typeId) shouldBe true
  }

  "A node type replacement" should "take the place of the original" in {
    // §8.3: eingebaute Typen spezialisieren, ohne jede Aufrufstelle anzupassen.
    val schema = resolved(
      CoreNodes,
      ext(
        "specialised",
        contributions = ExtensionContributions(
          replacements = Vector(NodeTypeReplacement(TextNode.typeId, StrictText))
        )
      )
    ).schema

    schema.descriptorFor(TextNode(id("t1"), "x")) shouldBe Some(StrictText)
    schema.types.map(_.typeId.value) should contain("test.strict-text/1")
    schema.types.map(_.typeId.value) should not contain "ember.core.text/1"
  }

  it should "keep the old wire name resolvable" in {
    // Damit bereits gespeicherte Dokumente weiter dekodierbar bleiben -- ein Rendererwechsel
    // allein migriert kein Dokument (§8.3).
    val schema = resolved(
      CoreNodes,
      ext(
        "specialised",
        contributions = ExtensionContributions(
          replacements = Vector(NodeTypeReplacement(TextNode.typeId, StrictText))
        )
      )
    ).schema

    schema.byId(TextNode.typeId) shouldBe Some(StrictText)
    schema.aliasedTypeIds should contain(TextNode.typeId)
  }

  it should "reject two replacements of the same type" in {
    val errors = errorsOf(
      CoreNodes,
      ext(
        "a",
        contributions = ExtensionContributions(replacements =
          Vector(NodeTypeReplacement(TextNode.typeId, StrictText))
        )
      ),
      ext(
        "b",
        contributions = ExtensionContributions(replacements =
          Vector(NodeTypeReplacement(TextNode.typeId, StrictText))
        )
      )
    )

    errors shouldBe Vector(ExtensionError.DuplicateReplacement(TextNode.typeId))
  }

  it should "reject a replacement of something nobody registered" in {
    val errors = errorsOf(
      CoreNodes,
      ext(
        "a",
        contributions = ExtensionContributions(
          replacements = Vector(NodeTypeReplacement(CaptionNode.typeId, StrictText))
        )
      )
    )

    errors shouldBe Vector(ExtensionError.ReplacementOfUnknownType(CaptionNode.typeId))
  }

  /** Ein spezialisierter Textlauf-Deskriptor mit eigenem Wire-Namen. */
  private object StrictText extends NodeType[TextNode]:
    val typeId                                      = NodeTypeId("test.strict-text/1")
    def project(node: EditorNode): Option[TextNode] = TextNode.project(node)
    def rekey(node: TextNode, id: NodeId): TextNode = TextNode.rekey(node, id)

  // ---------------------------------------------------------------------------------------
  // Sitzungskonfiguration
  // ---------------------------------------------------------------------------------------

  "The session config" should "carry every kind of contribution" in {
    val Bold  = EditorCommand.unit("bold")
    val Draft = new StateField[String] { val name = "draft"; val initial = "" }
    val rule  = PreCommitRule("regel")(_ => None)
    val trim  = new Transform[TextNode]:
      val name                                                   = "trim"
      val nodeType                                               = TextNode
      def transform(node: TextNode, scope: TransformScope): Unit = ()

    val config = resolved(
      CoreNodes,
      ext(
        "alles",
        contributions = ExtensionContributions(
          selectionMappers = Vector(CellRangeSelectionMapper),
          fields = Vector(Draft),
          preCommitRules = Vector(rule),
          transforms = Vector(trim),
          commands = Vector(CommandRegistration(Bold)((_, _) => CommandResult.Handled))
        )
      )
    ).sessionConfig()

    config.fields shouldBe Vector(Draft)
    config.preCommitRules shouldBe Vector(rule)
    config.transforms shouldBe Vector(trim)
    config.commands should have size 1
    config.selectionSupport.mapperFor(CellRangeSelection(id("a"), id("b"))) shouldBe
      Some(CellRangeSelectionMapper)
  }

  it should "produce a working session" in {
    val Bold       = EditorCommand.unit("bold")
    val configured = resolved(
      CoreNodes,
      ext(
        "bold",
        contributions =
          ExtensionContributions(commands = Vector(CommandRegistration(Bold) { (scope, _) =>
            scope.spliceText(id("t1"), 0, 0, "*")
            CommandResult.Handled
          }))
      )
    )

    val document = Document.unsafe(
      configured.schema,
      root,
      Vector(RootNode(root, Vector(id("t1"))), TextNode(id("t1"), "Hallo"))
    )
    val editor = EditorSession
      .create(document, configured, configured.sessionConfig())
      .getOrElse(fail("Sitzung abgewiesen"))

    editor.dispatch(Bold).map(_.wasHandled) shouldBe Right(true)
    editor.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "*Hallo"))
  }

  it should "refuse a document built against a different schema" in {
    val configured = resolved(CoreNodes)
    val stranger   = Document.unsafe(
      Schema.unsafe(RootNode, TextNode),
      root,
      Vector(RootNode(root, Vector.empty))
    )

    EditorSession.create(stranger, configured, configured.sessionConfig()) shouldBe
      Left(Vector(ExtensionError.SchemaMismatch))
  }

  // ---------------------------------------------------------------------------------------
  // Installation und Abbau
  // ---------------------------------------------------------------------------------------

  private def emptyDocumentFor(configured: ResolvedExtensions): Document =
    configured.emptyDocument(root).getOrElse(fail("leeres Dokument abgewiesen"))

  private def tracking(
      name: String,
      log: mutable.ArrayBuffer[String],
      failing: Boolean = false
  ): Extension =
    ext(
      name,
      onInstall = _ =>
        if failing then throw new IllegalStateException(s"$name kann nicht")
        else
          log += s"install:$name"
          Subscription(() => log += s"dispose:$name")
    )

  "Installation" should "run in resolution order and dispose in reverse" in {
    // §13: dispose in umgekehrter Reihenfolge. Wer zuletzt aufgebaut hat, baut zuerst ab --
    // sonst raeumt eine Extension unter einer anderen den Boden weg.
    val log        = mutable.ArrayBuffer.empty[String]
    val configured = resolved(CoreNodes, tracking("erste", log), tracking("zweite", log))
    val editor     = EditorSession
      .create(emptyDocumentFor(configured), configured, configured.sessionConfig())
      .getOrElse(fail("Installation gescheitert"))

    log.toVector shouldBe Vector("install:erste", "install:zweite")

    editor.dispose()

    log.toVector shouldBe Vector(
      "install:erste",
      "install:zweite",
      "dispose:zweite",
      "dispose:erste"
    )
  }

  it should "clean up everything when one installation fails" in {
    // §13: Teilweise fehlgeschlagene Installation raeumt alle bis dahin installierten
    // Ressourcen auf. Eine halb installierte Sitzung gibt es nicht.
    val log        = mutable.ArrayBuffer.empty[String]
    val configured =
      resolved(
        CoreNodes,
        tracking("erste", log),
        tracking("kaputt", log, failing = true),
        tracking("dritte", log)
      )

    val outcome =
      EditorSession.create(emptyDocumentFor(configured), configured, configured.sessionConfig())

    outcome.left.map(_.map(_.getClass.getSimpleName)) shouldBe Left(Vector("InstallationFailed"))
    log.toVector shouldBe Vector("install:erste", "dispose:erste")
  }

  it should "not install anything after the failure" in {
    val log        = mutable.ArrayBuffer.empty[String]
    val configured =
      resolved(CoreNodes, tracking("kaputt", log, failing = true), tracking("danach", log))

    EditorSession
      .create(emptyDocumentFor(configured), configured, configured.sessionConfig())
      .isLeft shouldBe
      true
    log should not contain "install:danach"
  }

  "Dispose" should "run exactly once" in {
    // §13, Akzeptanz: vollstaendiger Cleanup genau einmal.
    val log        = mutable.ArrayBuffer.empty[String]
    val configured = resolved(CoreNodes, tracking("einmal", log))
    val editor     = EditorSession
      .create(emptyDocumentFor(configured), configured, configured.sessionConfig())
      .getOrElse(fail("Installation gescheitert"))

    editor.dispose()
    editor.dispose()

    log.count(_ == "dispose:einmal") shouldBe 1
  }

  "An extension" should "be able to observe its session" in {
    // Der Regelfall braucht `install` nicht -- Commands, Transforms und Felder kommen ueber
    // `contribute`. Gedacht ist es fuer das, was eine lebende Sitzung braucht.
    val seen    = mutable.ArrayBuffer.empty[Long]
    val watcher =
      ext("watcher", onInstall = _.onCommit(commit => seen += commit.current.revision.value))
    val configured = resolved(CoreNodes, watcher)
    val document   = Document.unsafe(
      configured.schema,
      root,
      Vector(RootNode(root, Vector(id("t1"))), TextNode(id("t1"), "Hallo"))
    )
    val editor = EditorSession
      .create(document, configured, configured.sessionConfig())
      .getOrElse(fail("Installation gescheitert"))

    editor.update(_.spliceText(id("t1"), 0, 0, "X"))

    seen.toVector shouldBe Vector(1L)
  }

  it should "work entirely without any UI" in {
    // §13, Akzeptanz: Custom Extension ohne UI moeglich. Diese ganze Suite laeuft headless --
    // die Abhaengigkeitsgrenze aus P01 erzwingt es ohnehin.
    val Count    = new StateField[Int] { val name = "count"; val initial = 0 }
    val counting = new Transform[TextNode]:
      val name                                                   = "counting"
      val nodeType                                               = TextNode
      def transform(node: TextNode, scope: TransformScope): Unit = ()

    val configured = resolved(
      CoreNodes,
      ext(
        "headless",
        contributions =
          ExtensionContributions(fields = Vector(Count), transforms = Vector(counting))
      )
    )
    val editor = EditorSession
      .create(emptyDocumentFor(configured), configured, configured.sessionConfig())
      .getOrElse(fail("Installation gescheitert"))

    editor.state.fields(Count) shouldBe 0
  }
}
