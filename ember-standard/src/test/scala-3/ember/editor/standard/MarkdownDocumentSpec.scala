package ember.editor.standard

import ember.editor.code.*
import ember.editor.core.*
import ember.editor.image.*
import ember.editor.link.*
import ember.editor.list.{ListExtension, ListItemNode, ListNode, ListKind as DocumentListKind}
import ember.editor.markdown.*
import ember.editor.richtext.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Markdown to document and back (P18).
  *
  * The suite that actually exercises §18.1's path: `source → syntax → nodes` with no HTML and
  * no DOM in between. `MarkdownRoundTripSpec` in `ember-markdown` measures the syntax half over
  * the whole corpus; this one is about what arrives in the '''document''', because that is what
  * the editor edits.
  */
final class MarkdownDocumentSpec extends AnyFlatSpec with Matchers {

  private val root  = NodeId("root")
  private val links = LinkUrlPolicy.default
  private val media = MediaUrlPolicy.default

  private val support = MarkdownSupports.everything(links, media)

  private def schema: Schema =
    ExtensionResolver
      .resolve(
        Vector(
          RichText(NodeIdGenerator.sequential("x")),
          ListExtension(NodeIdGenerator.sequential("y")),
          LinkExtension(NodeIdGenerator.sequential("z")),
          CodeExtension(NodeIdGenerator.sequential("c")),
          ImageExtension(NodeIdGenerator.sequential("i"), media)
        )
      )
      .map(_.schema)
      .getOrElse(fail("Extensions nicht aufloesbar"))

  private def decode(source: String): DecodedDocument =
    MarkdownCodec.decode(source, schema, support, NodeIdGenerator.sequential("m"), root) match
      case Right(result) => result
      case Left(error)   => fail(s"nicht dekodierbar: ${error.render}")

  /** The document as one indented line per node -- the shape, without ids. */
  private def outline(source: String): String =
    val document = decode(source).document

    def describe(node: EditorNode): String = node match
      case _: RootNode           => "root"
      case _: ParagraphNode      => "p"
      case value: HeadingNode    => s"h${value.level.level}"
      case _: QuoteNode          => "quote"
      case _: ThematicBreakNode  => "break"
      case value: BreakNode      => s"br(${value.kind.toString.toLowerCase})"
      case value: ListNode =>
        val kind = if value.kind == DocumentListKind.Ordered then s"ol${value.start}" else "ul"
        s"$kind ${if value.tight then "tight" else "loose"}"
      case _: ListItemNode       => "li"
      case value: LinkNode       => s"a(${value.target.url.value})"
      case value: CodeBlockNode  => s"code(${value.info.render})"
      case value: ImageNode      => s"img(${value.src.value},${value.alt})"
      case value: TextNode =>
        val marks = value.marks.markIds.map(_.value.split('.').last.split('/').head).sorted
        s""""${value.text.replace("\n", "\\n")}"${if marks.isEmpty then "" else marks.mkString("[", ",", "]")}"""
      case other => other.getClass.getSimpleName

    def walk(id: NodeId, depth: Int): Vector[String] =
      document.node(id) match
        case None => Vector.empty
        case Some(node) =>
          val children = node match
            case element: ElementNode => element.children.flatMap(walk(_, depth + 1))
            case _                    => Vector.empty
          s"${"  " * depth}${describe(node)}" +: children

    walk(document.rootId, 0).mkString("\n")

  private def encode(
      document: Document,
      policy: LossPolicy = LossPolicy.Strict
  ): Either[MarkdownError, EncodedMarkdown] =
    MarkdownCodec.encode(document, support, policy)

  /** Source to document to source, compared as '''documents'''. §18.2 promises no more. */
  private def stable(source: String): Unit =
    val first = decode(source).document
    val again = encode(first) match
      case Right(written) => decode(written.source).document
      case Left(error)    => fail(s"nicht kodierbar: ${error.render}")

    withClue(s"\ngeschrieben:\n${encode(first).toOption.map(_.source).getOrElse("")}\n") {
      shapeOf(again) shouldBe shapeOf(first)
    }

  /** A document without ids -- what a round trip has to preserve (§18.2: IDs gehoeren nicht
    * zur Aequivalenz).
    */
  private def shapeOf(document: Document): String =
    def walk(id: NodeId): String =
      document.node(id) match
        case None => ""
        case Some(node) =>
          val body = node match
            case value: TextNode =>
              s""""${value.text}"${value.marks.markIds.map(_.value).sorted.mkString("[", ",", "]")}"""
            case value: HeadingNode   => s"h${value.level.level}"
            case value: LinkNode      => s"a(${value.target.url.value},${value.target.title})"
            case value: ImageNode     => s"img(${value.src.value},${value.alt},${value.title})"
            case value: CodeBlockNode => s"code(${value.info.render})"
            case value: ListNode      => s"${value.kind}${value.start}${value.tight}"
            case value: BreakNode     => s"br${value.kind}"
            case other                => other.getClass.getSimpleName

