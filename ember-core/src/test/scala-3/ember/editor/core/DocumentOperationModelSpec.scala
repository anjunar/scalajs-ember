package ember.editor.core

import ember.editor.foreign.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

/** Zufaellige Operationsfolgen gegen ein unabhaengiges Referenzmodell (P03).
  *
  * ==Warum es diese Suite gibt==
  *
  * [[OperationEngine]] baut seine Ergebnisse gueltig '''per Konstruktion''' und validiert nicht
  * nach jedem Schritt -- eine Vollvalidierung pro Tastendruck waere linear in der Dokumentgroesse
  * und genau das, was §8.2 ausschliesst. Diese Konstruktion ist damit eine Behauptung. Hier wird
  * sie geprueft, und zwar so, wie §8.2 es verlangt: gegen eine unabhaengige Vollvalidierung.
  *
  * Drei voneinander unabhaengige Instanzen pruefen nach '''jedem''' Schritt:
  *
  *   1. [[DocumentValidator]], indem das Ergebnis von Grund auf neu gebaut wird. Findet alles, was
  *      die Operationen an Invarianten kaputtmachen koennten.
  *   2. Ein Referenzmodell aus schlichten Maps, das dieselben Operationen mit offensichtlich
  *      korrekter, aber ineffizienter Logik nachvollzieht. Findet Ergebnisse, die zwar gueltig,
  *      aber falsch sind -- was der Validator nicht bemerken kann.
  *   3. Mitgefuehrte Punkte, die nach jedem Schritt im neuen Dokument darstellbar sein muessen.
  *      Findet Abbildungen, die auf nicht existierende Positionen zeigen.
  *
  * Fester Seed statt ScalaCheck: reproduzierbar, und der Kern bleibt ohne weitere
  * Testabhaengigkeit.
  */
final class DocumentOperationModelSpec extends AnyFlatSpec with Matchers {

  private val schema = Schema.unsafe(RootNode, TextNode, CaptionNode, StickerNode)

  private def id(value: String): NodeId = NodeId(value)

  private val root = id("root")

  /** Das Referenzmodell: Kindlisten und Texte, sonst nichts.
    *
    * Bewusst dumm gehalten. Es teilt keine Zeile Code mit dem Kern -- waere es aus denselben
    * Bausteinen gebaut, wuerde es dieselben Fehler machen und nichts beweisen.
    */
  private final case class Model(
      children: Map[String, Vector[String]],
      texts: Map[String, String],
      marks: Map[String, MarkSet]
  ):

    def contains(nodeId: String): Boolean = children.contains(nodeId) || texts.contains(nodeId)

    def parentOf(nodeId: String): Option[String] =
      children.collectFirst { case (parent, kids) if kids.contains(nodeId) => parent }

    def subtree(nodeId: String): Set[String] =
      children.get(nodeId) match
        case Some(kids) => kids.flatMap(subtree).toSet + nodeId
        case None       => Set(nodeId)

    def insertText(parent: String, index: Int, nodeId: String, text: String): Model =
      copy(
        children = children.updated(parent, children(parent).patch(index, Vector(nodeId), 0)),
        texts = texts.updated(nodeId, text),
        // Ein neuer Textlauf traegt eine leere, aber vorhandene Markmenge. Fehlte der Eintrag,
        // wichen Modell und Dokument in einer Map ab, ohne dass fachlich etwas falsch waere.
        marks = marks.updated(nodeId, MarkSet.empty)
      )

    def insertContainer(parent: String, index: Int, nodeId: String): Model =
      copy(
        children = children
          .updated(parent, children(parent).patch(index, Vector(nodeId), 0))
          .updated(nodeId, Vector.empty)
      )

    def remove(nodeId: String): Model =
      val gone   = subtree(nodeId)
      val parent = parentOf(nodeId).get
      copy(
        children = (children -- gone).updated(parent, children(parent).filterNot(_ == nodeId)),
        texts = texts -- gone,
        marks = marks -- gone
      )

    def move(nodeId: String, newParent: String, index: Int): Model =
      val oldParent = parentOf(nodeId).get
      val detached  = children(oldParent).filterNot(_ == nodeId)
      val withoutIt = children.updated(oldParent, detached)
      copy(children =
        withoutIt.updated(newParent, withoutIt(newParent).patch(index, Vector(nodeId), 0))
      )

