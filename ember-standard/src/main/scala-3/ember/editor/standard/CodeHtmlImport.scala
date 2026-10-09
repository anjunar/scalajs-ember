package ember.editor.standard

import ember.editor.code.{CodeBlockNode, CodeInfo}
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.{ImageNode, MediaReference, MediaUrlPolicy, PositivePixels}
import ember.editor.link.{LinkNode, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListItemNode, ListKind, ListNode}
import ember.editor.richtext.*

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
