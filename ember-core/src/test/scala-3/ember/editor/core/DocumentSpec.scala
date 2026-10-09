package ember.editor.core

import ember.editor.foreign.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

/** Invarianten und Diagnosen des Dokumentmodells (P02, Architektur §8). */
final class DocumentSpec extends AnyFlatSpec with Matchers {

  private val schema = Schema.unsafe(
    RootNode,
    TextNode,
    CaptionNode,
    StickerNode,
    UndescribedContainer
  )

  private def id(value: String): NodeId = NodeId(value)

  private val root = id("root")

  /** Flaches Dokument: alle angegebenen Knoten haengen direkt an der Wurzel. */
  private def documentOf(children: Vector[EditorNode]): Either[Vector[Violation], Document] =
    Document.build(schema, root, RootNode(root, children.map(_.id)) +: children)

  /** Dokument mit expliziter Wurzel-Kindliste, fuer verschachtelte Baeume. */
  private def nested(rootChildren: Vector[NodeId], nodes: EditorNode*): Document =
    built(Document.build(schema, root, RootNode(root, rootChildren) +: nodes.toVector))

  /** Packt das Ergebnis aus und meldet im Fehlerfall die tatsaechlichen Verletzungen.
    *
    * Ein blosses `fail("abgewiesen")` haette hier zwei Testfehler erzeugt, deren Ursache man nicht
    * sieht -- genau der Fall, den §8.2 mit "Pfad, ID und Grund" vermeiden will.
    */
  private def built(result: Either[Vector[Violation], Document]): Document = result match
    case Right(document)  => document
    case Left(violations) => fail(violations.map(_.render).mkString("abgewiesen:\n", "\n", ""))

  // ---------------------------------------------------------------------------------------
  // Gueltige Dokumente
  // ---------------------------------------------------------------------------------------

  "Document.empty" should "accept a root without children" in {
    // §8.2: Der Kern erlaubt eine leere Wurzel. Dass eine editierbare Flaeche einen Paragraph
    // braucht, ist eine Regel des Rich-Text-Profils, nicht des Kerns.
    val document = built(Document.empty(schema, root))

    document.size shouldBe 1
    document.rootId shouldBe root
    document.childrenOf(root) shouldBe empty
  }

  "A built document" should "derive the parent index from the child lists" in {
    val text     = TextNode(id("t1"), "Hallo")
    val caption  = CaptionNode.of(id("c1"), Vector(text.id))
    val document = nested(Vector(caption.id), caption, text)

    document.parentOf(text.id) shouldBe Some(caption.id)
    document.parentOf(caption.id) shouldBe Some(root)
    document.parentOf(root) shouldBe None
  }

  it should "traverse in document order, not in id order" in {
    // Gegenprobe zu §11: Dokumentordnung folgt dem Baum, niemals einem lexikographischen
    // ID-Vergleich. Die IDs sind hier absichtlich gegenlaeufig sortiert.
    val second   = TextNode(id("a-second"), "zwei")
    val first    = TextNode(id("z-first"), "eins")
    val document = built(documentOf(Vector(first, second)))

    document.subtreeOf(root).toVector shouldBe Vector(root, first.id, second.id)
  }

  it should "report a diagnostic path from the root to a nested node" in {
    val text     = TextNode(id("t1"), "Hallo")
    val caption  = CaptionNode.of(id("c1"), Vector(text.id))
    val document = nested(Vector(caption.id), caption, text)

    document.pathTo(text.id).render shouldBe "<root>#root#c1#t1"
    document.ancestorsOf(text.id) shouldBe Vector(caption.id, root)
  }

  it should "resolve the descriptor of a foreign node without any core change" in {
    val sticker  = StickerNode(id("s1"), "*")
    val document = built(documentOf(Vector(sticker)))

    document.typeOf(sticker.id).map(_.typeId) shouldBe Some(StickerNode.typeId)
  }

  it should "compare by root and nodes, not by parent index identity" in {
    val text = TextNode(id("t1"), "Hallo")
    val one  = built(documentOf(Vector(text)))
    val two  = built(documentOf(Vector(text)))

    one shouldBe two
    one.hashCode() shouldBe two.hashCode()
  }

  // ---------------------------------------------------------------------------------------
  // Verletzte Invarianten. Jede Meldung nennt Pfad, ID und Grund (§8.2).
  // ---------------------------------------------------------------------------------------

  private def violationsOf(rootId: NodeId, nodes: Vector[EditorNode]): Vector[Violation] =
    Document.build(schema, rootId, nodes) match
      case Left(violations) => violations
      case Right(_)         => fail("Ungueltiges Dokument wurde akzeptiert")

  "Validation" should "reject duplicate node ids" in {
    val violations = violationsOf(
      root,
      Vector(
        RootNode(root, Vector(id("t1"))),
        TextNode(id("t1"), "eins"),
        TextNode(id("t1"), "zwei")
      )
    )

    violations shouldBe Vector(Violation.DuplicateNodeId(id("t1")))
    violations.head.render shouldBe "<root>#t1: Die NodeId `t1` kommt mehrfach vor."
  }