          val children = node match
            case element: ElementNode => element.children.map(walk).mkString("{", ",", "}")
            case _                    => ""
          body + children

    walk(document.rootId)

  // ---------------------------------------------------------------------------------------
  // Bloecke
  // ---------------------------------------------------------------------------------------

  "A source" should "become paragraphs and headings" in {
    outline("# Titel\n\nEin Absatz.\n") shouldBe
      """root
        |  h1
        |    "Titel"
        |  p
        |    "Ein Absatz."""".stripMargin
  }

  it should "become quotes with their blocks" in {
    outline("> eins\n>\n> zwei\n") shouldBe
      """root
        |  quote
        |    p
        |      "eins"
        |    p
        |      "zwei"""".stripMargin
  }

  it should "become lists with items" in {
    outline("- eins\n- zwei\n") shouldBe
      """root
        |  ul tight
        |    li
        |      p
        |        "eins"
        |    li
        |      p
        |        "zwei"""".stripMargin
  }

  it should "keep an ordered start number and the loose flag" in {
    outline("5. fuenf\n\n6. sechs\n") shouldBe
      """root
        |  ol5 loose
        |    li
        |      p
        |        "fuenf"
        |    li
        |      p
        |        "sechs"""".stripMargin
  }

  it should "become a code block with typed language metadata" in {
    // §18.2: "Info-/Sprachmetadaten typisiert behandeln." Die Typisierung ist `CodeInfo` aus
    // P15 -- eine Sprache, wenn der Info-String eine nennt, sonst der Text als Meta.
    outline("```scala\nval x = 1\n```\n") shouldBe
      """root
        |  code(scala)
        |    "val x = 1"""".stripMargin
  }

  it should "keep an info string that names no language" in {
    decode("```nicht-echt!\ncode\n```\n").document.inDocumentOrder
      .collectFirst { case value: CodeBlockNode => value }
      .map(_.info.render) shouldBe Some("nicht-echt!")
  }

  it should "become a thematic break" in {
    outline("---\n") shouldBe
      """root
        |  break""".stripMargin
  }

  // ---------------------------------------------------------------------------------------
  // Inlines und Marks
  // ---------------------------------------------------------------------------------------

  "Emphasis" should "become a mark, not a node" in {
    // §8.2: eine Mark ist eine Eigenschaft des Laufs, kein Knoten um ihn herum. Der Codec
    // traegt die Markmenge den Inline-Baum hinunter, statt eine Huelle zu bauen.
    outline("*kursiv* und **fett**\n") shouldBe
      """root
        |  p
        |    "kursiv"[emphasis]
        |    " und "
        |    "fett"[strong]""".stripMargin
  }

  it should "combine when nested" in {
    outline("***beides***\n") shouldBe
      """root
        |  p
        |    "beides"[emphasis,strong]""".stripMargin
  }

  "A code span" should "become an inline-code mark" in {
    outline("`code`\n") shouldBe
      """root
        |  p
        |    "code"[inline-code]""".stripMargin
  }

  "A link" should "become a LinkNode with its content inside" in {
    outline("""[*Text*](/ziel "Titel")""" + "\n") shouldBe
      """root
        |  p
        |    a(/ziel)
        |      "Text"[emphasis]""".stripMargin
  }

  it should "keep its title" in {
    decode("""[t](/z "Titel")""" + "\n").document.inDocumentOrder
      .collectFirst { case value: LinkNode => value.target.title } shouldBe Some(Some("Titel"))
  }

  "An image" should "become an ImageNode with its alt text flattened" in {
    // Der Alt-Text ist im Syntaxbaum Inline-Struktur, im Dokument ein String: ein Attribut
    // traegt kein Markup. Das Abflachen passiert hier und nicht im Parser, damit die Struktur
    // bis zuletzt erhalten bleibt.
    outline("![*Ein* Bild](/b.png)\n") shouldBe
      """root
        |  p
        |    img(/b.png,Ein Bild)""".stripMargin
  }

  "A hard break" should "become a BreakNode" in {
    outline("eins\\\nzwei\n") shouldBe
      """root
        |  p
        |    "eins"
        |    br(hard)
        |    "zwei"""".stripMargin
  }