    def splice(nodeId: String, start: Int, deleteCount: Int, inserted: String): Model =
      val old = texts(nodeId)
      copy(texts =
        texts.updated(
          nodeId,
          old.substring(0, start) + inserted + old.substring(start + deleteCount)
        )
      )

    def split(nodeId: String, at: Int, newId: String): Model =
      val old    = texts(nodeId)
      val parent = parentOf(nodeId).get
      val index  = children(parent).indexOf(nodeId)
      copy(
        children = children.updated(parent, children(parent).patch(index + 1, Vector(newId), 0)),
        texts = texts.updated(nodeId, old.substring(0, at)).updated(newId, old.substring(at)),
        marks = marks.updated(newId, marks.getOrElse(nodeId, MarkSet.empty))
      )

    def merge(left: String, right: String): Model =
      val parent = parentOf(left).get
      copy(
        children = children.updated(parent, children(parent).filterNot(_ == right)),
        texts = (texts - right).updated(left, texts(left) + texts(right)),
        marks = marks - right
      )

  private def modelOf(document: Document): Model =
    Model(
      children = document.nodes.collect { case element: ElementNode =>
        element.id.value -> element.children.map(_.value)
      }.toMap,
      texts = document.nodes.collect { case text: TextNode => text.id.value -> text.text }.toMap,
      marks = document.nodes.collect { case text: TextNode => text.id.value -> text.marks }.toMap
    )

