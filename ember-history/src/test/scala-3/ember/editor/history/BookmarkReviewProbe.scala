package ember.editor.history

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class BookmarkReviewProbe extends AnyFlatSpec with Matchers {
  "A bookmark" should "return to its original content after an edit and undo" in {
    val f        = new HistoryFixture()
    val original = Point.textBefore(f.text, 3)
    val bookmark = Bookmark(original, f.session.state.revision)
    f.typeChar("X")
    f.history.undo() shouldBe Right(true)
    f.textOf() shouldBe "Hallo"
    bookmark.resolve(f.session.mappingSince(bookmark.revision).toOption.get) shouldBe Right(
      original
    )
    f.history.redo() shouldBe Right(true)
    bookmark.resolve(f.session.mappingSince(bookmark.revision).toOption.get) shouldBe Right(
      Point.textBefore(f.text, 4)
    )
  }
}
