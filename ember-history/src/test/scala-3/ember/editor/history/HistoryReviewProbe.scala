package ember.editor.history

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class HistoryReviewProbe extends AnyFlatSpec with Matchers {
  "Undo inside a rejected transaction" should "leave the history stack unchanged" in {
    val f = new HistoryFixture()
    f.typeChar("X")
    val before = f.history.state
    val result = f.session.update { tx =>
      tx.dispatch(HistoryCommands.Undo): Unit
      tx.remove(NodeId("missing")): Unit
    }
    result.isLeft shouldBe true
    f.textOf() shouldBe "XHallo"
    f.history.state shouldBe before
    f.history.undo() shouldBe Right(true)
    f.textOf() shouldBe "Hallo"
    f.history.redo() shouldBe Right(true)
    f.textOf() shouldBe "XHallo"
  }
}
