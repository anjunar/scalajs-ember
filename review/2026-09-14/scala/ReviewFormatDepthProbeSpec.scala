package ember.editor.markdown

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class ReviewFormatDepthProbeSpec extends AnyFlatSpec with Matchers {
  "Untrusted Markdown" should "reject a deeply nested inline tree through its error result" in {
    val depth = 3000
    val source = "![" * depth + "x" + "](x)" * depth
    val outcome = try Right(Markdown.parseSyntax(source, MarkdownProfile.untrustedPaste))
      catch { case error: Throwable => Left(s"${error.getClass.getName}: ${error.getMessage}") }
    info(s"depth=$depth, UTF-16 chars=${source.length}, outcome=${outcome.fold(identity, _.fold(_.render, _ => "Right(ParseResult)"))}")
    withClue(outcome.left.toOption.toString) { outcome.isRight shouldBe true }
    outcome.toOption.get.isLeft shouldBe true
  }
}
