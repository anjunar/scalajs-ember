package ember.editor.core

import ember.editor.foreign.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Die Abbildungsregeln aus Architektur §11, Regel fuer Regel.
  *
  * Diese Suite ist der Grund, warum Selection, Bookmarks und spaeter die History ueberhaupt
  * funktionieren koennen. Wenn hier etwas bricht, springt im Editor der Cursor.
  */
final class PositionMappingSpec extends AnyFlatSpec with Matchers {

  private val schema  = Schema.unsafe(RootNode, TextNode, CaptionNode, StickerNode)
  private val support = SelectionSupport.core

  private def id(value: String): NodeId = NodeId(value)

  private val root = id("root")

  /** ```
    * root
    *  +- c1  +- t1 "Hallo"
    *  |      +- t2 "Welt"
    *  +- t3 "Ende"
    * ```
    */
  private val base: Document = Document.unsafe(
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

  private def run(document: Document, operation: Operation): OperationResult =
    document.applyOperation(operation) match
      case Right(result) => result
      case Left(error)   => fail(s"Operation abgewiesen: ${error.render}")

  private def mapPoint(operation: Operation, point: Point): MappedPoint =
    run(base, operation).mapping.map(point)

  // ---------------------------------------------------------------------------------------
  // InsertText: "Punkte davor bleiben; Punkte danach wandern; genau p entscheidet die Affinitaet."
  // ---------------------------------------------------------------------------------------

  "Inserting text" should "leave earlier points alone" in {
    mapPoint(Operation.SpliceText(id("t1"), 3, 0, "XX"), Point.textBefore(id("t1"), 1)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 1))
  }

