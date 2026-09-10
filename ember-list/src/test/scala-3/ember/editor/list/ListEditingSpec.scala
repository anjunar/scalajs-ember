package ember.editor.list

import ember.editor.core.*
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Wrapping, indenting and the keys that mean something else inside a list (§8.2, §12). */
final class ListEditingSpec extends AnyFlatSpec with Matchers {

  /** Three paragraphs, all made into one bulleted list. */
  private def threeItems: ListFixture =
    val f = new ListFixture("Eins", "Zwei", "Drei")
    Vector("Eins", "Zwei", "Drei").foreach { text =>
      f.caretIn(text)
      f.bullets(): Unit
    }
    f

  // ---------------------------------------------------------------------------------------
  // Wrapping
  // ---------------------------------------------------------------------------------------

  "ToggleList" should "wrap the block at the caret" in {
    val f = new ListFixture("Eins")
    f.caretIn("Eins")

    f.bullets() shouldBe true

    f.outline shouldBe Vector("ul", "  li", "    \"Eins\"")
  }

  it should "put the paragraph inside an item, never straight into the list" in {
    // P13, Abnahme: "Kein nackter Paragraph direkt in ListNode." §8.2 fixes the shape, and
    // `<ul><p>` is not expressible in HTML or Markdown at all.
    val f = new ListFixture("Eins")
    f.caretIn("Eins")
    f.bullets(): Unit

    val list = f.firstList.getOrElse(fail("keine Liste"))
    list.children.flatMap(f.document.node).foreach(_ shouldBe a[ListItemNode])
  }

  it should "keep the caret where it was" in {
    // Everything here is a move, and §11 says a move keeps the node -- so the caret keeps its
    // run and its offset.
    val f = new ListFixture("Eins")
    f.caretIn("Eins", 2)

    f.bullets(): Unit

    f.caret shouldBe Some(("t0", 2))
  }

  it should "unwrap when the kind is already right" in {
    val f = new ListFixture("Eins")
    f.caretIn("Eins")
    f.bullets(): Unit

    f.bullets(): Unit

    f.outline shouldBe Vector("\"Eins\"")
  }

  it should "change the kind rather than rebuild" in {
    // A numbered list that becomes bulleted is the same list; rebuilding would throw away the
    // items' identities for nothing.
    val f = new ListFixture("Eins")
    f.caretIn("Eins")
    f.bullets(): Unit
    val before = f.firstList.map(_.id)

    f.numbers(): Unit

    f.firstList.map(_.id) shouldBe before
    f.firstList.map(_.kind) shouldBe Some(ListKind.Ordered)
  }

  "Adjacent wraps" should "join into one list" in {
    // Without joining, three keystrokes leave three `<ul>` where the author sees one list.
    val f = threeItems

    f.outline shouldBe Vector(
      "ul",
      "  li", "    \"Eins\"",
      "  li", "    \"Zwei\"",
      "  li", "    \"Drei\""
    )
  }

  it should "not join lists of different kinds" in {
    val f = new ListFixture("Eins", "Zwei")
    f.caretIn("Eins")
    f.bullets(): Unit
    f.caretIn("Zwei")
    f.numbers(): Unit

    f.outline shouldBe Vector("ul", "  li", "    \"Eins\"", "ol", "  li", "    \"Zwei\"")
  }

  // ---------------------------------------------------------------------------------------
  // Indent and outdent
  // ---------------------------------------------------------------------------------------

  "Indent" should "move the item into the one above it" in {
    val f = threeItems
    f.caretIn("Zwei")

    f.indent() shouldBe true

    f.outline shouldBe Vector(
      "ul",
      "  li", "    \"Eins\"",
      "    ul", "      li", "        \"Zwei\"",
      "  li", "    \"Drei\""
    )
  }

  it should "refuse on the first item" in {
    // There is nothing to indent into. Inventing an empty parent item would produce a bullet
    // the author never typed.
    val f = threeItems
    f.caretIn("Eins")
    val before = f.outline

    f.indent(): Unit

    f.outline shouldBe before
  }