  it should "reject a missing root" in {
    violationsOf(id("absent"), Vector(TextNode(id("t1"), "Hallo"))) shouldBe
      Vector(Violation.MissingRoot(id("absent")))
  }

  it should "reject a root that cannot hold children" in {
    violationsOf(root, Vector(TextNode(root, "Ich bin kein Container"))) shouldBe
      Vector(Violation.RootNotAContainer(root))
  }

  it should "reject a root that is referenced as a child" in {
    // §8.2: Root-Reparenting ist ausgeschlossen.
    val violations = violationsOf(
      root,
      Vector(
        RootNode(root, Vector(id("c1"))),
        CaptionNode.of(id("c1"), Vector(root))
      )
    )

    violations shouldBe Vector(Violation.RootIsChild(root, id("c1")))
  }

  it should "reject a dangling child reference" in {
    val violations = violationsOf(root, Vector(RootNode(root, Vector(id("ghost")))))

    violations shouldBe Vector(Violation.MissingChild(root, id("ghost"), 0))
    violations.head.path.render shouldBe "<root>#root.children[0]"
  }

  it should "reject the same child listed twice by one parent" in {
    val violations = violationsOf(
      root,
      Vector(
        RootNode(root, Vector(id("t1"), id("t1"))),
        TextNode(id("t1"), "Hallo")
      )
    )

    violations shouldBe Vector(Violation.DuplicateChild(root, id("t1"), 1))
  }

  it should "reject a node with two parents" in {
    val violations = violationsOf(
      root,
      Vector(
        RootNode(root, Vector(id("c1"), id("c2"))),
        CaptionNode.of(id("c1"), Vector(id("t1"))),
        CaptionNode.of(id("c2"), Vector(id("t1"))),
        TextNode(id("t1"), "geteilt")
      )
    )

    violations shouldBe Vector(Violation.MultipleParents(id("t1"), id("c1"), id("c2")))
  }

  it should "reject a cycle even when it is detached from the root" in {
    // Ein abgehaengter Zyklus waere auch als Unerreichbarkeit meldbar -- das waere wahr, aber
    // irrefuehrend. Die Zyklusstufe laeuft deshalb ueber alle Knoten, nicht nur die erreichbaren.
    val violations = violationsOf(
      root,
      Vector(
        RootNode(root, Vector.empty),
        CaptionNode.of(id("a"), Vector(id("b"))),
        CaptionNode.of(id("b"), Vector(id("a")))
      )
    )

    violations should contain(Violation.Cycle(id("a")))
    violations.foreach(_ shouldBe a[Violation.Cycle])
  }

  it should "reject nodes that are unreachable from the root" in {
    val violations = violationsOf(
      root,
      Vector(
        RootNode(root, Vector.empty),
        TextNode(id("orphan"), "verwaist")
      )
    )

    violations shouldBe Vector(Violation.UnreachableNode(id("orphan")))
  }

  it should "reject a node the schema does not know" in {
    val violations = violationsOf(
      root,
      Vector(
        RootNode(root, Vector(id("u1"))),
        UnregisteredNode(id("u1"))
      )
    )

    violations shouldBe Vector(Violation.UnknownNodeType(id("u1"), "UnregisteredNode"))
  }

  it should "reject a container whose descriptor is not an ElementNodeType" in {
    // §8.1: Ohne `withChildren` kann der Kern den Container nicht generisch umbauen.
    val violations = violationsOf(
      root,
      Vector(
        RootNode(root, Vector(id("bad"))),
        UndescribedContainer(id("bad"), Vector.empty)
      )
    )

    violations shouldBe Vector(
      Violation.MissingElementDescriptor(id("bad"), UndescribedContainer.typeId)
    )
  }

  it should "surface a rule of a foreign descriptor" in {
    val violations = violationsOf(
      root,
      Vector(
        RootNode(root, Vector(id("c1"))),
        CaptionNode(id("c1"), Vector.empty, language = "", visible = true)
      )
    )

    violations shouldBe Vector(
      Violation.NodeRejected(
        id("c1"),
        "Eine Caption braucht eine Sprachangabe.",
        DiagnosticPath.node("root").node("c1")
      )
    )
  }

  it should "stop after the first failing stage instead of cascading" in {
    // Eine ins Leere zeigende Kindreferenz macht Erreichbarkeit und Schema zwangslaeufig
    // ebenfalls unstimmig. Gemeldet wird nur die Ursache.
    val violations = violationsOf(
      root,
      Vector(
        RootNode(root, Vector(id("ghost"))),
        UnregisteredNode(id("unreachable"))
      )
    )

    violations shouldBe Vector(Violation.MissingChild(root, id("ghost"), 0))
  }

  // ---------------------------------------------------------------------------------------
  // Tiefe Baeume
  // ---------------------------------------------------------------------------------------

