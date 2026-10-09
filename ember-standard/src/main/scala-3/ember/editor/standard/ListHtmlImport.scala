package ember.editor.standard

import ember.editor.code.{CodeBlockNode, CodeInfo}
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.{ImageNode, MediaReference, MediaUrlPolicy, PositivePixels}
import ember.editor.link.{LinkNode, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListItemNode, ListKind, ListNode}
import ember.editor.richtext.*

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
