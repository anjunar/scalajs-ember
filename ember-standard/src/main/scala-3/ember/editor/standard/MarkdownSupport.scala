package ember.editor.standard

import ember.editor.code.*
import ember.editor.core.*
import ember.editor.image.*
import ember.editor.link.*
import ember.editor.list.{ListKind as DocumentListKind, ListItemNode, ListNode}
import ember.editor.markdown.*
import ember.editor.richtext.*

/** The standard Markdown rules: syntax on one side, node types on the other.
  *
  * ==Why they live here==
  *
  * §6 names this module for exactly this: "der einzige Ort, an dem Feature-Nodes und Renderer
  * einander kennen". `ember-markdown` may not know what a `ParagraphNode` is -- it depends on the
  * core alone -- and `ember-rich-text` may not know what Markdown is. This file is where both are
  * on the classpath, and it is the only one.
  *
  * The consequence is worth stating: an application that wants Markdown for its '''own''' block
  * types writes its own rules in its own module and changes nothing here. §6 asks for that ("Eine
  * Fremderweiterung liefert ihre Adapter in ihrem eigenen Modul").
  *
  * ==The policies are injected, for the third time==
  *
  * [[MarkdownRules.links]] and [[MarkdownRules.images]] take a [[LinkUrlPolicy]] and a
  * [[MediaUrlPolicy]]. §19.1 says why: "URLs werden nach Entities-/Whitespace-Normalisierung durch
  * die jeweilige Link-/Media-Policy geprueft." The parser normalises, the rule decides -- and it
  * decides with the same policy the command path uses, because there is no other way to build a
  * [[LinkUrl]] or a [[MediaUrl]].
  *
  * A source whose link the profile refuses does not silently become text. It becomes a diagnostic
  * and the link's '''content''' survives, because losing the words of a sentence because its target
  * was wrong would be the worse failure.
  */
