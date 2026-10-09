package ember.editor.link

import ember.editor.core.*
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class LinkReviewProbe extends AnyFlatSpec with Matchers {
  private def fixture(): EditorSession = {
    val generator = NodeIdGenerator.sequential("probe")
    val resolved  =
      ExtensionResolver.resolve(Vector(RichText(generator), LinkExtension(generator))).toOption.get
    EditorSession
      .create(
        Document.unsafe(
          resolved.schema,
          NodeId("root"),
          Vector(
            RootNode(NodeId("root"), Vector(NodeId("p"))),
            ParagraphNode(NodeId("p"), Vector(NodeId("a"), NodeId("link"), NodeId("c"))),
            TextNode(NodeId("a"), "a"),
            LinkNode(
              NodeId("link"),
              Vector(NodeId("b")),
              LinkTarget(LinkUrlPolicy.default.unsafe("https://old.example"))
            ),
            TextNode(NodeId("b"), "bbb"),
            TextNode(NodeId("c"), "c")
          )
        ),
        resolved,
        resolved.sessionConfig()
      )
      .toOption
      .get
  }

  "Enter inside an inline link" should "split the outer paragraph" in {
    val session = fixture()
    session
      .update(_.select(RangeSelection.caret(Point.textBefore(NodeId("b"), 1))): Unit)
      .isRight shouldBe true
    session.dispatch(RichText.InsertParagraph).isRight shouldBe true
    session.document.childrenOf(NodeId("root")) should have size 2
    session.document
      .childrenOf(NodeId("p"))
      .flatMap(session.document.node)
      .exists(_.isInstanceOf[ParagraphNode]) shouldBe false
  }

  "Setting a link across an existing link" should "preserve the text order" in {
    val session = fixture()
    session
      .update(
        _.select(
          RangeSelection(Point.textBefore(NodeId("a"), 0), Point.textBefore(NodeId("c"), 1))
        ): Unit
      )
      .isRight shouldBe true
    session
      .dispatch(
        LinkCommands.SetLink,
        LinkTarget(LinkUrlPolicy.default.unsafe("https://new.example"))
      )
      .isRight shouldBe true
    session.document.inDocumentOrder.collect { case run: TextNode =>
      run.text
    }.mkString shouldBe "abbbc"
  }

  "Splitting an inline link" should "preserve text, target and block structure at either boundary and inside" in {
    Vector(0 -> Vector("a", "bbbc"), 1 -> Vector("ab", "bbc"), 3 -> Vector("abbb", "c")).foreach {
      case (offset, expected) =>
        val session = fixture()
        session
          .update(_.select(RangeSelection.caret(Point.textBefore(NodeId("b"), offset))): Unit)
          .isRight shouldBe true
        session.dispatch(RichText.InsertParagraph).isRight shouldBe true
        val doc = session.document
        doc
          .childrenOf(doc.rootId)
          .map(id =>
            doc
              .subtreeOf(id)
              .flatMap(doc.node)
              .collect { case text: TextNode =>
                text.text
              }
              .mkString
          ) shouldBe expected
        all(doc.inDocumentOrder.collect { case link: LinkNode =>
          link.target.url.value
        }.toVector) shouldBe "https://old.example"
    }
  }
}
