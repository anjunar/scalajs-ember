package ember.editor.core

import ember.editor.foreign.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Die primitiven Operationen und ihre Vorbedingungen (P03, Architektur §§8, 10). */
final class OperationSpec extends AnyFlatSpec with Matchers {

  private val schema = Schema.unsafe(RootNode, TextNode, CaptionNode, StickerNode)

  private def id(value: String): NodeId = NodeId(value)

  private val root = id("root")

  /** ```
    * root
    *  +- c1  (Caption, language = "de")
    *  |   +- t1 "Hallo"
    *  |   +- t2 "Welt"
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

  private def applied(document: Document, operation: Operation): OperationResult =
    document.applyOperation(operation) match
      case Right(result) => result
      case Left(error)   => fail(s"Operation abgewiesen: ${error.render}")

  private def rejected(document: Document, operation: Operation): OperationError =
    document.applyOperation(operation) match
      case Left(error) => error
      case Right(_)    => fail("Ungueltige Operation wurde angenommen")

  /** Unabhaengige Vollvalidierung: baut das Ergebnis von Grund auf neu (§8.2). */
  private def revalidate(document: Document): Document =
    Document.build(document.schema, document.rootId, document.nodes.toVector) match
      case Right(rebuilt)   => rebuilt
      case Left(violations) => fail(violations.map(_.render).mkString("ungueltig:\n", "\n", ""))

  // ---------------------------------------------------------------------------------------
  // Insert
  // ---------------------------------------------------------------------------------------

  "Insert" should "place a leaf at the requested position" in {
    val result = applied(base, Operation.insertLeaf(root, 1, TextNode(id("new"), "Mitte")))

    result.document.childrenOf(root) shouldBe Vector(id("c1"), id("new"), id("t3"))
    result.document.parentOf(id("new")) shouldBe Some(root)
    result.changes.created shouldBe Set(id("new"))
    result.changes.childListChanged shouldBe Set(root)
    revalidate(result.document).size shouldBe base.size + 1
  }

  it should "insert a whole subtree in one step" in {
    // Ein Knoten mit Kindreferenzen auf noch nicht eingefuegte Knoten waere ein ungueltiger
    // Zwischenstand. §10 verlangt, dass keiner sichtbar wird -- deshalb der ganze Teilbaum.
    val caption = CaptionNode.of(id("c2"), Vector(id("t4")))
    val result  =
      applied(base, Operation.Insert(root, 2, caption, Vector(TextNode(id("t4"), "Neu"))))

    result.document.childrenOf(id("c2")) shouldBe Vector(id("t4"))
    result.document.parentOf(id("t4")) shouldBe Some(id("c2"))
    result.changes.created shouldBe Set(id("c2"), id("t4"))
    revalidate(result.document)
  }

  it should "preserve every field of a foreign node" in {
    val caption = CaptionNode(id("c2"), Vector.empty, language = "fr", visible = false)
    val result  = applied(base, Operation.insertLeaf(root, 0, caption))

    result.document.node(id("c2")) shouldBe Some(caption)
  }

  it should "record the ancestors it touched without claiming they changed" in {
    val result = applied(base, Operation.insertLeaf(id("c1"), 0, TextNode(id("t0"), "x")))

    result.changes.childListChanged shouldBe Set(id("c1"))
    result.changes.touchedAncestors shouldBe Set(root)
    result.changes.changedNodes should not contain root
  }

  it should "reject an unknown or unsuitable parent" in {
    rejected(base, Operation.insertLeaf(id("ghost"), 0, TextNode(id("x"), "x"))) shouldBe
      OperationError.UnknownNode(id("ghost"))
    rejected(base, Operation.insertLeaf(id("t1"), 0, TextNode(id("x"), "x"))) shouldBe
      OperationError.NotAContainer(id("t1"))
  }

  it should "reject a position outside the child list" in {
    rejected(base, Operation.insertLeaf(root, 3, TextNode(id("x"), "x"))) shouldBe
      OperationError.IndexOutOfRange(root, 3, 2)
    rejected(base, Operation.insertLeaf(root, -1, TextNode(id("x"), "x"))) shouldBe
      OperationError.IndexOutOfRange(root, -1, 2)
  }

  it should "reject an id the document already uses" in {
    rejected(base, Operation.insertLeaf(root, 0, TextNode(id("t1"), "x"))) shouldBe
      OperationError.IdAlreadyInUse(id("t1"))
  }

  it should "reject a subtree with a dangling child reference" in {
    val caption = CaptionNode.of(id("c2"), Vector(id("missing")))

    rejected(base, Operation.Insert(root, 0, caption, Vector.empty)) shouldBe a[
      OperationError.SubtreeNotSelfContained
    ]
  }

