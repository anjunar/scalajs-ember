package ember.editor.standard

import ember.editor.code.{CodeBlockNode, CodeInfo}
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.{ImageNode, MediaReference, MediaUrlPolicy, PositivePixels}
import ember.editor.link.{LinkNode, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListItemNode, ListKind, ListNode}
import ember.editor.richtext.*

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
      HtmlImportDecision.Leaf(ThematicBreakNode(scope.nextId()), NodeLevel.Block)

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
