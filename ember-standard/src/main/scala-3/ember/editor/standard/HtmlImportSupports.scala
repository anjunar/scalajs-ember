package ember.editor.standard

import ember.editor.code.{CodeBlockNode, CodeInfo}
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.{ImageNode, MediaReference, MediaUrlPolicy, PositivePixels}
import ember.editor.link.{LinkNode, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListItemNode, ListKind, ListNode}
import ember.editor.richtext.*

/** Which HTML the paragraph profile can read.
  *
  * ==Why the profile lives here and not in `ember-html`==
  *
  * Because a paragraph is a rich-text idea. §6 keeps the HTML module free of node types, and it is
  * the same separation that keeps the export side honest: `ember-html` knows what a tag is, the
  * profile knows what a document is, and the two meet in this module.
  */
object ParagraphHtmlImport:

  /** Tags that open a block, for the one decision a rule cannot make from its own element.
    *
    * A `<div>` is a paragraph when it holds prose and a wrapper when it holds blocks, and the
    * difference is in its children. Asking the rule table recursively would be circular; a list of
    * names is honest about what it is.
    */
  private val blockTags = Set(
    "p",
    "div",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "blockquote",
    "pre",
    "ul",
    "ol",
    "li",
    "table",
    "tr",
    "td",
    "th",
    "section",
    "article",
    "header",
    "footer",
    "aside",
    "nav",
    "main",
    "figure",
    "figcaption",
    "dl",
    "dt",
    "dd",
    "hr"
  )

  private def holdsBlocks(element: HtmlFragment.Element): Boolean =
    element.children.exists {
      case child: HtmlFragment.Element => blockTags.contains(child.tag)
      case _                           => false
    }

  val profile: HtmlImportProfile = new HtmlImportProfile:
    def paragraph(children: Vector[NodeId], scope: HtmlImportScope): EditorNode =
      ParagraphNode(scope.nextId(), children)

    def text(value: String, marks: MarkSet, scope: HtmlImportScope): EditorNode =
      TextNode(scope.nextId(), value, marks)

    def root(children: Vector[NodeId], id: NodeId): EditorNode = RootNode(id, children)

  val paragraph: HtmlImportRule =
    HtmlImportRule.container("html.paragraph", Set("p")) { (_, children, scope) =>
      ParagraphNode(scope.nextId(), children)
    }

  /** A `div` is whichever of the two it turns out to be.
    *
    * Word and every web page wrap prose in `div`s for layout. Treating one as a paragraph when it
    * holds prose keeps the paragraph boundary; dissolving it when it holds blocks keeps the blocks
    * from ending up inside one.
    */
  val division: HtmlImportRule = new HtmlImportRule:
    val name = "html.div"

    def handles(element: HtmlFragment.Element): Boolean =
      Set("div", "section", "article", "main", "header", "footer", "aside", "figure")
        .contains(element.tag)

    def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
      if holdsBlocks(element) then HtmlImportDecision.Unwrap
      else
        HtmlImportDecision.Container(
          children => ParagraphNode(scope.nextId(), children),
          NodeLevel.Block,
          ChildMode.Inline
        )

  val rules: Vector[HtmlImportRule] = Vector(paragraph, division)