  it should "shift later points by the inserted length" in {
    mapPoint(Operation.SpliceText(id("t1"), 1, 0, "XX"), Point.textBefore(id("t1"), 4)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 6))
  }

  it should "let affinity decide exactly at the insertion point" in {
    // Das ist der Fall, fuer den es Affinitaet ueberhaupt gibt. Ein Caret, der an genau dieser
    // Stelle steht, muss beim Tippen mitwandern (After) oder liegen bleiben (Before) -- eines
    // von beiden fest zu verdrahten waere in der Haelfte der Faelle falsch.
    val before = mapPoint(Operation.SpliceText(id("t1"), 2, 0, "XX"), Point.textBefore(id("t1"), 2))
    val after  = mapPoint(Operation.SpliceText(id("t1"), 2, 0, "XX"), Point.textAfter(id("t1"), 2))

    before shouldBe MappedPoint.Preserved(Point.textBefore(id("t1"), 2))
    after shouldBe MappedPoint.Preserved(Point.textAfter(id("t1"), 4))
  }

  it should "not touch points in other nodes" in {
    mapPoint(Operation.SpliceText(id("t1"), 0, 0, "XX"), Point.textBefore(id("t2"), 2)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t2"), 2))
  }

  // ---------------------------------------------------------------------------------------
  // DeleteText: "Punkte im geloeschten Bereich fallen auf a; Punkte dahinter verlieren b-a."
  // ---------------------------------------------------------------------------------------

  "Deleting text" should "collapse points inside the deleted range onto its start" in {
    mapPoint(Operation.SpliceText(id("t1"), 1, 3, ""), Point.textBefore(id("t1"), 3)) shouldBe
      MappedPoint.Displaced(Point.textBefore(id("t1"), 1))
  }

  it should "shift points behind the range" in {
    mapPoint(Operation.SpliceText(id("t1"), 1, 2, ""), Point.textBefore(id("t1"), 5)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 3))
  }

  it should "keep points before the range" in {
    mapPoint(Operation.SpliceText(id("t1"), 2, 2, ""), Point.textBefore(id("t1"), 1)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 1))
  }

  it should "mark collapsed points as displaced, not merely moved" in {
    // Der Unterschied entscheidet spaeter, ob ein Upload-Bookmark einfuegen darf oder abbricht.
    mapPoint(
      Operation.SpliceText(id("t1"), 0, 5, "Neu"),
      Point.textBefore(id("t1"), 3)
    ).isPreserved shouldBe
      false
  }

  "Replacing a range" should "compose deletion and insertion in that order" in {
    // Punkt hinter dem ersetzten Bereich: erst -deleteCount, dann +insertedLength.
    mapPoint(Operation.SpliceText(id("t1"), 0, 2, "XYZ"), Point.textBefore(id("t1"), 4)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 5))
  }

  // ---------------------------------------------------------------------------------------
  // SplitText: "Linke ID bleibt. Rechts liegende Punkte wechseln zur neuen rechten ID."
  // ---------------------------------------------------------------------------------------

  "Splitting text" should "keep points left of the cut on the left node" in {
    mapPoint(Operation.SplitText(id("t1"), 2, id("t1b")), Point.textBefore(id("t1"), 1)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 1))
  }

  it should "move points right of the cut onto the new node" in {
    mapPoint(Operation.SplitText(id("t1"), 2, id("t1b")), Point.textBefore(id("t1"), 4)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1b"), 2))
  }

  it should "let affinity decide exactly at the cut" in {
    mapPoint(Operation.SplitText(id("t1"), 2, id("t1b")), Point.textBefore(id("t1"), 2)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 2))
    mapPoint(Operation.SplitText(id("t1"), 2, id("t1b")), Point.textAfter(id("t1"), 2)) shouldBe
      MappedPoint.Preserved(Point.textAfter(id("t1b"), 0))
  }

  it should "shift child offsets in the parent past the new sibling" in {
    // t1 steht auf 0, das neue Geschwister kommt auf 1. Eine Kindposition dahinter wandert.
    mapPoint(
      Operation.SplitText(id("t1"), 2, id("t1b")),
      Point.childrenBefore(id("c1"), 2)
    ) shouldBe
      MappedPoint.Preserved(Point.childrenBefore(id("c1"), 3))
  }

  // ---------------------------------------------------------------------------------------
  // MergeText: "Rechte Punkte wechseln zur linken ID plus urspruenglicher linker Laenge."
  // ---------------------------------------------------------------------------------------

  "Merging text" should "rebase points of the right node onto the left one" in {
    mapPoint(Operation.MergeText(id("t1"), id("t2")), Point.textBefore(id("t2"), 2)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 7))
  }

  it should "treat the rebase as preserved, not displaced" in {
    // Der Inhalt ist nicht verschwunden, er steht nur woanders. Ein Bookmark darauf bleibt gueltig.
    mapPoint(
      Operation.MergeText(id("t1"), id("t2")),
      Point.textBefore(id("t2"), 0)
    ).isPreserved shouldBe
      true
  }

  it should "leave points of the left node untouched" in {
    mapPoint(Operation.MergeText(id("t1"), id("t2")), Point.textBefore(id("t1"), 3)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 3))
  }

  // ---------------------------------------------------------------------------------------
  // Insert/Remove Child
  // ---------------------------------------------------------------------------------------

  "Inserting a child" should "shift later child offsets and honour affinity at the boundary" in {
    val operation = Operation.insertLeaf(id("c1"), 1, TextNode(id("mid"), "M"))

    mapPoint(operation, Point.childrenBefore(id("c1"), 0)) shouldBe
      MappedPoint.Preserved(Point.childrenBefore(id("c1"), 0))
    mapPoint(operation, Point.childrenBefore(id("c1"), 2)) shouldBe
      MappedPoint.Preserved(Point.childrenBefore(id("c1"), 3))
    mapPoint(operation, Point.childrenBefore(id("c1"), 1)) shouldBe
      MappedPoint.Preserved(Point.childrenBefore(id("c1"), 1))
    mapPoint(operation, Point.childrenAfter(id("c1"), 1)) shouldBe
      MappedPoint.Preserved(Point.childrenAfter(id("c1"), 2))
  }

  "Removing a subtree" should "collapse inner points onto the surviving boundary in the parent" in {
    // §11: "Punkte innerhalb fallen auf die erhaltene Einfuegegrenze im Parent zurueck."
    val result = run(base, Operation.Remove(id("c1")))

    result.mapping.map(Point.textBefore(id("t1"), 3)) shouldBe
      MappedPoint.Displaced(Point.childrenBefore(root, 0))
    result.mapping.map(Point.childrenBefore(id("c1"), 1)) shouldBe
      MappedPoint.Displaced(Point.childrenBefore(root, 0))
  }

  it should "shift sibling offsets after the removed position" in {
    run(base, Operation.Remove(id("c1"))).mapping.map(Point.childrenBefore(root, 2)) shouldBe
      MappedPoint.Preserved(Point.childrenBefore(root, 1))
  }

  it should "leave points outside the subtree alone" in {
    run(base, Operation.Remove(id("c1"))).mapping.map(Point.textBefore(id("t3"), 2)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t3"), 2))
  }

  // ---------------------------------------------------------------------------------------
  // Move: "Punkte in ueberlebenden Nodes behalten ID und Offset; alte und neue Parent-Offsets
  // werden komponiert gemappt."
  // ---------------------------------------------------------------------------------------

  "Moving a subtree" should "keep points inside it unchanged" in {
    mapPoint(Operation.Move(id("c1"), root, 1), Point.textBefore(id("t1"), 3)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 3))
  }

  it should "compose the offsets of both parents" in {
    // t1 verlaesst c1 (Position 0) und landet in root auf Position 0. Eine Kindposition in c1
    // hinter der Entnahmestelle rutscht herunter, eine in root ab der Einfuegestelle hinauf.
    val result = run(base, Operation.Move(id("t1"), root, 0))

    result.mapping.map(Point.childrenBefore(id("c1"), 2)) shouldBe
      MappedPoint.Preserved(Point.childrenBefore(id("c1"), 1))
    result.mapping.map(Point.childrenAfter(root, 0)) shouldBe
      MappedPoint.Preserved(Point.childrenAfter(root, 1))
  }

  it should "apply removal before insertion when reordering inside one parent" in {
    // Der Fall mit dem Off-by-one (P03, Risiken): beide Verschiebungen treffen denselben
    // Parent, und die Reihenfolge entscheidet. `[t1, t2]` wird zu `[t2, t1]`.
    //
    // Eine Kindposition ist keine Nummer, die stehen bleibt, sondern eine Grenze zwischen
    // Geschwistern -- und welche Grenze gemeint ist, sagt die Affinitaet. Offset 2 mit
    // `Before` klebt an t2, also am letzten Kind der alten Liste. Nach dem Umsortieren steht
    // t2 vorne, und die Position unmittelbar dahinter ist Offset 1. Der Punkt folgt seinem
    // Inhalt statt seiner Nummer -- genau das soll er.
    val result = run(base, Operation.Move(id("t1"), id("c1"), 1))

    result.mapping.map(Point.childrenBefore(id("c1"), 2)) shouldBe
      MappedPoint.Preserved(Point.childrenBefore(id("c1"), 1))

    // Mit `After` gibt es vor der Aenderung nichts, woran der Punkt kleben koennte -- er steht
    // am Listenende. Er bleibt am Listenende.
    result.mapping.map(Point.childrenAfter(id("c1"), 2)) shouldBe
      MappedPoint.Preserved(Point.childrenAfter(id("c1"), 2))

    // Gegenprobe an der Entnahmestelle: Offset 0 mit `Before` klebt an nichts und bleibt vorn.
    result.mapping.map(Point.childrenBefore(id("c1"), 0)) shouldBe
      MappedPoint.Preserved(Point.childrenBefore(id("c1"), 0))
  }

  // ---------------------------------------------------------------------------------------
  // Replace
  // ---------------------------------------------------------------------------------------

  "Replacing a node" should "preserve text points the replacement can still represent" in {
    val marked = TextNode(id("t1"), "Hallo", MarkSet.of(NamedMark("strong")))

    mapPoint(Operation.Replace(id("t1"), marked), Point.textBefore(id("t1"), 3)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 3))
  }

  it should "fall back to the replace boundary when the offset no longer exists" in {
    // §11 verbietet eine heuristische Zuordnung nach Textgleichheit -- geprueft wird nur, ob
    // der Offset im Ersatz ueberhaupt noch eine Position bezeichnet.
    mapPoint(
      Operation.Replace(id("t1"), TextNode(id("t1"), "Hi")),
      Point.textBefore(id("t1"), 4)
    ) shouldBe
      MappedPoint.Displaced(Point.childrenBefore(id("c1"), 0))
  }

  // ---------------------------------------------------------------------------------------
  // Komposition
  // ---------------------------------------------------------------------------------------

  "Composed mappings" should "chain in order" in {
    val result = base
      .applyAll(
        Seq(Operation.SpliceText(id("t1"), 0, 0, "XX"), Operation.SpliceText(id("t1"), 0, 0, "YY"))
      )
      .getOrElse(fail("Folge abgewiesen"))

    result.mapping.map(Point.textBefore(id("t1"), 1)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 5))
  }

  it should "keep displacement contagious" in {
    // Was in Schritt eins verschwunden ist, kann in Schritt zwei nicht wieder auftauchen.
    // Ohne diese Regel meldete eine Folge aus Loeschen und Einfuegen ein Bookmark faelschlich
    // als gueltig.
    val result = base
      .applyAll(
        Seq(
          Operation.Remove(id("t1")),
          Operation.insertLeaf(id("c1"), 0, TextNode(id("t9"), "Neu"))
        )
      )
      .getOrElse(fail("Folge abgewiesen"))

    result.mapping.map(Point.textBefore(id("t1"), 2)).isPreserved shouldBe false
  }

  it should "treat identity as neutral" in {
    val mapping = PositionMapping.identity andThen PositionMapping.identity

    mapping.map(Point.textBefore(id("t1"), 2)) shouldBe
      MappedPoint.Preserved(Point.textBefore(id("t1"), 2))
  }

  // ---------------------------------------------------------------------------------------
  // Dokumentordnung
  // ---------------------------------------------------------------------------------------

  "Document order" should "follow the tree, never the ids" in {
    // §11: kein lexikographischer ID-Vergleich. `c1` < `t3` waere hier zufaellig richtig --
    // deshalb prueft der Gegenfall mit absichtlich gegenlaeufigen IDs.
    val reversed = Document.unsafe(
      schema,
      root,
      Vector(
        RootNode(root, Vector(id("zzz"), id("aaa"))),
        TextNode(id("zzz"), "erst"),
        TextNode(id("aaa"), "dann")
      )
    )

    reversed.comparePoints(
      Point.textBefore(id("zzz"), 0),
      Point.textBefore(id("aaa"), 0)
    ) should be < 0
  }

  it should "place a child boundary before everything inside that child" in {
    base.comparePoints(
      Point.childrenBefore(id("c1"), 0),
      Point.textBefore(id("t1"), 0)
    ) should be < 0
    base.comparePoints(
      Point.childrenBefore(id("c1"), 1),
      Point.textBefore(id("t1"), 5)
    ) should be > 0
  }

  "A range selection" should "report direction from the tree, keeping anchor and focus" in {
    val backward = RangeSelection(Point.textBefore(id("t3"), 2), Point.textBefore(id("t1"), 1))

    backward.direction(base) shouldBe SelectionDirection.Backward
    backward.ordered(base) shouldBe (Point.textBefore(id("t1"), 1), Point.textBefore(id("t3"), 2))
    backward.anchor shouldBe Point.textBefore(id("t3"), 2)
  }

  it should "recognise a caret as collapsed" in {
    RangeSelection.caret(Point.textBefore(id("t1"), 2)).isCollapsed shouldBe true
    RangeSelection.caret(Point.textBefore(id("t1"), 2)).direction(base) shouldBe
      SelectionDirection.Collapsed
  }

  // ---------------------------------------------------------------------------------------
  // Selection-Mapping
  // ---------------------------------------------------------------------------------------

  "Mapping a range selection" should "map anchor and focus independently and keep the direction" in {
    val result   = run(base, Operation.SpliceText(id("t1"), 0, 0, "XX"))
    val backward = RangeSelection(Point.textBefore(id("t1"), 4), Point.textBefore(id("t1"), 1))

    support.map(backward, result.mapping, result.document) shouldBe
      Some(RangeSelection(Point.textBefore(id("t1"), 6), Point.textBefore(id("t1"), 3)))
  }

  it should "give up when a point can no longer be represented" in {
    // Eine halb gueltige Auswahl waere schlimmer als keine.
    val result    = run(base, Operation.Remove(id("c1")))
    val selection = RangeSelection(Point.textBefore(id("t1"), 0), Point.textBefore(id("t1"), 3))

    support.map(selection, result.mapping, result.document) shouldBe
      Some(RangeSelection(Point.childrenBefore(root, 0), Point.childrenBefore(root, 0)))
  }

  "Mapping a node selection" should "drop removed nodes and keep moved ones" in {
    val removed   = run(base, Operation.Remove(id("t1")))
    val moved     = run(base, Operation.Move(id("t1"), root, 0))
    val selection = NodeSelection(Set(id("t1"), id("t3")))

    support.map(selection, removed.mapping, removed.document) shouldBe
      Some(NodeSelection(Set(id("t3"))))
    support.map(selection, moved.mapping, moved.document) shouldBe Some(selection)
  }

  it should "normalise descendants of already selected ancestors" in {
    NodeSelection(Set(id("c1"), id("t1"), id("t3"))).normalized(base) shouldBe
      NodeSelection(Set(id("c1"), id("t3")))
  }

  it should "vanish once nothing is left" in {
    val result = run(base, Operation.Remove(id("c1")))

    support.map(NodeSelection(Set(id("t1"))), result.mapping, result.document) shouldBe None
  }

  "Selection support" should "accept a foreign selection kind without a core change" in {
    val extended  = support.extendedWith(CellRangeSelectionMapper)
    val selection = CellRangeSelection(id("t1"), id("t2"))
    val result    = run(base, Operation.SpliceText(id("t1"), 0, 0, "X"))

    extended.map(selection, result.mapping, result.document) shouldBe Some(selection)
    extended.validate(selection, base) shouldBe empty
  }

  it should "lose a selection whose kind is not registered" in {
    // Unveraendert weiterreichen hiesse behaupten, sie zeige noch auf denselben Inhalt -- und
    // das kann niemand behaupten, der die Art nicht kennt.
    val result = run(base, Operation.SpliceText(id("t1"), 0, 0, "X"))

    support.map(
      CellRangeSelection(id("t1"), id("t2")),
      result.mapping,
      result.document
    ) shouldBe None
    support.validate(CellRangeSelection(id("t1"), id("t2")), base) should have size 1
  }

  // ---------------------------------------------------------------------------------------
  // Bookmarks
  // ---------------------------------------------------------------------------------------

  private val start = Revision.initial

  "A bookmark" should "follow a change that preserves its target" in {
    val result   = run(base, Operation.SpliceText(id("t1"), 0, 0, "XX"))
    val bookmark = Bookmark(Point.textBefore(id("t1"), 3), start)

    bookmark.resolve(RevisionMapping(start, start.next, result.mapping)) shouldBe
      Right(Point.textBefore(id("t1"), 5))
  }

  it should "expire when its target was removed" in {
    // §20: Ist das Ziel entfernt, wird die Einfuegung verworfen. Ein stiller Rueckfall auf die
    // Grenze setzte das hochgeladene Bild an einer beliebigen anderen Stelle ein.
    val result   = run(base, Operation.Remove(id("c1")))
    val bookmark = Bookmark(Point.textBefore(id("t1"), 2), start)

    bookmark.resolve(RevisionMapping(start, start.next, result.mapping)).isLeft shouldBe true
  }

  it should "expire when the mapping starts at a different revision" in {
    val result   = run(base, Operation.SpliceText(id("t1"), 0, 0, "XX"))
    val bookmark = Bookmark(Point.textBefore(id("t1"), 3), Revision(7L))

    bookmark
      .resolve(RevisionMapping(start, start.next, result.mapping))
      .swap
      .map(_.message) shouldBe Right(
      "Bookmark aus Revision 7, Abbildung beginnt bei 0."
    )
  }

  it should "offer the fallback boundary only when asked for it" in {
    val result   = run(base, Operation.Remove(id("c1")))
    val bookmark = Bookmark(Point.textBefore(id("t1"), 2), start)
    val mapping  = RevisionMapping(start, start.next, result.mapping)

    bookmark.resolve(mapping).isLeft shouldBe true
    bookmark.resolveOrFallback(mapping) shouldBe Right(Point.childrenBefore(root, 0))
  }

  "A revision mapping chain" should "compose across consecutive steps" in {
    val first  = run(base, Operation.SpliceText(id("t1"), 0, 0, "A"))
    val second = run(first.document, Operation.SpliceText(id("t1"), 0, 0, "B"))

    val chain = RevisionMapping
      .composeAll(
        Seq(
          RevisionMapping(Revision(0L), Revision(1L), first.mapping),
          RevisionMapping(Revision(1L), Revision(2L), second.mapping)
        )
      )
      .getOrElse(fail("Kette abgewiesen"))

    Bookmark(Point.textBefore(id("t1"), 0), Revision(0L)).resolve(chain) shouldBe
      Right(Point.textBefore(id("t1"), 0))
    Bookmark(Point.textAfter(id("t1"), 0), Revision(0L)).resolve(chain) shouldBe
      Right(Point.textAfter(id("t1"), 2))
  }

  it should "refuse a chain with a gap" in {
    val gapped = RevisionMapping.composeAll(
      Seq(
        RevisionMapping(Revision(0L), Revision(1L), PositionMapping.identity),
        RevisionMapping(Revision(5L), Revision(6L), PositionMapping.identity)
      )
    )

    gapped.isLeft shouldBe true
  }
}
