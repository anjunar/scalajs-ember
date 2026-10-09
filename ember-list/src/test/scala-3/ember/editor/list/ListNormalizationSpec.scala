package ember.editor.list

import ember.editor.core.*
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The rules that keep a list legal no matter who edited it (§8.2, P13 acceptance).
  *
  * The editing suite drives the commands; this one drives the *document*. A foreign module, a paste
  * or a later feature can move nodes anywhere, and the invariants have to hold anyway -- which is
  * why they are transforms and not care taken inside each command (§3.2).
  */
final class ListNormalizationSpec extends AnyFlatSpec with Matchers {

  private def listOf(texts: String*): ListFixture =
    val f = new ListFixture(texts*)
    texts.foreach { text =>
      f.caretIn(text)
      f.bullets(): Unit
    }
    f

  // ---------------------------------------------------------------------------------------
  // No bare block in a list
  // ---------------------------------------------------------------------------------------

  "A block moved straight into a list" should "get an item around it" in {
    // P13, Abnahme: "Kein nackter Paragraph direkt in ListNode." Repairing rather than
    // rejecting keeps the content -- rejecting would fail the whole transaction, dropping would
    // lose text.
    val f = listOf("Eins")
    f.edit(
      _.insert(
        NodeId("root"),
        1,
        ParagraphNode(NodeId("frei"), Vector(NodeId("ft"))),
        Vector(TextNode(NodeId("ft"), "Frei"))
      ): Unit
    )
    val list = f.firstList.getOrElse(fail("keine Liste"))

    f.edit(_.move(NodeId("frei"), list.id, 1): Unit)

    f.outline shouldBe Vector("ul", "  li", "    \"Eins\"", "  li", "    \"Frei\"")
  }

  it should "repair several of them" in {
    val f    = listOf("Eins")
    val list = f.firstList.getOrElse(fail("keine Liste"))

    Vector("a", "b", "c").zipWithIndex.foreach { (name, index) =>
      f.edit(
        _.insert(
          NodeId("root"),
          1,
          ParagraphNode(NodeId(name), Vector(NodeId(s"${name}t"))),
          Vector(TextNode(NodeId(s"${name}t"), name.toUpperCase))
        ): Unit
      )
      f.edit(_.move(NodeId(name), list.id, index + 1): Unit)
    }

    f.document.childrenOf(list.id).flatMap(f.document.node).foreach(_ shouldBe a[ListItemNode])
    f.outline.count(_.endsWith("li")) shouldBe 4
  }

  // ---------------------------------------------------------------------------------------
  // Empty items and empty lists
  // ---------------------------------------------------------------------------------------

  "An empty item" should "get a paragraph to hold a caret" in {
    // Same reason as `BlockNeedsText` one level up: a caret needs a text position, and a child
    // position in an empty item is not one.
    val f    = listOf("Eins")
    val list = f.firstList.getOrElse(fail("keine Liste"))

    f.edit(_.insert(list.id, 1, ListItemNode.empty(NodeId("leer"))): Unit)

    f.document.childrenOf(NodeId("leer")) should have length 1
    f.node("leer")
      .collect { case item: ListItemNode => item.children.head }
      .flatMap(f.document.node) shouldBe a[Some[?]]
  }

  "An empty list" should "disappear" in {
    // What is left after the last item is outdented. Keeping it would leave an invisible
    // `<ul></ul>` and a bullet in every renderer that draws one.
    val f    = listOf("Eins")
    val list = f.firstList.getOrElse(fail("keine Liste"))

    f.caretIn("Eins")
    f.outdent(): Unit

    f.document.node(list.id) shouldBe None
    f.outline shouldBe Vector("\"Eins\"")
  }

  // ---------------------------------------------------------------------------------------
  // Joining
  // ---------------------------------------------------------------------------------------

  "Two adjacent lists of the same kind" should "become one" in {
    val f = listOf("Eins", "Zwei")

    f.outline shouldBe Vector("ul", "  li", "    \"Eins\"", "  li", "    \"Zwei\"")
  }

  it should "keep the left list's fields" in {
    // The left one was there first; its `start` and `tight` win.
    val f = new ListFixture("Eins", "Zwei")
    f.caretIn("Eins")
    f.numbers(): Unit
    val first = f.firstList.getOrElse(fail("keine Liste"))
    f.edit(_.replace(first.id, first.copy(start = 5, tight = false)): Unit)

    f.caretIn("Zwei")
    f.numbers(): Unit

    f.firstList.map(list => (list.start, list.tight)) shouldBe Some((5, false))
    f.outline should have length 5
  }