/** Headings, quotes, breaks and the marks (§8.2). */
object RichTextHtmlImport:

  private val levels = Map(
    "h1" -> HeadingLevel.H1,
    "h2" -> HeadingLevel.H2,
    "h3" -> HeadingLevel.H3,
    "h4" -> HeadingLevel.H4,
    "h5" -> HeadingLevel.H5,
    "h6" -> HeadingLevel.H6
  )

  val heading: HtmlImportRule = new HtmlImportRule:
    val name = "html.heading"

    def handles(element: HtmlFragment.Element): Boolean = levels.contains(element.tag)

    def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
      HtmlImportDecision.Container(
        children => HeadingNode(scope.nextId(), children, levels(element.tag)),
        NodeLevel.Block,
        ChildMode.Inline
      )

  /** A quote holds blocks, whether or not the HTML wrote any. */
  val quote: HtmlImportRule =
    HtmlImportRule.container("html.quote", Set("blockquote"), ChildMode.Blocks) {
      (_, children, scope) => QuoteNode(scope.nextId(), children)
    }

  /** A `<br>` is a hard break: the author asked for it (§8.2). */
  val lineBreak: HtmlImportRule = new HtmlImportRule:
    val name                                            = "html.br"
    def handles(element: HtmlFragment.Element): Boolean = element.tag == "br"
    def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
      HtmlImportDecision.Leaf(BreakNode(scope.nextId(), BreakKind.Hard))

  val thematicBreak: HtmlImportRule = new HtmlImportRule:
    val name                                            = "html.hr"
    def handles(element: HtmlFragment.Element): Boolean = element.tag == "hr"
    def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
      HtmlImportDecision.Leaf(ThematicBreakNode(scope.nextId()))

  /** The marks, with every spelling that occurs.
    *
    * `<b>` and `<strong>` mean the same to a document even though they do not to HTML, and a paste
    * that kept only one of them would lose half the bold text on the web. `<del>` and `<ins>` are
    * the semantic pair for strike-through; Word emits `<s>`, browsers `<strike>`.
    */
  val marks: Vector[HtmlImportRule] = Vector(
    HtmlImportRule.mark("html.strong", Set("b", "strong"), StandardMarks.Strong),
    HtmlImportRule
      .mark("html.emphasis", Set("i", "em", "cite", "var", "dfn"), StandardMarks.Emphasis),
    HtmlImportRule.mark("html.underline", Set("u", "ins"), StandardMarks.Underline),
    HtmlImportRule.mark("html.strike", Set("s", "strike", "del"), StandardMarks.Strike),
    InlineCodeRule
  )

  /** `<code>` is a mark -- except inside a `<pre>`.
    *
    * There it is the wrapper every highlighter writes, and it means nothing of its own; marking the
    * whole block as inline code would be a second, contradictory statement about the same text. The
    * distinction needs the context, which is why [[HtmlImportScope.insideTag]] exists.
    */
  object InlineCodeRule extends HtmlImportRule:
    val name = "html.inline-code"

    def handles(element: HtmlFragment.Element): Boolean =
      Set("code", "kbd", "samp", "tt").contains(element.tag)

    def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
      if scope.insideTag("pre") then HtmlImportDecision.Unwrap
      else HtmlImportDecision.Marked(StandardMarks.InlineCode)

  val rules: Vector[HtmlImportRule] =
    Vector(heading, quote, lineBreak, thematicBreak) ++ marks

/** Lists. */
object ListHtmlImport:

  val list: HtmlImportRule = new HtmlImportRule:
    val name = "html.list"

    def handles(element: HtmlFragment.Element): Boolean =
      element.tag == "ul" || element.tag == "ol"

    def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
      val kind = if element.tag == "ol" then ListKind.Ordered else ListKind.Unordered
      // `start` is the one numeric attribute a list carries, and losing it renumbers a pasted
      // list from one.
      val start = element.attributes
        .find(_.name == "start")
        .flatMap(attribute => attribute.value.toIntOption)
        .filter(_ > 0)
        .getOrElse(1)

      HtmlImportDecision.Container(
        children => ListNode(scope.nextId(), children, kind, start),
        NodeLevel.Block,
        ChildMode.Blocks
      )

  /** An item holds blocks. `<li>a</li>` writes none, and the paragraph is supplied (§13). */
  val item: HtmlImportRule =
    HtmlImportRule.container("html.list-item", Set("li"), ChildMode.Blocks) {
      (_, children, scope) => ListItemNode(scope.nextId(), children)
    }

  val rules: Vector[HtmlImportRule] = Vector(list, item)

/** Links, through the application's URL policy (§19.1). */
object LinkHtmlImport:

  /** @param policy
    *   the same one the editor uses for a typed link. §19.1 sends an imported URL "durch die
    *   jeweilige Link-/Media-Policy" -- the same one, not a laxer copy, or a paste would be a way
    *   past it.
    */
  def link(policy: LinkUrlPolicy): HtmlImportRule = new HtmlImportRule:
    val name = "html.link"

    def handles(element: HtmlFragment.Element): Boolean = element.tag == "a"

    def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
      element.attributes.find(_.name == "href").map(_.value) match
        // An anchor without a target is not a link, it is a name. Its text is what matters.
        case None => HtmlImportDecision.Unwrap

        case Some(raw) =>
          scope.url(raw).flatMap(url => policy.parse(url).toOption) match
            case None =>
              // §19.1: the text survives, the link does not.
              scope.note(HtmlLoss.RefusedUrl, s"<a href>: ${raw.take(60)}")
              HtmlImportDecision.Unwrap

            case Some(url) =>
              val title = element.attributes.find(_.name == "title").map(_.value)
              // §8.2: an inline container, not a block and not a mark.
              HtmlImportDecision.Container(
                children => LinkNode(scope.nextId(), children, LinkTarget(url, title)),
                NodeLevel.Inline,
                ChildMode.Inline
              )

  def rules(policy: LinkUrlPolicy): Vector[HtmlImportRule] = Vector(link(policy))