  "Validation and traversal" should "survive a tree far deeper than the JavaScript stack" in {
    // §8.3: Traversierung ist iterativ, damit importierte tiefe Baeume eine Diagnose liefern
    // statt eines Stackueberlaufs. Node.js schafft rund 11 000 Rekursionsebenen -- eine
    // rekursive Implementierung wuerde hier zuverlaessig scheitern.
    val depth = 25000
    val chain = Vector.tabulate(depth)(level => id(s"level-$level"))
    val nodes = RootNode(root, Vector(chain.head)) +:
      chain.zipWithIndex.map { (nodeId, level) =>
        val children = if level + 1 < depth then Vector(chain(level + 1)) else Vector.empty
        CaptionNode.of(nodeId, children)
      }

    val document = built(Document.build(schema, root, nodes))

    document.size shouldBe depth + 1
    document.subtreeOf(root).size shouldBe depth + 1
    document.ancestorsOf(chain.last).size shouldBe depth
  }

  // ---------------------------------------------------------------------------------------
  // Rekonstruktionsvertraege und ID-Stabilitaet
  // ---------------------------------------------------------------------------------------

  "rekey" should "preserve every other field of a foreign node" in {
    val caption = CaptionNode(id("c1"), Vector(id("t1")), language = "fr", visible = false)

    val moved = CaptionNode.rekey(caption, id("c2"))

    moved.id shouldBe id("c2")
    moved.children shouldBe Vector(id("t1"))
    moved.language shouldBe "fr"
    moved.visible shouldBe false
  }

  "withChildren" should "preserve every other field of a foreign node" in {
    val caption = CaptionNode(id("c1"), Vector(id("t1")), language = "fr", visible = false)

    val rewired = CaptionNode.withChildren(caption, Vector(id("t2"), id("t3")))

    rewired.children shouldBe Vector(id("t2"), id("t3"))
    rewired.id shouldBe id("c1")
    rewired.language shouldBe "fr"
    rewired.visible shouldBe false
  }

  "Node ids" should "stay stable while an older snapshot remains readable" in {
    // §8.3: Server- und Client-Snapshot desselben Dokuments behalten dieselben IDs. Ein
    // aelterer Snapshot bleibt gueltig; entfernte Teilbaeume leben nur dort weiter.
    val kept    = TextNode(id("t1"), "bleibt")
    val removed = TextNode(id("t2"), "verschwindet")

    val before = built(documentOf(Vector(kept, removed)))
    val after  = built(documentOf(Vector(kept)))

    after.contains(removed.id) shouldBe false
    before.contains(removed.id) shouldBe true
    before.node(kept.id) shouldBe after.node(kept.id)
    before.subtreeOf(root).toVector shouldBe Vector(root, kept.id, removed.id)
  }

  // ---------------------------------------------------------------------------------------
  // Deterministisch erzeugte Baeume
  // ---------------------------------------------------------------------------------------

  "Randomly generated valid trees" should "satisfy every invariant the validator claims" in {
    // Kein Scalacheck: ein fester Seed genuegt fuer Reproduzierbarkeit und erspart dem Kern
    // eine weitere Testabhaengigkeit. Geprueft wird gegen unabhaengig nachgerechnete
    // Erwartungen, nicht gegen dieselbe Logik.
    val random = new Random(20260909L)

    (1 to 50).foreach { round =>
      val nodes    = generateTree(random, size = 40)
      val document = built(Document.build(schema, root, nodes))

      withClue(s"Runde $round: ") {
        document.size shouldBe nodes.size

        // Erreichbarkeit, unabhaengig nachgerechnet.
        document.subtreeOf(root).toSet shouldBe nodes.map(_.id).toSet

        // Der Elternindex stimmt mit den Kindlisten ueberein -- in beide Richtungen.
        nodes.foreach { node =>
          document.childrenOf(node.id).foreach { child =>
            document.parentOf(child) shouldBe Some(node.id)
          }
        }
        document.ids.filter(_ != root).foreach { child =>
          val parent = document.parentOf(child).getOrElse(fail(s"$child ohne Eltern"))
          document.childrenOf(parent) should contain(child)
        }

        // Genau die Wurzel hat keine Eltern.
        document.ids.count(document.parentOf(_).isEmpty) shouldBe 1
      }
    }
  }

  /** Baut einen zufaelligen, per Konstruktion gueltigen Baum: jeder neue Knoten haengt an einem
    * bereits vorhandenen Container. Dadurch sind Zyklen, Mehrfacheltern und Waisen ausgeschlossen
    * -- der Test prueft die Validierung, nicht den Generator.
    */
  private def generateTree(random: Random, size: Int): Vector[EditorNode] =
    var containers = Vector(root)
    var children   = Map(root -> Vector.empty[NodeId])
    var leaves     = Vector.empty[EditorNode]

    (1 to size).foreach { index =>
      val parent = containers(random.nextInt(containers.length))
      val nodeId = id(s"n$index")
      children = children.updated(parent, children(parent) :+ nodeId)
      if random.nextBoolean() then
        containers = containers :+ nodeId
        children = children.updated(nodeId, Vector.empty)
      else leaves = leaves :+ TextNode(nodeId, s"Text $index")
    }

    val elements = containers.map { nodeId =>
      if nodeId == root then RootNode(root, children(root))
      else CaptionNode.of(nodeId, children(nodeId))
    }
    elements ++ leaves
}
