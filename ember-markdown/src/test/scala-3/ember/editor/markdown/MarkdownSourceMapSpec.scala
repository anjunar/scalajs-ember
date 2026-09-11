package ember.editor.markdown

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Source maps, and the rule SPI without a profile behind it (P18).
  *
  * ==Why this suite builds its own node types==
  *
  * §6 gives this module the core alone. The standard rules live in `ember-standard` and are tested
  * there, against real paragraphs and real links -- but that suite could pass while this module
  * quietly required something from the rich-text profile.
  *
  * So the rules here are local and minimal: two node types nobody else has. That is not a
  * workaround, it is the check. If [[MarkdownCodec]] ever needed to know what a `ParagraphNode` is,
  * this file would stop compiling.
  *
  * ==And why source maps get their own suite==
  *
  * §18.2 asks for two: UTF-16 source ranges and document positions. The first is a property of the
  * parse, the second of the codec, and they are the thing a source view sits on. An off-by-one in
  * either is invisible until someone clicks -- so it gets asserted against real substrings rather
  * than against numbers.
  */
final class MarkdownSourceMapSpec extends AnyFlatSpec with Matchers {

  private val root = NodeId("root")

  // ---------------------------------------------------------------------------------------
  // Ein winziges Profil, das nur diese Suite kennt
  // ---------------------------------------------------------------------------------------

  private val blockRule: MarkdownBlockRule = new MarkdownBlockRule:
    val id = "test.block"