  it should "reject a descendant that hangs off nothing" in {
    val caption = CaptionNode.of(id("c2"), Vector.empty)

    rejected(
      base,
      Operation.Insert(root, 0, caption, Vector(TextNode(id("loose"), "x")))
    ) shouldBe a[OperationError.SubtreeNotSelfContained]
  }

  // ---------------------------------------------------------------------------------------
  // Remove
  // ---------------------------------------------------------------------------------------

  "Remove" should "take the whole subtree with it" in {
    val result = applied(base, Operation.Remove(id("c1")))

    result.changes.removed shouldBe Set(id("c1"), id("t1"), id("t2"))
    result.document.childrenOf(root) shouldBe Vector(id("t3"))
    result.document.contains(id("t1")) shouldBe false
    revalidate(result.document).size shouldBe 2
  }

  it should "leave the previous snapshot untouched" in {
    // §8.2: Entfernte Teilbaeume leben in noch referenzierten Snapshots weiter.
    val result = applied(base, Operation.Remove(id("c1")))

    base.contains(id("t1")) shouldBe true
    base.childrenOf(root) shouldBe Vector(id("c1"), id("t3"))
    result.document.contains(id("t1")) shouldBe false
  }

  it should "refuse to remove the root" in {
    rejected(base, Operation.Remove(root)) shouldBe OperationError.RootIsImmovable(root)
  }

  // ---------------------------------------------------------------------------------------
  // Move -- der Fall mit dem Off-by-one (P03, Risiken)
  // ---------------------------------------------------------------------------------------

  "Move" should "reorder within the same parent, counting the target after removal" in {
    // t1 steht auf 0, t2 auf 1. Ziel 1 heisst: nach der Herausnahme von t1 an Position 1 --
    // also hinter t2. Wer den Index in der urspruenglichen Liste zaehlt, landet daneben.
    val result = applied(base, Operation.Move(id("t1"), id("c1"), 1))

    result.document.childrenOf(id("c1")) shouldBe Vector(id("t2"), id("t1"))
    result.changes.moved shouldBe Set(id("t1"))
    result.changes.childListChanged shouldBe Set(id("c1"))
    revalidate(result.document)
  }

  it should "reject a target index beyond the shortened list" in {
    // c1 hat zwei Kinder; nach Herausnahme bleibt eines, gueltige Ziele sind also 0 und 1.
    rejected(base, Operation.Move(id("t1"), id("c1"), 2)) shouldBe
      OperationError.IndexOutOfRange(id("c1"), 2, 1)
  }

  it should "move across parents and keep node identity" in {
    val before = base.node(id("t1"))
    val result = applied(base, Operation.Move(id("t1"), root, 0))

    result.document.childrenOf(root) shouldBe Vector(id("t1"), id("c1"), id("t3"))
    result.document.childrenOf(id("c1")) shouldBe Vector(id("t2"))
    result.document.parentOf(id("t1")) shouldBe Some(root)
    result.document.node(id("t1")) shouldBe before
    result.changes.childListChanged shouldBe Set(id("c1"), root)
    revalidate(result.document)
  }

  it should "carry the whole subtree along" in {
    val result = applied(base, Operation.Move(id("c1"), root, 1))

    result.document.childrenOf(id("c1")) shouldBe Vector(id("t1"), id("t2"))
    result.document.parentOf(id("t1")) shouldBe Some(id("c1"))
    revalidate(result.document)
  }

  it should "refuse to move a node into its own subtree" in {
    // Braucht einen verschachtelten Container als Ziel: ein Textknoten scheiterte schon an
    // NotAContainer, und der Zyklus waere gar nicht erst geprueft worden.
    val nested = Document.unsafe(
      schema,
      root,
      Vector(
        RootNode(root, Vector(id("outer"))),
        CaptionNode.of(id("outer"), Vector(id("inner"))),
        CaptionNode.of(id("inner"), Vector.empty)
      )
    )

    rejected(nested, Operation.Move(id("outer"), id("inner"), 0)) shouldBe
      OperationError.MoveIntoOwnSubtree(id("outer"), id("inner"))
  }

  it should "refuse to move a node into itself" in {
    val nested = Document.unsafe(
      schema,
      root,
      Vector(RootNode(root, Vector(id("c9"))), CaptionNode.of(id("c9"), Vector.empty))
    )

    rejected(nested, Operation.Move(id("c9"), id("c9"), 0)) shouldBe
      OperationError.MoveIntoOwnSubtree(id("c9"), id("c9"))
  }

