package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.ui.*
import ember.editor.richtext.*

/** The semantic HTML of the rich-text node types that P12 added: headings, quotes and breaks.
  *
  * Together with [[ParagraphSupport]] this is the whole adapter set of the profile. They are
  * separate objects because §6 says they may be chosen separately -- an application with no
  * headings does not link one in, and "Eager Sammelregistrierungen halten optionale Module fest" is
  * the risk that sentence names.
  */
object RichTextSupport:

  /** `h1` through `h6`. The level is the tag, not an attribute.
    *
    * §16 wants semantic HTML in the delivered version, and a heading's level *is* its semantics: a
    * screen reader builds its outline from `h2` and `h3`, not from `data-level`.
    */
  val heading: HtmlSemantics[HeadingNode] = new HtmlSemantics[HeadingNode]:
    val nodeType: NodeType[HeadingNode] = HeadingNode

    def shapeOf(node: HeadingNode, profile: RenderProfile): HtmlShape =
      HtmlShape.Element(s"h${node.level.level}", Identity.of(node.id, profile), textBlock = true)

  val quote: HtmlSemantics[QuoteNode] = new HtmlSemantics[QuoteNode]:
    val nodeType: NodeType[QuoteNode] = QuoteNode

    def shapeOf(node: QuoteNode, profile: RenderProfile): HtmlShape =
      HtmlShape.Element("blockquote", Identity.of(node.id, profile))

  /** A break.
    *
    * A hard break is `<br>`. A soft one is a line break in the *source* that HTML renders as a
    * space -- so in HTML it is a `<span>` carrying nothing, and the space comes from the text
    * around it. §8.2 keeps the two distinguishable "damit Markdown und semantisches HTML ihre
    * Bedeutung erhalten": Markdown will write the first as a backslash and the second as a plain
    * newline, and both need the node to still be there when P18 asks.
    *
    * `<span>` and not nothing: the node needs an element of its own, because
    * [[ui.core.statement.KeyedChildren]] orders physical children (see [[HtmlShape.TextRun]]).
    */
  val lineBreak: HtmlSemantics[BreakNode] = new HtmlSemantics[BreakNode]:
    val nodeType: NodeType[BreakNode] = BreakNode

    def shapeOf(node: BreakNode, profile: RenderProfile): HtmlShape =
      val tag = node.kind match
        case BreakKind.Hard => "br"
        case BreakKind.Soft => "span"
      HtmlShape.Element(tag, Identity.of(node.id, profile))

  val thematicBreak: HtmlSemantics[ThematicBreakNode] = new HtmlSemantics[ThematicBreakNode]:
    val nodeType: NodeType[ThematicBreakNode] = ThematicBreakNode

    def shapeOf(node: ThematicBreakNode, profile: RenderProfile): HtmlShape =
      HtmlShape.Element("hr", Identity.of(node.id, profile))

  /** All four, plus the three from [[ParagraphSupport]]. */
  val semantics: HtmlSupport =
    ParagraphSupport.semantics ++ HtmlSupport.of(heading, quote, lineBreak, thematicBreak)

  val views: ViewSupport = ViewSupport.semantic(semantics)
