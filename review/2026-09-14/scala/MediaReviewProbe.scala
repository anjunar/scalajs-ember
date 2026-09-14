package ember.editor.image

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class MediaReviewProbe extends AnyFlatSpec with Matchers {
  "A media host allowlist" should "reject a backslash authority bypass" in {
    val policy = MediaUrlPolicy(hosts = Some(Set("cdn.example")))
    policy.parse("https://evil.example\\@cdn.example/x.png").isLeft shouldBe true
  }

  "The internal-only policy" should "reject a backslash network-path reference" in {
    MediaUrlPolicy.internalOnly.parse("\\\\evil.example/x.png").isLeft shouldBe true
  }
}