  "A soft break" should "become a space" in {
    // §18.2 verlangt den Unterschied zwischen Soft und Hard erhalten. Der harte ist ein Knoten,
    // der weiche ein Leerzeichen -- ein `\n` in einem Absatzlauf waere ein Dokument, das kein
    // Renderer richtig zeigt (dieselbe Entscheidung wie in P15).
    outline("eins\nzwei\n") shouldBe
      """root
        |  p
        |    "eins"
        |    " "
        |    "zwei"""".stripMargin
  }

  // ---------------------------------------------------------------------------------------
  // Policies
  // ---------------------------------------------------------------------------------------

  "A dangerous link target" should "be refused, and the text survive" in {
    // §19.1: dieselbe Policy wie der Command. Die Woerter eines Satzes zu verlieren, weil seine
    // Adresse falsch war, waere der schlechtere Ausgang -- also bleibt der Text.
    val result = decode("[Klick mich](javascript:alert\\(1\\))\n")

    result.document.inDocumentOrder.collectFirst { case value: LinkNode => value } shouldBe None
    result.diagnostics.exists(_.loss) shouldBe true
    outline("[Klick mich](javascript:alert\\(1\\))\n") should include("\"Klick mich\"")
  }

  "A dangerous image source" should "be refused, and the image go" in {
    val result = decode("![alt](javascript:alert\\(1\\))\n")

    result.document.inDocumentOrder.collectFirst { case value: ImageNode => value } shouldBe None
    result.diagnostics.exists(_.loss) shouldBe true
  }

  "A plain http image" should "be refused under the default media profile" in {
    // Die Media-Policy ist strenger als die fuer Links, und das gilt hier genauso wie im
    // Command-Pfad -- es gibt nur eine Tuer.
    decode("![alt](http://example.com/b.png)\n").diagnostics.exists(_.loss) shouldBe true
  }

  it should "pass once the application allows http" in {
    val allowing = MarkdownSupports.everything(links, MediaUrlPolicy.allowingHttp)
    val result = MarkdownCodec
      .decode(
        "![alt](http://example.com/b.png)\n",
        schema,
        allowing,
        NodeIdGenerator.sequential("m"),
        root
      )
      .getOrElse(fail("nicht dekodierbar"))

    result.document.inDocumentOrder.collectFirst { case value: ImageNode => value } should not be empty
  }

  "Raw HTML" should "arrive as visible text" in {
    // §18.1: "Raw HTML wird als sichtbarer Text erhalten statt ausgefuehrt."
    val result = decode("<div onclick=\"boese()\">Inhalt</div>\n")

    outline("<div onclick=\"boese()\">Inhalt</div>\n") should include("<div onclick=")
    result.diagnostics.map(_.message).exists(_.contains("Rohes HTML")) shouldBe true
  }

  // ---------------------------------------------------------------------------------------
  // Export
  // ---------------------------------------------------------------------------------------

  "Encoding" should "refuse a loss under Strict" in {
    // §18.2: "`Strict` verweigert Informationsverlust, `AllowLossy` muss die Anwendung bewusst
    // waehlen." Bildmasse sind ausdruecklich keine CommonMark-Garantie.
    val document = withImage(width = Some(640))

    encode(document) should matchPattern { case Left(MarkdownError.WouldLose(_)) => }
  }

  it should "export the same document under AllowLossy, and say what went" in {
    val document = withImage(width = Some(640))

    encode(document, LossPolicy.AllowLossy) match
      case Right(written) =>
        written.source should include("![Alt](/b.png)")
        written.losses.map(_.message).exists(_.contains("Bildmasse")) shouldBe true
      case Left(error) => fail(s"nicht kodierbar: ${error.render}")
  }

  it should "report an unwritable mark as a loss" in {
    // §18.2 zaehlt Underline ausdruecklich nicht zur CommonMark-Garantie.
    val document = withUnderline

    encode(document) should matchPattern { case Left(MarkdownError.WouldLose(_)) => }

    encode(document, LossPolicy.AllowLossy) match
      case Right(written) =>
        written.source should include("unterstrichen")
        written.losses.map(_.message).exists(_.contains("underline")) shouldBe true
      case Left(error) => fail(s"nicht kodierbar: ${error.render}")
  }

  it should "refuse a support with a duplicate rule id" in {
    val doubled = MarkdownSupports.richText ++ MarkdownSupport.of(MarkdownRules.paragraph)

    MarkdownCodec.encode(decode("Text\n").document, doubled) should matchPattern {
      case Left(MarkdownError.DuplicateRules(_)) =>
    }
  }