  it should "refuse to move the root" in {
    rejected(base, Operation.Move(root, id("c1"), 0)) shouldBe OperationError.RootIsImmovable(root)
  }

  // ---------------------------------------------------------------------------------------
  // Replace
  // ---------------------------------------------------------------------------------------

  "Replace" should "swap the payload and keep identity and children" in {
    val recoloured =
      CaptionNode(id("c1"), Vector(id("t1"), id("t2")), language = "fr", visible = false)
    val result = applied(base, Operation.Replace(id("c1"), recoloured))

    result.document.node(id("c1")) shouldBe Some(recoloured)
    result.document.childrenOf(id("c1")) shouldBe Vector(id("t1"), id("t2"))
    result.changes.updated shouldBe Set(id("c1"))
    revalidate(result.document)
  }

  it should "reject a replacement with a different id" in {
    rejected(base, Operation.Replace(id("t1"), TextNode(id("other"), "x"))) shouldBe
      OperationError.ReplacementIdMismatch(id("t1"), id("other"))
  }

  it should "reject a replacement that rewires children" in {
    // Struktur aendert man mit Insert, Remove oder Move. Ginge es hier mit, wuerden die
    // bisherigen Kinder zu Waisen.
    rejected(
      base,
      Operation.Replace(id("c1"), CaptionNode.of(id("c1"), Vector(id("t1"))))
    ) shouldBe OperationError.ReplacementChangesChildren(id("c1"))
  }

  // ---------------------------------------------------------------------------------------
  // SpliceText
  // ---------------------------------------------------------------------------------------

  "SpliceText" should "insert, delete and replace in place" in {
    applied(base, Operation.SpliceText(id("t1"), 5, 0, "!")).document
      .node(id("t1")) shouldBe Some(TextNode(id("t1"), "Hallo!"))

    applied(base, Operation.SpliceText(id("t1"), 0, 2, "")).document
      .node(id("t1")) shouldBe Some(TextNode(id("t1"), "llo"))

    applied(base, Operation.SpliceText(id("t1"), 0, 5, "Servus")).document
      .node(id("t1")) shouldBe Some(TextNode(id("t1"), "Servus"))
  }

  it should "report the splice in the change set instead of a full update" in {
    // Die Projektion soll `CharacterData.replaceData` aufrufen koennen, nicht den Knoten
    // neu schreiben. Deshalb steht die Aenderung in textSplices und nicht in updated.
    val result = applied(base, Operation.SpliceText(id("t1"), 5, 0, "!"))

    result.changes.textSplices shouldBe Map(id("t1") -> Vector(TextSplice(5, 0, "!")))
    result.changes.updated shouldBe empty
  }

  it should "treat a splice with an unchanged result as a no-op" in {
    // Gleicher Vertrag wie `TextNode.spliceText` in ui-core: identischer Text, kein
    // Schreibzugriff. Eine Ebene hoeher heisst das: kein Commit, keine History-Stufe (§10).
    val result = applied(base, Operation.SpliceText(id("t1"), 2, 1, "l"))

    result.changes.isEmpty shouldBe true
    result.document should be theSameInstanceAs base
  }

  it should "reject offsets outside the text" in {
    rejected(base, Operation.SpliceText(id("t1"), 0, 99, "")) shouldBe
      OperationError.TextOffsetOutOfRange(id("t1"), 99, 5)
  }

  it should "reject a cut through a surrogate pair" in {
    // Ein Schnitt hier erzeugte zwei Strings mit je einem halben Codepoint. Das ist eine
    // UTF-16-Gueltigkeitsfrage, keine Unicode-Segmentierung -- Graphemcluster prueft erst
    // der TextBoundaryService.
    val withEmoji = applied(base, Operation.SpliceText(id("t1"), 0, 5, "😀")).document

    rejected(withEmoji, Operation.SpliceText(id("t1"), 1, 0, "x")) shouldBe
      OperationError.SplitsSurrogatePair(id("t1"), 1)
  }

  it should "reject inserted text with an unpaired surrogate" in {
    rejected(base, Operation.SpliceText(id("t1"), 0, 0, "\uD83D")) shouldBe
      OperationError.MalformedText(id("t1"))
  }

  it should "reject a non-text target" in {
    rejected(base, Operation.SpliceText(id("c1"), 0, 0, "x")) shouldBe
      OperationError.NotATextNode(id("c1"))
  }

  // ---------------------------------------------------------------------------------------
  // SplitText und MergeText
  // ---------------------------------------------------------------------------------------

