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