    def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId] =
      block match
        case MarkdownBlock.Paragraph(_, span, _)     => Some(sink.add(span)(Box(_, children)))
        case MarkdownBlock.Heading(_, span, _, _, _) => Some(sink.add(span)(Box(_, children)))
        case MarkdownBlock.BlockQuote(_, span, _)    => Some(sink.add(span)(Box(_, children)))
        case _                                       => None

    def handles(node: EditorNode): Boolean = node.isInstanceOf[Box]

    def encode(
        node: EditorNode,
        children: MarkdownChildren,
        sink: SyntaxSink
    ): Option[MarkdownBlock] = node match
      case _: Box => Some(MarkdownBlock.Paragraph(sink.fresh(), sink.nowhere, children.inlines))
      case _      => None

  private val textRule: MarkdownInlineRule = new MarkdownInlineRule:
    val id = "test.text"

    def decode(
        inline: MarkdownInline,
        marks: MarkSet,
        children: Vector[NodeId],
        sink: NodeSink
    ): Option[Vector[NodeId]] = inline match
      case MarkdownInline.Text(_, span, value) =>
        Some(Vector(sink.add(span)(TextNode(_, value, marks))))
      case MarkdownInline.Code(_, span, literal) =>
        Some(Vector(sink.add(span)(TextNode(_, literal, marks))))
      case MarkdownInline.SoftBreak(_, span) =>
        Some(Vector(sink.add(span)(TextNode(_, " ", marks))))
      case _ => None

    def handles(node: EditorNode): Boolean = node.isInstanceOf[TextNode]

    def encode(
        node: EditorNode,
        children: Vector[MarkdownInline],
        sink: SyntaxSink
    ): Option[Vector[MarkdownInline]] = node match
      case run: TextNode => Some(Vector(MarkdownInline.Text(sink.fresh(), sink.nowhere, run.text)))
      case _             => None

  /** A mark rule for a mark this suite invents. Proves the codec needs no known mark. */
  private val markRule: MarkdownMarkRule = new MarkdownMarkRule:
    val id      = "test.mark"
    val nesting = 1

    def markFor(inline: MarkdownInline): Option[TextMark] = inline match
      case MarkdownInline.Emphasis(_, _, _) => Some(Loud)
      case _                                => None

    def owns(mark: TextMark): Boolean = mark == Loud

    def inlineFor(
        mark: TextMark,
        children: Vector[MarkdownInline],
        sink: SyntaxSink
    ): Option[MarkdownInline] = Some(MarkdownInline.Emphasis(sink.fresh(), sink.nowhere, children))

  private val support = MarkdownSupport.of(blockRule, textRule, markRule)

  private val schema: Schema =
    ExtensionResolver
      .resolve(Vector(TestProfile))
      .map(_.schema)
      .getOrElse(fail("Extensions nicht aufloesbar"))

  private def decode(source: String): DecodedDocument =
    MarkdownCodec.decode(source, schema, support, NodeIdGenerator.sequential("n"), root) match
      case Right(result) => result
      case Left(error)   => fail(s"nicht dekodierbar: ${error.render}")

  // ---------------------------------------------------------------------------------------
  // Die SPI ohne Profil
  // ---------------------------------------------------------------------------------------

  "The codec" should "work with node types it has never heard of" in {
    val result = decode("Ein Absatz.\n")

    result.document.inDocumentOrder.collectFirst { case value: Box => value } should not be empty
  }

  it should "turn an inline into a mark without knowing the mark" in {
    // §8.2: eine Mark ist eine Eigenschaft des Laufs. Der Codec traegt die Menge hinunter und
    // muss nicht wissen, was darin steht.
    val result = decode("*laut*\n")

    result.document.inDocumentOrder.collectFirst { case run: TextNode =>
      run.marks.contains(Loud.markId)
    } shouldBe
      Some(true)
  }

  it should "refuse syntax no rule handles, instead of dropping it" in {
    // Ein stilles Weglassen waere ein Dokument, das der Quelltext nicht meint.
    MarkdownCodec.decode("---\n", schema, support, NodeIdGenerator.sequential("n"), root) should
      matchPattern { case Left(MarkdownError.NoRule(_, _)) => }
  }

  it should "refuse a support that registers a rule twice" in {
    val doubled = support ++ MarkdownSupport.of(blockRule)

    MarkdownCodec.decode("Text\n", schema, doubled, NodeIdGenerator.sequential("n"), root) should
      matchPattern { case Left(MarkdownError.DuplicateRules(_)) => }
  }

  it should "report a mark no rule owns as a loss" in {
    val document = Document.unsafe(
      schema,
      root,
      Vector(
        RootNode(root, Vector(NodeId("b"))),
        Box(NodeId("b"), Vector(NodeId("t"))),
        TextNode(NodeId("t"), "Text", MarkSet.of(Quiet))
      )
    )

    MarkdownCodec.encode(document, support) should matchPattern {
      case Left(MarkdownError.WouldLose(_)) =>
    }
  }

  // ---------------------------------------------------------------------------------------
  // Quellbereiche der Syntax
  // ---------------------------------------------------------------------------------------

  "A syntax span" should "cover the source of its block" in {
    val source = "# Titel\n\nEin Absatz.\n"
    val result = Markdown.parseSyntax(source).getOrElse(fail("nicht parsebar"))

    result.document.children.map(block =>
      source.substring(block.span.start, block.span.end)
    ) shouldBe
      Vector("# Titel", "Ein Absatz.")
  }

  it should "answer through the map by id" in {
    val result  = Markdown.parseSyntax("# Titel\n").getOrElse(fail("nicht parsebar"))
    val heading = result.document.children.head

    result.sourceMap.spanOf(heading.id) shouldBe Some(heading.span)
  }

  // ---------------------------------------------------------------------------------------
  // Dokumentpositionen
  // ---------------------------------------------------------------------------------------

  "A document span" should "cover the source of its node" in {
    val source = "Ein *lauter* Absatz.\n"
    val result = decode(source)

    val loud = result.document.inDocumentOrder
      .collectFirst { case run: TextNode if run.marks.contains(Loud.markId) => run }
      .getOrElse(fail("kein lauter Lauf"))

    val span = result.sourceMap.spanOf(loud.id).getOrElse(fail("keine Spanne"))
    source.substring(span.start, span.end) shouldBe "lauter"
  }

  it should "survive a block marker that is not in the content" in {
    // Der Inhalt eines Zitats ist kein Ausschnitt des Quelltexts -- `> ` faellt beim
    // Blockparsen weg. Ohne die Zeilenkarte zeigte die Spanne zwei Zeichen daneben.
    val source = "> Ein Zitat.\n"
    val result = decode(source)

    val run = result.document.inDocumentOrder
      .collectFirst { case value: TextNode => value }
      .getOrElse(fail("kein Lauf"))

    val span = result.sourceMap.spanOf(run.id).getOrElse(fail("keine Spanne"))
    source.substring(span.start, span.end) shouldBe "Ein Zitat."
  }

  it should "find the innermost node at an offset" in {
    // Ein Kasten und sein einziger Lauf decken dieselben Zeichen ab. Der Gleichstand geht an
    // den zuerst aufgezeichneten -- der Codec dekodiert Kinder vor ihren Eltern, also ist das
    // der tiefere Knoten.
    val source = "Ein Absatz.\n"
    val result = decode(source)

    val found = result.sourceMap.nodeAt(4).getOrElse(fail("nichts gefunden"))
    result.document.node(found) should matchPattern { case Some(_: TextNode) => }
  }

  it should "answer with nothing outside the source" in {
    decode("Text\n").sourceMap.nodeAt(999) shouldBe None
  }

  it should "hold one span per node, the root included" in {
    val result = decode("# Titel\n\n> Zitat\n")

    result.sourceMap.size shouldBe result.document.size
  }

  it should "never point outside the source" in {
    val source = "# Titel\n\n> *Zitat* mit `code`\n\nUnd ein Absatz.\n"
    val result = decode(source)

    result.sourceMap.entries.foreach { (id, span) =>
      withClue(s"${id.value}: ${span.render} in 0..${source.length}\n") {
        span.start should be >= 0
        span.end should be <= source.length
        span.end should be >= span.start
      }
    }
  }
}

/** A block type that exists only in this suite. */
private final case class Box(id: NodeId, children: Vector[NodeId]) extends ElementNode

private object Box extends ElementNodeType[Box]:
  val typeId: NodeTypeId = NodeTypeId("test.box/1")

  def project(node: EditorNode): Option[Box] = node match
    case box: Box => Some(box)
    case _        => None

  def rekey(node: Box, id: NodeId): Box                      = node.copy(id = id)
  def withChildren(node: Box, children: Vector[NodeId]): Box = node.copy(children = children)

/** Two marks nobody else has. */
private case object Loud extends TextMark:
  val markId: MarkId = MarkId("test.loud/1")

private case object Quiet extends TextMark:
  val markId: MarkId = MarkId("test.quiet/1")

private object TestProfile extends Extension:
  val id: ExtensionId = ExtensionId("test.profile")

  override def contribute: ExtensionContributions =
    ExtensionContributions(nodeTypes = Vector(RootNode, TextNode, Box))