  "SplitText" should "keep the left id and give the right side a new one" in {
    // §8.3: Split behaelt die linke Text-ID und erzeugt rechts eine neue.
    val result = applied(base, Operation.SplitText(id("t1"), 2, id("t1b")))

    result.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "Ha"))
    result.document.node(id("t1b")) shouldBe Some(TextNode(id("t1b"), "llo"))
    result.document.childrenOf(id("c1")) shouldBe Vector(id("t1"), id("t1b"), id("t2"))
    revalidate(result.document)
  }

  it should "copy the marks onto the right side" in {
    val marked = applied(
      base,
      Operation.Replace(id("t1"), TextNode(id("t1"), "Hallo", MarkSet.of(NamedMark("strong"))))
    ).document

    val result = applied(marked, Operation.SplitText(id("t1"), 2, id("t1b")))

    result.document.node(id("t1b")).collect { case text: TextNode => text.marks } shouldBe
      Some(MarkSet.of(NamedMark("strong")))
  }

  it should "reject an id that is already in use" in {
    rejected(base, Operation.SplitText(id("t1"), 2, id("t2"))) shouldBe
      OperationError.IdAlreadyInUse(id("t2"))
  }

  "MergeText" should "append the right text to the left node" in {
    val result = applied(base, Operation.MergeText(id("t1"), id("t2")))

    result.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "HalloWelt"))
    result.document.contains(id("t2")) shouldBe false
    result.document.childrenOf(id("c1")) shouldBe Vector(id("t1"))
    result.changes.textSplices shouldBe Map(id("t1") -> Vector(TextSplice(5, 0, "Welt")))
    revalidate(result.document)
  }

  it should "reject nodes that are not adjacent siblings" in {
    rejected(base, Operation.MergeText(id("t1"), id("t3"))) shouldBe
      OperationError.NotAdjacentSiblings(id("t1"), id("t3"))
  }

  it should "reject a merge that would silently drop formatting" in {
    val marked = applied(
      base,
      Operation.Replace(id("t2"), TextNode(id("t2"), "Welt", MarkSet.of(NamedMark("strong"))))
    ).document

    rejected(marked, Operation.MergeText(id("t1"), id("t2"))) shouldBe
      OperationError.MarksDiffer(id("t1"), id("t2"))
  }

  it should "round-trip with SplitText" in {
    val split  = applied(base, Operation.SplitText(id("t1"), 2, id("t1b"))).document
    val merged = applied(split, Operation.MergeText(id("t1"), id("t1b"))).document

    merged.node(id("t1")) shouldBe base.node(id("t1"))
    merged.childrenOf(id("c1")) shouldBe base.childrenOf(id("c1"))
  }

  // ---------------------------------------------------------------------------------------
  // Atomaritaet
  // ---------------------------------------------------------------------------------------

  "A rejected operation" should "leave the document completely untouched" in {
    base.applyOperation(Operation.Remove(id("ghost"))).isLeft shouldBe true

    base.size shouldBe 5
    base.childrenOf(root) shouldBe Vector(id("c1"), id("t3"))
  }

  "applyAll" should "compose a sequence into one result" in {
    val result = base.applyAll(
      Seq(
        Operation.SpliceText(id("t1"), 5, 0, "!"),
        Operation.insertLeaf(root, 2, TextNode(id("t4"), "Nach")),
        Operation.Move(id("t3"), id("c1"), 0)
      )
    ) match
      case Right(result) => result
      case Left(error)   => fail(error.render)

    result.document.node(id("t1")) shouldBe Some(TextNode(id("t1"), "Hallo!"))
    result.document.childrenOf(id("c1")) shouldBe Vector(id("t3"), id("t1"), id("t2"))
    result.document.childrenOf(root) shouldBe Vector(id("c1"), id("t4"))
    result.changes.created shouldBe Set(id("t4"))
    result.changes.moved shouldBe Set(id("t3"))
    revalidate(result.document)
  }

  it should "stop at the first failure without applying anything" in {
    // Es gibt nichts zurueckzurollen: jeder Zwischenstand ist ein eigener unveraenderlicher
    // Wert, und das Ausgangsdokument wurde nie angefasst.
    val outcome = base.applyAll(
      Seq(
        Operation.SpliceText(id("t1"), 5, 0, "!"),
        Operation.Remove(root),
        Operation.SpliceText(id("t2"), 0, 0, "nie")
      )
    )

    outcome shouldBe Left(OperationError.RootIsImmovable(root))
    base.node(id("t1")) shouldBe Some(TextNode(id("t1"), "Hallo"))
  }

  it should "produce an empty result for an empty sequence" in {
    val result = base.applyAll(Seq.empty).getOrElse(fail("leere Folge abgewiesen"))

    result.changes.isEmpty shouldBe true
    result.document should be theSameInstanceAs base
  }
}