  it should "join a sublist that is already there" in {
    // Without the check, indenting two items in a row makes two nested lists of one item each.
    val f = threeItems
    f.caretIn("Zwei")
    f.indent(): Unit
    f.caretIn("Drei")
    f.indent(): Unit

    f.outline shouldBe Vector(
      "ul",
      "  li", "    \"Eins\"",
      "    ul",
      "      li", "        \"Zwei\"",
      "      li", "        \"Drei\""
    )
  }

  it should "keep the caret" in {
    val f = threeItems
    f.caretIn("Zwei", 3)

    f.indent(): Unit

    f.caret shouldBe Some(("t1", 3))
  }

  "Outdent" should "take a nested item back out" in {
    val f = threeItems
    f.caretIn("Zwei")
    f.indent(): Unit

    f.outdent() shouldBe true

    f.outline shouldBe Vector(
      "ul",
      "  li", "    \"Eins\"",
      "  li", "    \"Zwei\"",
      "  li", "    \"Drei\""
    )
  }

  it should "leave the list at the top level" in {
    val f = threeItems
    f.caretIn("Zwei")

    f.outdent(): Unit

    f.outline shouldBe Vector(
      "ul", "  li", "    \"Eins\"",
      "\"Zwei\"",
      "ul", "  li", "    \"Drei\""
    )
  }

  it should "do nothing outside a list" in {
    val f = new ListFixture("Eins")
    f.caretIn("Eins")
    val before = f.session.state.documentRevision

    f.outdent(): Unit

    f.session.state.documentRevision shouldBe before
  }

  "Indent and outdent" should "cancel out" in {
    val f = threeItems
    f.caretIn("Zwei")
    val before = f.outline

    f.indent(): Unit
    f.outdent(): Unit

    f.outline shouldBe before
  }

  // ---------------------------------------------------------------------------------------
  // Enter
  // ---------------------------------------------------------------------------------------

  "Enter in a full item" should "start a new one" in {
    val f = threeItems
    f.caretIn("Zwei", 4)

    f.enter() shouldBe true

    f.outline shouldBe Vector(
      "ul",
      "  li", "    \"Eins\"",
      "  li", "    \"Zwei\"",
      "  li", "    \"\"",
      "  li", "    \"Drei\""
    )
  }

  it should "carry the text after the caret along" in {
    val f = threeItems
    f.caretIn("Zwei", 1)

    f.enter(): Unit

    f.outline should contain inOrder ("    \"Z\"", "    \"wei\"")
  }

  it should "leave the caret in the new item" in {
    val f = threeItems
    f.caretIn("Zwei", 2)

    f.enter(): Unit
    f.typeText("X"): Unit

    f.outline should contain("    \"Xei\"")
  }

  "Enter in an empty item" should "leave the list" in {
    // The author is done with the list. Every editor since the eighties.
    val f = threeItems
    f.caretIn("Drei", 4)
    f.enter(): Unit

    f.enter(): Unit

    f.outline shouldBe Vector(
      "ul",
      "  li", "    \"Eins\"",
      "  li", "    \"Zwei\"",
      "  li", "    \"Drei\"",
      "\"\""
    )
  }

  it should "only outdent one level when nested" in {
    val f = threeItems
    f.caretIn("Zwei")
    f.indent(): Unit
    f.caretIn("Zwei", 4)
    f.enter(): Unit

    f.enter(): Unit

    f.outline shouldBe Vector(
      "ul",
      "  li", "    \"Eins\"",
      "    ul", "      li", "        \"Zwei\"",
      "  li", "    \"\"",
      "  li", "    \"Drei\""
    )
  }

  it should "pass through outside a list" in {
    // §12: the handler returns `Pass`, and the rich-text one behind it splits the paragraph.
    val f = new ListFixture("Eins")
    f.caretIn("Eins", 2)

    f.enter() shouldBe true

    f.outline shouldBe Vector("\"Ei\"", "\"ns\"")
  }

