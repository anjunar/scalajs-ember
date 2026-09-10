package ember.editor.richtext

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Headings, quotes and breaks (§8.2). */
final class RichTextStructureSpec extends AnyFlatSpec with Matchers {

  private def hallo = new RichTextFixture("Hallo Welt!")

  private def typeOf(f: RichTextFixture, id: String): String =
    f.document
      .node(NodeId(id))
      .flatMap(f.document.schema.descriptorFor)
      .map(_.typeId.value)
      .getOrElse("?")

  // ---------------------------------------------------------------------------------------
  // Headings
  // ---------------------------------------------------------------------------------------

  "SetHeading" should "turn the block at the caret into a heading" in {
    val f = hallo
    f.caretAt("t0", 3)

    f.dispatch(RichText.SetHeading, Some(HeadingLevel.H2)) shouldBe true

    f.node("p0") shouldBe Some(HeadingNode(NodeId("p0"), Vector(NodeId("t0")), HeadingLevel.H2))
  }

  it should "keep the block id and its children" in {
    // `Operation.Replace` keeps identity and children, so every point inside the block survives.
    val f = hallo
    f.caretAt("t0", 3)

    f.dispatch(RichText.SetHeading, Some(HeadingLevel.H1)): Unit

    f.document.childrenOf(NodeId("p0")) shouldBe Vector(NodeId("t0"))
    f.caret shouldBe Some(("t0", 3))
  }

  it should "change the level of an existing heading" in {
    val f = hallo
    f.caretAt("t0", 0)
    f.dispatch(RichText.SetHeading, Some(HeadingLevel.H1)): Unit

    f.dispatch(RichText.SetHeading, Some(HeadingLevel.H3)): Unit

    f.node("p0").collect { case heading: HeadingNode => heading.level } shouldBe
      Some(HeadingLevel.H3)
  }

  it should "turn it back into a paragraph with None" in {
    val f = hallo
    f.caretAt("t0", 0)
    f.dispatch(RichText.SetHeading, Some(HeadingLevel.H1)): Unit

    f.dispatch(RichText.SetHeading, None): Unit

    typeOf(f, "p0") shouldBe "ember.rich-text.paragraph/1"
  }

  it should "do nothing when the level is already right" in {
    val f = hallo
    f.caretAt("t0", 0)
    f.dispatch(RichText.SetHeading, Some(HeadingLevel.H2)): Unit
    val before = f.session.state.documentRevision

    f.dispatch(RichText.SetHeading, Some(HeadingLevel.H2)): Unit

    f.session.state.documentRevision shouldBe before
  }

  "A heading" should "normalise its runs like a paragraph" in {
    // The merge rule hangs on the run, not on the block -- so it works in every container
    // without knowing which ones exist.
    val f = hallo
    f.caretAt("t0", 0)
    f.dispatch(RichText.SetHeading, Some(HeadingLevel.H2)): Unit

    f.edit(_.insert(NodeId("p0"), 1, TextNode(NodeId("zweiter"), " Mehr")): Unit)

    f.shape() shouldBe Vector(("Hallo Welt! Mehr", Vector.empty))
  }

  "HeadingLevel" should "have exactly six levels" in {
    HeadingLevel.values.map(_.level).toVector shouldBe Vector(1, 2, 3, 4, 5, 6)
    HeadingLevel.fromInt(0) shouldBe None
    HeadingLevel.fromInt(7) shouldBe None
    HeadingLevel.fromInt(3) shouldBe Some(HeadingLevel.H3)
  }

  // ---------------------------------------------------------------------------------------
  // Quotes
  // ---------------------------------------------------------------------------------------

  "Quote" should "wrap the block, not change it" in {
    // §8.2: a quote holds blocks. Quoting is putting a paragraph into a container, not setting
    // a property on it.
    val f = hallo
    f.caretAt("t0", 0)

    f.dispatch(RichText.Quote) shouldBe true

    val quoteId = f.document.parentOf(NodeId("p0")).getOrElse(fail("kein Elternknoten"))
    typeOf(f, quoteId.value) shouldBe "ember.rich-text.quote/1"
    typeOf(f, "p0") shouldBe "ember.rich-text.paragraph/1"
  }

  it should "sit where the block sat" in {
    val f = new RichTextFixture("Erster", "Zweiter")
    f.caretAt("t1", 0)

    f.dispatch(RichText.Quote): Unit

    val children = f.document.childrenOf(f.root)
    children should have length 2
    children.head shouldBe NodeId("p0")
    f.document.childrenOf(children.last) shouldBe Vector(NodeId("p1"))
  }

  it should "nest when applied twice" in {
    val f = hallo
    f.caretAt("t0", 0)

    f.dispatch(RichText.Quote): Unit
    f.dispatch(RichText.Quote): Unit

    val inner = f.document.parentOf(NodeId("p0")).getOrElse(fail("kein Elternknoten"))
    val outer = f.document.parentOf(inner).getOrElse(fail("kein Grosselternknoten"))
    typeOf(f, inner.value) shouldBe "ember.rich-text.quote/1"
    typeOf(f, outer.value) shouldBe "ember.rich-text.quote/1"
  }

