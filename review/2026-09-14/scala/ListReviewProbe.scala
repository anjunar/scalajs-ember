package ember.editor.list

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class ListReviewProbe extends AnyFlatSpec with Matchers {
  "Deleting a range through three items" should "remove the selected middle item" in {
    val f = new ListFixture("ABC", "DEF", "GHI")
    Vector("ABC", "DEF", "GHI").foreach { text =>
      f.caretIn(text)
      f.bullets(): Unit
    }
    f.edit(_.select(RangeSelection(Point.textBefore(NodeId("t0"), 1), Point.textBefore(NodeId("t2"), 2))): Unit)
    f.backspace() shouldBe true
    f.textOf(f.root) shouldBe "AI"
    f.document.inDocumentOrder.collect { case item: ListItemNode => item }.toVector should have size 1
  }
}