  private val start: Document = Document.unsafe(
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

  "Random operation sequences" should "keep the document valid, correct and mappable" in {
    val random = new Random(20260909L)

    (1 to 30).foreach { round =>
      var document  = start
      var model     = modelOf(start)
      var tracked   = Vector(Point.textBefore(id("t1"), 2), Point.childrenBefore(root, 1))
      var fresh     = 0
      var performed = 0

      (1 to 40).foreach { step =>
        val (operation, applyToModel) =
          choose(random, document, () => { fresh += 1; s"g$round-$fresh" })

        operation.foreach { chosen =>
          document.applyOperation(chosen) match
            case Left(error) =>
              fail(s"Runde $round, Schritt $step: ${chosen} abgewiesen mit ${error.render}")

            case Right(result) =>
              performed += 1
              val clue = s"Runde $round, Schritt $step, $chosen: "

              withClue(clue + "unabhaengige Vollvalidierung: ") {
                Document.build(schema, root, result.document.nodes.toVector) match
                  case Left(violations) => fail(violations.map(_.render).mkString("\n"))
                  case Right(_)         => ()
              }

              model = applyToModel(model)

              withClue(clue + "Referenzmodell: ") {
                modelOf(result.document) shouldBe model
              }

              withClue(clue + "Elternindex: ") {
                result.document.ids.filter(_ != root).foreach { node =>
                  val parent = result.document.parentOf(node).getOrElse(fail(s"$node ohne Eltern"))
                  result.document.childrenOf(parent) should contain(node)
                }
              }

              // Jeder mitgefuehrte Punkt muss im neuen Dokument darstellbar sein -- ganz gleich,
              // ob er erhalten blieb oder auf eine Grenze zurueckfiel.
              tracked = tracked.map(result.mapping.map(_).point)
              withClue(clue + "mitgefuehrte Punkte: ") {
                tracked.flatMap(_.validateIn(result.document)) shouldBe empty
              }

              document = result.document
        }
      }

      withClue(s"Runde $round: ") { performed should be > 10 }
    }
  }

  /** Eine gewaehlte Operation samt der Aenderung, die das Referenzmodell dazu nachvollzieht. */
  private type Step = (Option[Operation], Model => Model)

  /** Fuer diese Runde gibt es kein gueltiges Ziel dieser Art. */
  private val skip: Step = (None, (model: Model) => model)

  private def step(operation: Operation)(update: Model => Model): Step = (Some(operation), update)

  /** Waehlt eine auf dieses Dokument anwendbare Operation und die passende Modellaenderung.
    *
    * Liefert [[skip]], wenn fuer die gewuerfelte Art gerade kein gueltiges Ziel existiert -- etwa
    * ein Merge, wenn es keine zwei benachbarten Textlaeufe mit gleichen Marks gibt. Ungueltige
    * Operationen erzeugt der Generator bewusst nicht: deren Abweisung prueft `OperationSpec`
    * gezielt, hier geht es um die Korrektheit der gelungenen.
    */
  private def choose(random: Random, document: Document, nextId: () => String): Step =
    val containers = document.nodes.collect { case element: ElementNode => element }.toVector
    val texts      = document.nodes.collect { case text: TextNode => text }.toVector
    val removable  = document.ids.filter(_ != root).toVector.sorted

    def pick[A](candidates: Vector[A]): Option[A] =
      Option.when(candidates.nonEmpty)(candidates(random.nextInt(candidates.length)))

    random.nextInt(6) match

      case 0 => // Textlauf einfuegen
        pick(containers).fold(skip) { parent =>
          val index   = random.nextInt(parent.children.length + 1)
          val created = nextId()
          val content = s"x$created"
          step(Operation.insertLeaf(parent.id, index, TextNode(NodeId(created), content)))(
            _.insertText(parent.id.value, index, created, content)
          )
        }

      case 1 => // Container einfuegen
        pick(containers).fold(skip) { parent =>
          val index   = random.nextInt(parent.children.length + 1)
          val created = nextId()
          step(Operation.insertLeaf(parent.id, index, CaptionNode.of(NodeId(created))))(
            _.insertContainer(parent.id.value, index, created)
          )
        }

      case 2 => // Entfernen
        pick(removable).fold(skip)(target => step(Operation.Remove(target))(_.remove(target.value)))

      case 3 => // Verschieben
        val movable = for
          node      <- removable
          newParent <- containers.map(_.id)
          if !document.subtreeOf(node).contains(newParent)
        yield (node, newParent)

        pick(movable).fold(skip) { (node, newParent) =>
          // Die Zielposition zaehlt in der Liste nach der Herausnahme -- beim Umsortieren im
          // selben Parent ist die Kapazitaet deshalb um eins kleiner.
          val capacity =
            if document.parentOf(node).contains(newParent) then
              document.childrenOf(newParent).length - 1
            else document.childrenOf(newParent).length
          val index = random.nextInt(capacity + 1)
          step(Operation.Move(node, newParent, index))(_.move(node.value, newParent.value, index))
        }

      case 4 => // Text aendern
        pick(texts).fold(skip) { text =>
          // Nur BMP-Zeichen im Generator, deshalb faellt kein Offset in ein Surrogatpaar.
          val at          = random.nextInt(text.text.length + 1)
          val inserted    = "ab".take(random.nextInt(3))
          val deletable   = math.max(0, math.min(text.text.length - at, 2))
          val deleteCount = if deletable == 0 then 0 else random.nextInt(deletable + 1)
          val unchanged   = inserted == text.text.substring(at, at + deleteCount)

          // Ein No-op liefert dasselbe Dokument und einen leeren ChangeSet. Das ist richtig,
          // waere hier aber ein Leerlauf -- der Generator soll echte Aenderungen erzeugen.
          if unchanged then skip
          else
            step(Operation.SpliceText(text.id, at, deleteCount, inserted))(
              _.splice(text.id.value, at, deleteCount, inserted)
            )
        }

      case 5 => // Textlauf teilen
        pick(texts).fold(skip) { text =>
          val at      = random.nextInt(text.text.length + 1)
          val created = nextId()
          step(Operation.SplitText(text.id, at, NodeId(created)))(
            _.split(text.id.value, at, created)
          )
        }

      case _ => // Benachbarte Textlaeufe zusammenfuehren
        val mergeable = for
          parent    <- containers
          pair      <- parent.children.sliding(2).collect { case Vector(l, r) => (l, r) }.toVector
          leftText  <- document.node(pair._1).collect { case text: TextNode => text }.toVector
          rightText <- document.node(pair._2).collect { case text: TextNode => text }.toVector
          if leftText.marks == rightText.marks
        yield (leftText.id, rightText.id)

        pick(mergeable).fold(skip) { (left, right) =>
          step(Operation.MergeText(left, right))(_.merge(left.value, right.value))
        }
}