  "Unquote" should "take the block back out" in {
    val f = hallo
    f.caretAt("t0", 0)
    f.dispatch(RichText.Quote): Unit

    f.dispatch(RichText.Unquote) shouldBe true

    f.document.parentOf(NodeId("p0")) shouldBe Some(f.root)
  }

  it should "remove the quote it emptied" in {
    val f = hallo
    f.caretAt("t0", 0)
    f.dispatch(RichText.Quote): Unit
    val quoteId = f.document.parentOf(NodeId("p0")).getOrElse(fail("kein Elternknoten"))

    f.dispatch(RichText.Unquote): Unit

    f.document.node(quoteId) shouldBe None
  }

  it should "keep a quote that still has children" in {
    val f = new RichTextFixture("Erster", "Zweiter")
    f.caretAt("t0", 0)
    f.dispatch(RichText.Quote): Unit
    val quoteId = f.document.parentOf(NodeId("p0")).getOrElse(fail("kein Elternknoten"))
    f.edit(_.move(NodeId("p1"), quoteId, 1): Unit)

    f.caretAt("t0", 0)
    f.dispatch(RichText.Unquote): Unit

    f.document.childrenOf(quoteId) shouldBe Vector(NodeId("p1"))
  }

  it should "do nothing outside a quote" in {
    val f = hallo
    f.caretAt("t0", 0)
    val before = f.session.state.documentRevision

    f.dispatch(RichText.Unquote): Unit

    f.session.state.documentRevision shouldBe before
  }

  // ---------------------------------------------------------------------------------------
  // Breaks
  // ---------------------------------------------------------------------------------------

  "A hard break" should "split the run and sit between the halves" in {
    val f = hallo
    f.caretAt("t0", 5)

    f.dispatch(RichText.InsertBreak, BreakKind.Hard) shouldBe true

    val children = f.document.childrenOf(NodeId("p0"))
    children should have length 3
    f.document.node(children(1)) shouldBe Some(BreakNode(children(1), BreakKind.Hard))
    f.runs().map(_.text) shouldBe Vector("Hallo", " Welt!")
  }

  it should "leave the caret after it" in {
    val f = hallo
    f.caretAt("t0", 5)

    f.dispatch(RichText.InsertBreak, BreakKind.Hard): Unit

    f.caret.map(_._2) shouldBe Some(0)
    f.caret.map(_._1) should not be Some("t0")
  }

  "A soft break" should "stay distinguishable from a hard one" in {
    // §8.2: "SoftBreak und HardBreak bleiben unterscheidbar, damit Markdown und semantisches
    // HTML ihre Bedeutung erhalten."
    val f = hallo
    f.caretAt("t0", 5)

    f.dispatch(RichText.InsertBreak, BreakKind.Soft): Unit

    val kinds = f.document
      .childrenOf(NodeId("p0"))
      .flatMap(id => f.document.node(id).collect { case value: BreakNode => value.kind })
    kinds shouldBe Vector(BreakKind.Soft)
  }

  "A break at the end of a run" should "get a run to put the caret in" in {
    // A caret has to stand somewhere, and a child position after an atom is not a text position.
    val f = hallo
    f.caretAt("t0", 11)

    f.dispatch(RichText.InsertBreak, BreakKind.Hard): Unit

    f.runs().map(_.text) shouldBe Vector("Hallo Welt!", "")
    f.caret.map(_._2) shouldBe Some(0)
  }

  "A thematic break" should "become a sibling of the block" in {
    // Block level, unlike a line break -- it stands between blocks, not inside one.
    val f = hallo
    f.caretAt("t0", 3)

    f.dispatch(RichText.InsertThematicBreak) shouldBe true

    val children = f.document.childrenOf(f.root)
    children should have length 2
    f.document.node(children.last) shouldBe Some(ThematicBreakNode(children.last))
  }

  it should "leave the caret where it was" in {
    val f = hallo
    f.caretAt("t0", 3)

    f.dispatch(RichText.InsertThematicBreak): Unit

    f.caret shouldBe Some(("t0", 3))
  }

  // ---------------------------------------------------------------------------------------
  // The profile still holds together
  // ---------------------------------------------------------------------------------------

  "The document" should "stay valid after every structural command" in {
    // The session validates on commit (§10); this asserts the commands never even try to
    // produce something invalid, across the whole set.
    val f = hallo

    f.caretAt("t0", 3)
    f.dispatch(RichText.SetHeading, Some(HeadingLevel.H2)): Unit
    f.dispatch(RichText.Quote): Unit
    f.dispatch(RichText.InsertBreak, BreakKind.Hard): Unit
    f.dispatch(RichText.InsertThematicBreak): Unit
    f.dispatch(RichText.Unquote): Unit

    Document.build(
      f.document.schema,
      f.document.rootId,
      f.document.ids.flatMap(f.document.node).toVector
    ) should matchPattern { case Right(_) => }
  }
}