object MarkdownRules:

  // -----------------------------------------------------------------------------------------
  // Bloecke
  // -----------------------------------------------------------------------------------------

  val paragraph: MarkdownBlockRule = new MarkdownBlockRule:
    val id = "ember.markdown.paragraph"

    def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId] =
      block match
        case MarkdownBlock.Paragraph(_, span, _) =>
          Some(sink.add(span)(ParagraphNode(_, children)))
        case _ => None

    def handles(node: EditorNode): Boolean = node.isInstanceOf[ParagraphNode]

    def encode(
        node: EditorNode,
        children: MarkdownChildren,
        sink: SyntaxSink
    ): Option[MarkdownBlock] = node match
      case _: ParagraphNode =>
        Some(MarkdownBlock.Paragraph(sink.fresh(), sink.nowhere, children.inlines))
      case _ => None

  val heading: MarkdownBlockRule = new MarkdownBlockRule:
    val id = "ember.markdown.heading"

    def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId] =
      block match
        case MarkdownBlock.Heading(_, span, level, _, _) =>
          HeadingLevel.fromInt(level) match
            case Some(heading) => Some(sink.add(span)(HeadingNode(_, children, heading)))
            case None          =>
              // Unerreichbar fuer CommonMark -- der Parser laesst nur eins bis sechs zu. Der
              // Fall steht hier, weil eine fremde Syntaxquelle nicht daran gebunden ist.
              sink.note(s"Ueberschriftsebene $level gibt es nicht; wird ein Absatz.", span)
              Some(sink.add(span)(ParagraphNode(_, children)))
        case _ => None

    def handles(node: EditorNode): Boolean = node.isInstanceOf[HeadingNode]

    def encode(
        node: EditorNode,
        children: MarkdownChildren,
        sink: SyntaxSink
    ): Option[MarkdownBlock] = node match
      case value: HeadingNode =>
        // ATX beim Schreiben: die Setext-Schreibweise des Originals traegt das Dokument nicht
        // (§18.2 laesst dem Writer die kanonische Wahl), und eine erfundene waere geraten.
        Some(
          MarkdownBlock.Heading(
            sink.fresh(),
            sink.nowhere,
            value.level.level,
            HeadingStyle.Atx,
            children.inlines
          )
        )
      case _ => None

  val quote: MarkdownBlockRule = new MarkdownBlockRule:
    val id = "ember.markdown.quote"

    def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId] =
      block match
        case MarkdownBlock.BlockQuote(_, span, _) =>
          Some(sink.add(span)(QuoteNode(_, children)))
        case _ => None

    def handles(node: EditorNode): Boolean = node.isInstanceOf[QuoteNode]

    def encode(
        node: EditorNode,
        children: MarkdownChildren,
        sink: SyntaxSink
    ): Option[MarkdownBlock] = node match
      case _: QuoteNode =>
        Some(MarkdownBlock.BlockQuote(sink.fresh(), sink.nowhere, children.blocks))
      case _ => None

  val thematicBreak: MarkdownBlockRule = new MarkdownBlockRule:
    val id = "ember.markdown.thematic-break"

    def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId] =
      block match
        case MarkdownBlock.ThematicBreak(_, span) => Some(sink.add(span)(ThematicBreakNode(_)))
        case _                                    => None

    def handles(node: EditorNode): Boolean = node.isInstanceOf[ThematicBreakNode]

    def encode(
        node: EditorNode,
        children: MarkdownChildren,
        sink: SyntaxSink
    ): Option[MarkdownBlock] = node match
      case _: ThematicBreakNode => Some(MarkdownBlock.ThematicBreak(sink.fresh(), sink.nowhere))
      case _                    => None

  val list: MarkdownBlockRule = new MarkdownBlockRule:
    val id = "ember.markdown.list"

    def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId] =
      block match
        case MarkdownBlock.MarkdownList(_, span, kind, tight, _) =>
          val (documentKind, start) = kind match
            case ListKind.Bullet(_)          => (DocumentListKind.Unordered, 1)
            case ListKind.Ordered(number, _) => (DocumentListKind.Ordered, number)
          Some(sink.add(span)(ListNode(_, children, documentKind, start, tight)))

        case MarkdownBlock.ListItem(_, span, _) =>
          Some(sink.add(span)(ListItemNode(_, children)))

        case _ => None

    def handles(node: EditorNode): Boolean =
      node.isInstanceOf[ListNode] || node.isInstanceOf[ListItemNode]

    def encode(
        node: EditorNode,
        children: MarkdownChildren,
        sink: SyntaxSink
    ): Option[MarkdownBlock] = node match
      case value: ListNode =>
        // `-` und `.` sind die kanonische Schreibweise. Welches Zeichen das Original benutzte,
        // traegt das Dokument nicht, und §18.2 verlangt es auch nicht.
        val kind = value.kind match
          case DocumentListKind.Unordered => ListKind.Bullet('-')
          case DocumentListKind.Ordered   => ListKind.Ordered(value.start, '.')
        Some(
          MarkdownBlock.MarkdownList(sink.fresh(), sink.nowhere, kind, value.tight, children.blocks)
        )

      case _: ListItemNode =>
        Some(MarkdownBlock.ListItem(sink.fresh(), sink.nowhere, children.blocks))

      case _ => None

  /** Code blocks. Fenced and indented both become one node -- the fence is spelling.
    *
    * §18.2: "Inhalt einschliesslich innerer Leerzeilen erhalten; sichere Fence-Laenge beim Export;
    * Info-/Sprachmetadaten typisiert behandeln." The typing is [[CodeInfo]], which P15 already
    * built: a language when the info string names one, and the raw text as meta when it does not,
    * so that a round trip loses neither.
    */
  val code: MarkdownBlockRule = new MarkdownBlockRule:
    val id = "ember.markdown.code"

    def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId] =
      block match
        case MarkdownBlock.CodeBlock(_, span, literal, fence) =>
          val text = sink.add(span)(TextNode(_, literal.stripSuffix("\n"), MarkSet.empty))
          val info = fence.map(value => CodeInfo.parse(value.info)).getOrElse(CodeInfo.empty)
          Some(sink.add(span)(CodeBlockNode(_, Vector(text), info)))
        case _ => None

    def handles(node: EditorNode): Boolean = node.isInstanceOf[CodeBlockNode]

    def encode(
        node: EditorNode,
        children: MarkdownChildren,
        sink: SyntaxSink
    ): Option[MarkdownBlock] = node match
      case value: CodeBlockNode =>
        // Der Inhalt kommt aus dem einen Lauf und nicht aus `children.inlines`: der Lauf eines
        // Codeblocks ist woertlicher Text, und ihn durch die Inline-Regeln zu schicken hiesse,
        // ihn zu escapen (§8.2 -- genau ein unmarkierter Lauf).
        val literal = MarkdownInline.plainText(children.inlines)
        val info    = value.info.render
        Some(
          MarkdownBlock.CodeBlock(
            sink.fresh(),
            sink.nowhere,
            if literal.isEmpty then "" else s"$literal\n",
            Some(Fence('`', 3, info))
          )
        )
      case _ => None

  /** Raw HTML blocks, kept as visible text (§18.1).
    *
    * `RawHtmlPolicy.AsText` is the profile's promise, and this is where it is kept: the literal
    * becomes a paragraph with one text run. Not a code block -- that would claim the author meant
    * code -- and not an HTML node, because there is none and §2 rules one out.
    */
  val rawHtml: MarkdownBlockRule = new MarkdownBlockRule:
    val id = "ember.markdown.raw-html"

    def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId] =
      block match
        case MarkdownBlock.HtmlBlock(_, span, literal) =>
          sink.note("Rohes HTML wird als sichtbarer Text uebernommen (§18.1).", span)
          val text = sink.add(span)(TextNode(_, literal, MarkSet.empty))
          Some(sink.add(span)(ParagraphNode(_, Vector(text))))
        case _ => None

    def handles(node: EditorNode): Boolean = false

    def encode(
        node: EditorNode,
        children: MarkdownChildren,
        sink: SyntaxSink
    ): Option[MarkdownBlock] = None

  // -----------------------------------------------------------------------------------------
  // Inlines
  // -----------------------------------------------------------------------------------------

  val text: MarkdownInlineRule = new MarkdownInlineRule:
    val id = "ember.markdown.text"

    def decode(
        inline: MarkdownInline,
        marks: MarkSet,
        children: Vector[NodeId],
        sink: NodeSink
    ): Option[Vector[NodeId]] = inline match
      case MarkdownInline.Text(_, span, value) =>
        Some(Vector(sink.add(span)(TextNode(_, value, marks))))

      // Ein Code-Span ist ein Lauf mit Mark, kein eigener Knoten (§8.2). Er steht hier und nicht
      // bei den Mark-Regeln, weil sein Inhalt woertlich ist und keine Kinder hat.
      case MarkdownInline.Code(_, span, literal) =>
        Some(Vector(sink.add(span)(TextNode(_, literal, marks + StandardMarks.InlineCode))))

      // Ein weicher Umbruch ist im Dokument ein Leerzeichen: §18.2 verlangt den Unterschied zum
      // harten erhalten, und der harte ist ein Knoten. Ein `\n` in einem Absatzlauf waere ein
      // Dokument, das kein Renderer richtig zeigt (P15 hat dieselbe Entscheidung getroffen).
      case MarkdownInline.SoftBreak(_, span) =>
        Some(Vector(sink.add(span)(TextNode(_, " ", marks))))

      case MarkdownInline.HtmlInline(_, span, literal) =>
        sink.note("Rohes Inline-HTML wird als sichtbarer Text uebernommen (§18.1).", span)
        Some(Vector(sink.add(span)(TextNode(_, literal, marks))))

      case _ => None

    def handles(node: EditorNode): Boolean = node.isInstanceOf[TextNode]

    def encode(
        node: EditorNode,
        children: Vector[MarkdownInline],
        sink: SyntaxSink
    ): Option[Vector[MarkdownInline]] = node match
      case run: TextNode if run.marks.contains(StandardMarks.InlineCode.markId) =>
        Some(Vector(MarkdownInline.Code(sink.fresh(), sink.nowhere, run.text)))
      case run: TextNode =>
        Some(Vector(MarkdownInline.Text(sink.fresh(), sink.nowhere, run.text)))
      case _ => None

  val breaks: MarkdownInlineRule = new MarkdownInlineRule:
    val id = "ember.markdown.break"

    def decode(
        inline: MarkdownInline,
        marks: MarkSet,
        children: Vector[NodeId],
        sink: NodeSink
    ): Option[Vector[NodeId]] = inline match
      case MarkdownInline.HardBreak(_, span) =>
        Some(Vector(sink.add(span)(BreakNode(_, BreakKind.Hard))))
      case _ => None

    def handles(node: EditorNode): Boolean = node.isInstanceOf[BreakNode]

    def encode(
        node: EditorNode,
        children: Vector[MarkdownInline],
        sink: SyntaxSink
    ): Option[Vector[MarkdownInline]] = node match
      case value: BreakNode if value.kind == BreakKind.Hard =>
        Some(Vector(MarkdownInline.HardBreak(sink.fresh(), sink.nowhere)))
      case _: BreakNode =>
        // Ein weicher Umbruch im Dokument ist ein Zeilenumbruch im Quelltext, sonst nichts.
        Some(Vector(MarkdownInline.SoftBreak(sink.fresh(), sink.nowhere)))
      case _ => None

  /** Links. The URL goes through the application's policy, exactly as a command's would. */
  def links(policy: LinkUrlPolicy = LinkUrlPolicy.default): MarkdownInlineRule =
    new MarkdownInlineRule:
      val id = "ember.markdown.link"

      def decode(
          inline: MarkdownInline,
          marks: MarkSet,
          children: Vector[NodeId],
          sink: NodeSink
      ): Option[Vector[NodeId]] = inline match
        case MarkdownInline.Link(_, span, destination, title, _) =>
          policy.parse(destination) match
            case Right(url) =>
              Some(Vector(sink.add(span)(LinkNode(_, children, LinkTarget(url, title)))))
            case Left(error) =>
              // Der Inhalt ueberlebt, das Ziel nicht. Die Woerter eines Satzes zu verlieren,
              // weil seine Adresse falsch war, waere der schlechtere Ausgang.
              sink.note(
                s"Linkziel abgewiesen, Text bleibt: ${error.render}",
                span,
                loss = true
              )
              Some(children)
        case _ => None

      def handles(node: EditorNode): Boolean = node.isInstanceOf[LinkNode]

      def encode(
          node: EditorNode,
          children: Vector[MarkdownInline],
          sink: SyntaxSink
      ): Option[Vector[MarkdownInline]] = node match
        case value: LinkNode =>
          Some(
            Vector(
              MarkdownInline.Link(
                sink.fresh(),
                sink.nowhere,
                value.target.url.value,
                value.target.title,
                children
              )
            )
          )
        case _ => None

  /** Images. Same shape as links, same reason for the policy -- and one loss to report. */
  def images(policy: MediaUrlPolicy = MediaUrlPolicy.default): MarkdownInlineRule =
    new MarkdownInlineRule:
      val id = "ember.markdown.image"

      def decode(
          inline: MarkdownInline,
          marks: MarkSet,
          children: Vector[NodeId],
          sink: NodeSink
      ): Option[Vector[NodeId]] = inline match
        case MarkdownInline.Image(_, span, destination, title, alt) =>
          // Der Alt-Text ist im Dokument ein String. Die Kinder wurden trotzdem dekodiert --
          // der Codec steigt vor der Regel ab --, also muessen sie ausdruecklich weg, sonst
          // haengen sie unerreichbar im Knotensatz.
          sink.discard(children)
          policy.parse(destination) match
            case Right(source) =>
              Some(
                Vector(
                  sink.add(span)(
                    ImageNode(
                      _,
                      MediaReference(source),
                      MarkdownInline.plainText(alt),
                      title.filter(_.trim.nonEmpty)
                    )
                  )
                )
              )
            case Left(error) =>
              sink.note(
                s"Bildquelle abgewiesen, Bild entfaellt: ${error.render}",
                span,
                loss = true
              )
              sink.discard(children)
              Some(Vector.empty)
        case _ => None

      def handles(node: EditorNode): Boolean = node.isInstanceOf[ImageNode]

      def encode(
          node: EditorNode,
          children: Vector[MarkdownInline],
          sink: SyntaxSink
      ): Option[Vector[MarkdownInline]] = node match
        case value: ImageNode =>
          // §18.2 zaehlt Bildmasse ausdruecklich nicht zur CommonMark-Garantie. Sie gehen
          // verloren, und das wird gemeldet statt verschwiegen -- unter `Strict` schlaegt der
          // Export deshalb fehl.
          if value.width.isDefined || value.height.isDefined then
            sink.lost(s"Bildmasse von `${value.id.value}` haben keine Markdown-Schreibweise.")
          if value.source.mediaId.isDefined then
            sink.lost(s"Die MediaId von `${value.id.value}` hat keine Markdown-Schreibweise.")

          val alt =
            if value.alt.isEmpty then Vector.empty
            else Vector(MarkdownInline.Text(sink.fresh(), sink.nowhere, value.alt))

          Some(
            Vector(
              MarkdownInline.Image(sink.fresh(), sink.nowhere, value.src.value, value.title, alt)
            )
          )
        case _ => None

  // -----------------------------------------------------------------------------------------
  // Marks
  // -----------------------------------------------------------------------------------------

  /** Emphasis, strong -- and the two marks Markdown cannot write.
    *
    * §18.2 is explicit: "Underline, Strike, Bildmasse/-ID und GFM-Tabellen sind '''keine'''
    * implizite CommonMark-Garantie." So [[StandardMarks.Underline]] and [[StandardMarks.Strike]]
    * have a rule that reports a loss instead of quietly inventing `<u>` or `~~`. A profile that
    * wants them is a separate, named profile.
    */
  val emphasis: MarkdownMarkRule = markRule("emphasis", 20, StandardMarks.Emphasis) {
    case MarkdownInline.Emphasis(_, _, _) => StandardMarks.Emphasis
  } { (children, sink) =>
    Some(MarkdownInline.Emphasis(sink.fresh(), sink.nowhere, children))
  }

  val strong: MarkdownMarkRule = markRule("strong", 10, StandardMarks.Strong) {
    case MarkdownInline.Strong(_, _, _) => StandardMarks.Strong
  } { (children, sink) =>
    Some(MarkdownInline.Strong(sink.fresh(), sink.nowhere, children))
  }

  /** Inline code, so that the encoder finds an owner for the mark.
    *
    * The decode direction is empty: a code span becomes a run with this mark through [[text]],
    * because its content is verbatim and has no children to descend into. The encode direction is
    * empty too -- [[text]] writes the backticks. What is left is [[owns]], and it has to exist or
    * the export would report a missing rule for a mark that is handled.
    */
  val inlineCode: MarkdownMarkRule = new MarkdownMarkRule:
    val id      = "ember.markdown.inline-code"
    val nesting = 5

    def markFor(inline: MarkdownInline): Option[TextMark] = None
    def owns(mark: TextMark): Boolean                     = mark == StandardMarks.InlineCode

    def inlineFor(
        mark: TextMark,
        children: Vector[MarkdownInline],
        sink: SyntaxSink
    ): Option[MarkdownInline] = children.headOption

  /** The two marks that have no CommonMark spelling.
    *
    * They need a rule even though they can never be decoded: without one, the encoder would find no
    * rule for the mark and the export would fail with "no rule" instead of with the truth, which is
    * that Markdown cannot write it.
    */
  val unwritableMarks: MarkdownMarkRule = new MarkdownMarkRule:
    val id      = "ember.markdown.unwritable-marks"
    val nesting = 90

    def markFor(inline: MarkdownInline): Option[TextMark] = None

    def owns(mark: TextMark): Boolean =
      mark == StandardMarks.Underline || mark == StandardMarks.Strike

    def inlineFor(
        mark: TextMark,
        children: Vector[MarkdownInline],
        sink: SyntaxSink
    ): Option[MarkdownInline] = None

  private def markRule(name: String, order: Int, owned: TextMark)(
      read: PartialFunction[MarkdownInline, TextMark]
  )(write: (Vector[MarkdownInline], SyntaxSink) => Option[MarkdownInline]): MarkdownMarkRule =
    new MarkdownMarkRule:
      val id      = s"ember.markdown.$name"
      val nesting = order

      def markFor(inline: MarkdownInline): Option[TextMark] = read.lift(inline)
      def owns(mark: TextMark): Boolean                     = mark == owned

      def inlineFor(
          mark: TextMark,
          children: Vector[MarkdownInline],
          sink: SyntaxSink
      ): Option[MarkdownInline] = write(children, sink)

/** Ready-made sets, for an application that does not want to list rules itself. */
object MarkdownSupports:

  /** Paragraphs, headings, quotes, breaks, text and the two writable marks. */
  val richText: MarkdownSupport = MarkdownSupport.of(
    MarkdownRules.paragraph,
    MarkdownRules.heading,
    MarkdownRules.quote,
    MarkdownRules.thematicBreak,
    MarkdownRules.rawHtml,
    MarkdownRules.text,
    MarkdownRules.breaks,
    MarkdownRules.strong,
    MarkdownRules.emphasis,
    MarkdownRules.inlineCode,
    MarkdownRules.unwritableMarks
  )

  /** Everything §18.2 lists, with the application's policies.
    *
    * The policies have no defaults here on purpose -- a caller that reaches for "everything" should
    * still have to say which URLs it trusts.
    */
  def everything(
      links: LinkUrlPolicy = LinkUrlPolicy.default,
      media: MediaUrlPolicy = MediaUrlPolicy.default
  ): MarkdownSupport =
    richText ++ MarkdownSupport.of(
      MarkdownRules.list,
      MarkdownRules.code,
      MarkdownRules.links(links),
      MarkdownRules.images(media)
    )