/** Code blocks, the one place where whitespace is content. */
object CodeHtmlImport:

  /** `<pre>`, with the language from a `<code class="language-x">` inside it.
    *
    * The nesting is the convention every highlighter uses, and reading the class off the inner
    * element is the only way to keep the language. The inner `<code>` itself is then not an inline
    * code mark -- hence the priority, which puts this rule ahead of the mark rule.
    */
  val codeBlock: HtmlImportRule = new HtmlImportRule:
    val name              = "html.code-block"
    override val priority = 10

    def handles(element: HtmlFragment.Element): Boolean = element.tag == "pre"

    def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
      val info = languageOf(element).map(CodeInfo.of).getOrElse(CodeInfo.empty)

      HtmlImportDecision.Container(
        children => CodeBlockNode(scope.nextId(), children, info),
        NodeLevel.Block,
        ChildMode.Inline,
        Whitespace.Preserve
      )

    private def languageOf(element: HtmlFragment.Element): Option[String] =
      element.children.collectFirst {
        case child: HtmlFragment.Element if child.tag == "code" =>
          child.attributes
            .find(_.name == "class")
            .map(_.value)
            .flatMap(_.split(' ').find(_.startsWith("language-")))
            .map(_.drop("language-".length))
      }.flatten

  val rules: Vector[HtmlImportRule] = Vector(codeBlock)

/** Images, through the media policy -- and with the alt text kept when it refuses (§19.1). */
object ImageHtmlImport:

  def image(policy: MediaUrlPolicy): HtmlImportRule = new HtmlImportRule:
    val name = "html.image"

    def handles(element: HtmlFragment.Element): Boolean = element.tag == "img"

    def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
      val alt = element.attributes.find(_.name == "alt").map(_.value).getOrElse("")
      val raw = element.attributes.find(_.name == "src").map(_.value)

      raw.flatMap(scope.url).flatMap(url => policy.parse(url).toOption) match
        case Some(url) =>
          HtmlImportDecision.Leaf(
            ImageNode(
              scope.nextId(),
              MediaReference(url),
              alt,
              element.attributes.find(_.name == "title").map(_.value),
              pixels(element, "width"),
              pixels(element, "height")
            )
          )

        case None =>
          // §19.1: "Unsichere Bilder werden mit Diagnose als Alt-Text erhalten." A picture that
          // cannot be shown is still something the author wrote about.
          scope.note(HtmlLoss.RefusedUrl, s"<img src>: ${raw.getOrElse("").take(60)}")
          if alt.isEmpty then HtmlImportDecision.Discard("unzulaessige Quelle, kein Alt-Text")
          else HtmlImportDecision.Leaf(TextNode(scope.nextId(), alt))

    private def pixels(element: HtmlFragment.Element, name: String): Option[PositivePixels] =
      element.attributes
        .find(_.name == name)
        .flatMap(a => a.value.toIntOption)
        .flatMap(PositivePixels.parse)

  def rules(policy: MediaUrlPolicy): Vector[HtmlImportRule] = Vector(image(policy))

/** The assembled profiles, the counterpart to [[RichTextSupport]] on the export side. */
object StandardHtmlImport:

  /** Paragraphs, headings, quotes, breaks and marks. No lists, links, code or images. */
  val richText: HtmlImportSupport =
    HtmlImportSupport.of(
      ParagraphHtmlImport.profile,
      (ParagraphHtmlImport.rules ++ RichTextHtmlImport.rules)*
    )

  /** Everything this repository can read.
    *
    * The policies come from the caller for the reason §19.1 gives: an imported URL goes through the
    * same policy a typed one does, and a library that shipped its own would be a way around the
    * application's.
    */
  def everything(links: LinkUrlPolicy, media: MediaUrlPolicy): HtmlImportSupport =
    HtmlImportSupport.of(
      ParagraphHtmlImport.profile,
      (ParagraphHtmlImport.rules ++
        RichTextHtmlImport.rules ++
        ListHtmlImport.rules ++
        LinkHtmlImport.rules(links) ++
        CodeHtmlImport.rules ++
        ImageHtmlImport.rules(media))*
    )