  it should "refuse a node no rule handles" in {
    // Ein stilles Weglassen waere ein Export, der weniger enthaelt als das Dokument -- und
    // niemand saehe es.
    val onlyText = MarkdownSupport.of(MarkdownRules.text)

    MarkdownCodec.encode(decode("Text\n").document, onlyText) should matchPattern {
      case Left(MarkdownError.NoNodeRule(_, _)) =>
    }
  }

  // ---------------------------------------------------------------------------------------
  // Round-Trip durch das Dokument
  // ---------------------------------------------------------------------------------------

  "A document" should "survive an export and a re-import" in {
    stable("# Titel\n\nEin Absatz mit *kursiv* und **fett**.\n")
  }

  it should "survive quotes and lists" in {
    stable("> Zitat\n\n- eins\n- zwei\n")
  }

  it should "survive a nested list" in {
    stable("- aussen\n  - innen\n")
  }

  it should "survive code with a language" in {
    stable("```scala\nval x = 1\n\nval y = 2\n```\n")
  }

  it should "survive links and images" in {
    stable("""[Text](/ziel "T") und ![Alt](/b.png "B")""" + "\n")
  }

  it should "survive an ordered list that does not start at one" in {
    stable("5. fuenf\n6. sechs\n")
  }

  it should "survive hard and soft breaks" in {
    stable("eins\\\nzwei\n")
  }

  it should "survive a code span next to emphasis" in {
    stable("`code` und *kursiv*\n")
  }

  it should "survive text that looks like syntax" in {
    // Der Fall, den nur ein Round-Trip findet: der Writer muss escapen, was sich sonst als
    // Markup zuruecklaese.
    stable("Ein \\* Stern und ein \\_ Unterstrich und \\[Klammern\\]\n")
  }

  // ---------------------------------------------------------------------------------------
  // Source-Map
  // ---------------------------------------------------------------------------------------

  "The document source map" should "point every node at its source" in {
    // §18.2: "SourceMaps erfassen UTF-16-Quellbereiche '''und Dokumentpositionen'''." Die zweite
    // Haelfte ist genau das hier -- und sie ist, was eine Quelltextansicht braucht, um den
    // Knoten unter dem Caret zu markieren.
    val source = "# Titel\n\nEin Absatz.\n"
    val result = decode(source)

    val heading = result.document.inDocumentOrder.collectFirst { case value: HeadingNode => value }
      .getOrElse(fail("keine Ueberschrift"))

    val span = result.sourceMap.spanOf(heading.id).getOrElse(fail("keine Spanne"))
    source.substring(span.start, span.end) shouldBe "# Titel"
  }

  it should "find the node at an offset" in {
    val source = "# Titel\n\nEin Absatz.\n"
    val result = decode(source)

    val found = result.sourceMap.nodeAt(source.indexOf("Absatz")).getOrElse(fail("nichts gefunden"))
    result.document.node(found) should matchPattern { case Some(_: TextNode) => }
  }

  it should "hold one span per node" in {
    val result = decode("# Titel\n\n> - eins\n")

    result.sourceMap.size shouldBe result.document.size
  }

  it should "point through a block marker that is not in the content" in {
    val source = "> Ein Zitat.\n"
    val result = decode(source)

    val run = result.document.inDocumentOrder.collectFirst { case value: TextNode => value }
      .getOrElse(fail("kein Lauf"))

    val span = result.sourceMap.spanOf(run.id).getOrElse(fail("keine Spanne"))
    source.substring(span.start, span.end) shouldBe "Ein Zitat."
  }

  // ---------------------------------------------------------------------------------------
  // Hilfsdokumente
  // ---------------------------------------------------------------------------------------

  private def withImage(width: Option[Int]): Document =
    Document.unsafe(
      schema,
      root,
      Vector(
        RootNode(root, Vector(NodeId("p"))),
        ParagraphNode(NodeId("p"), Vector(NodeId("img"))),
        ImageNode(
          NodeId("img"),
          MediaReference(media.unsafe("/b.png")),
          "Alt",
          None,
          width.flatMap(PositivePixels.parse)
        )
      )
    )

  private def withUnderline: Document =
    Document.unsafe(
      schema,
      root,
      Vector(
        RootNode(root, Vector(NodeId("p"))),
        ParagraphNode(NodeId("p"), Vector(NodeId("t"))),
        TextNode(NodeId("t"), "unterstrichen", MarkSet.of(StandardMarks.Underline))
      )
    )
}