  // ---------------------------------------------------------------------------------------
  // Backspace
  // ---------------------------------------------------------------------------------------

  "Backspace at the start of an item" should "outdent instead of deleting" in {
    // There is no character to the left inside this item, and joining with the item above would
    // silently merge two bullets the author still wants apart.
    val f = threeItems
    f.caretIn("Zwei")
    f.indent(): Unit
    f.caretIn("Zwei", 0)

    f.backspace() shouldBe true

    f.outline shouldBe Vector(
      "ul",
      "  li", "    \"Eins\"",
      "  li", "    \"Zwei\"",
      "  li", "    \"Drei\""
    )
  }

  it should "leave the list from the top level" in {
    val f = threeItems
    f.caretIn("Zwei", 0)

    f.backspace(): Unit

    f.outline should contain("\"Zwei\"")
  }

  it should "delete normally anywhere else" in {
    val f = threeItems
    f.caretIn("Zwei", 2)

    f.backspace(): Unit

    f.outline should contain("    \"Zei\"")
  }

  it should "pass through outside a list" in {
    val f = new ListFixture("Eins")
    f.caretIn("Eins", 2)

    f.backspace(): Unit

    // Der Caret steht hinter "Ei"; Backspace nimmt das "i".
    f.outline shouldBe Vector("\"Ens\"")
  }

  // ---------------------------------------------------------------------------------------
  // Items with several blocks
  // ---------------------------------------------------------------------------------------

  "An item with two paragraphs" should "keep both when indented" in {
    // §8.2: "ein ListItem Blockinhalte" -- plural, and this is why.
    val f = threeItems
    val item = f.document
      .inDocumentOrder
      .collectFirst { case value: ListItemNode if f.textOf(value.id) == "Zwei" => value }
      .getOrElse(fail("kein Item"))
    // Ein Teilbaum wird als Ganzes eingefuegt (§10): ein Absatz mit Kindreferenz auf einen noch
    // nicht vorhandenen Lauf waere ein ungueltiger Zwischenstand.
    f.edit(
      _.insert(
        item.id,
        1,
        ParagraphNode(NodeId("extra"), Vector(NodeId("xt"))),
        Vector(TextNode(NodeId("xt"), "Mehr"))
      ): Unit
    )

    f.caretIn("Zwei")
    f.indent(): Unit

    f.outline should contain inOrder ("        \"Zwei\"", "        \"Mehr\"")
  }

  it should "take both out again" in {
    val f = threeItems
    val item = f.document
      .inDocumentOrder
      .collectFirst { case value: ListItemNode if f.textOf(value.id) == "Zwei" => value }
      .getOrElse(fail("kein Item"))
    // Ein Teilbaum wird als Ganzes eingefuegt (§10): ein Absatz mit Kindreferenz auf einen noch
    // nicht vorhandenen Lauf waere ein ungueltiger Zwischenstand.
    f.edit(
      _.insert(
        item.id,
        1,
        ParagraphNode(NodeId("extra"), Vector(NodeId("xt"))),
        Vector(TextNode(NodeId("xt"), "Mehr"))
      ): Unit
    )

    f.caretIn("Zwei")
    f.outdent(): Unit

    f.outline should contain inOrder ("\"Zwei\"", "\"Mehr\"")
  }

  // ---------------------------------------------------------------------------------------
  // The document stays valid
  // ---------------------------------------------------------------------------------------

  "Any sequence of list commands" should "leave a valid document" in {
    val f = threeItems

    f.caretIn("Zwei"); f.indent(): Unit
    f.caretIn("Drei"); f.indent(): Unit
    f.caretIn("Zwei"); f.outdent(): Unit
    f.caretIn("Drei", 4); f.enter(): Unit
    f.enter(): Unit
    f.caretIn("Eins"); f.numbers(): Unit

    Document.build(
      f.document.schema,
      f.document.rootId,
      f.document.ids.flatMap(f.document.node).toVector
    ) should matchPattern { case Right(_) => }
  }
}