  it should "leave different kinds alone" in {
    val f = new ListFixture("Eins", "Zwei")
    f.caretIn("Eins")
    f.bullets(): Unit
    f.caretIn("Zwei")
    f.numbers(): Unit

    f.document.inDocumentOrder.collect { case list: ListNode => list.kind }.toVector shouldBe
      Vector(ListKind.Unordered, ListKind.Ordered)
  }

  it should "not reach across something in between" in {
    val f = new ListFixture("Eins", "Dazwischen", "Zwei")
    f.caretIn("Eins")
    f.bullets(): Unit
    f.caretIn("Zwei")
    f.bullets(): Unit

    f.outline shouldBe Vector(
      "ul",
      "  li",
      "    \"Eins\"",
      "\"Dazwischen\"",
      "ul",
      "  li",
      "    \"Zwei\""
    )
  }

  // ---------------------------------------------------------------------------------------
  // Termination
  // ---------------------------------------------------------------------------------------

  "Normalisation" should "terminate" in {
    // P13, Abnahme. The risk is real: a rule that wraps loose children and one that fills empty
    // items can feed each other until §10's budget aborts the transaction after 32 rounds.
    // Every rule either removes a node or reduces the number of misplaced ones.
    val f = listOf("Eins", "Zwei", "Drei")

    f.caretIn("Zwei"); f.indent(): Unit
    f.caretIn("Drei"); f.indent(): Unit
    f.caretIn("Zwei"); f.outdent(): Unit
    f.caretIn("Drei"); f.outdent(): Unit
    f.caretIn("Eins"); f.outdent(): Unit

    // Nur "Eins" hat die Liste verlassen; die beiden anderen stehen weiter darin, und die
    // Reihenfolge ist die des Textes geblieben. Genau das ist der Punkt: keine der fuenf
    // Umbauten hat das Arbeitsbudget aus §10 erschoepft oder die Lesereihenfolge verdreht.
    f.outline shouldBe Vector(
      "\"Eins\"",
      "ul",
      "  li",
      "    \"Zwei\"",
      "  li",
      "    \"Drei\""
    )
  }

  it should "be a no-op on an already normal document" in {
    val f = listOf("Eins", "Zwei")

    val outcome = f.session.update(
      _.select(RangeSelection.caret(Point.textBefore(NodeId("t0"), 1)))
    )

    outcome.map(_.documentChanged) shouldBe Right(false)
  }

  it should "leave a valid document after every repair" in {
    val f    = listOf("Eins")
    val list = f.firstList.getOrElse(fail("keine Liste"))

    f.edit(
      _.insert(
        NodeId("root"),
        1,
        ParagraphNode(NodeId("frei"), Vector(NodeId("ft"))),
        Vector(TextNode(NodeId("ft"), "Frei"))
      ): Unit
    )
    f.edit(_.move(NodeId("frei"), list.id, 0): Unit)

    Document.build(
      f.document.schema,
      f.document.rootId,
      f.document.ids.flatMap(f.document.node).toVector
    ) should matchPattern { case Right(_) => }
  }

  // ---------------------------------------------------------------------------------------
  // The start number
  // ---------------------------------------------------------------------------------------

  "A start below one" should "be rejected" in {
    val f    = listOf("Eins")
    val list = f.firstList.getOrElse(fail("keine Liste"))

    val outcome = f.session.update(_.replace(list.id, list.copy(start = 0)): Unit)

    outcome should matchPattern { case Left(_) => }
  }

  "Splitting a numbered list" should "keep the second half counting" in {
    // §18.2: "Startnummer ... erhalten." Taking the second item out of `1. 2. 3.` leaves `1.`
    // and `3.`, not `1.` and `1.`.
    val f = new ListFixture("Eins", "Zwei", "Drei")
    Vector("Eins", "Zwei", "Drei").foreach { text =>
      f.caretIn(text)
      f.numbers(): Unit
    }

    f.caretIn("Zwei")
    f.outdent(): Unit

    f.outline shouldBe Vector(
      "ol",
      "  li",
      "    \"Eins\"",
      "\"Zwei\"",
      "ol(3)",
      "  li",
      "    \"Drei\""
    )
  }
}
