package ember.editor.standard

import ember.editor.code.*
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.ui.*

/** Code blocks as HTML.
  *
  * ==Why two tags==
  *
  * `<pre><code>` is what HTML has for this, and both halves earn their place: `pre` preserves the
  * whitespace -- without it every indentation and every blank line collapses -- and `code` says
  * what the content is, which is what a screen reader and a stylesheet read. §16 asks for semantic
  * HTML in the delivered version, and here that means both.
  *
  * They belong to '''one''' document node. Splitting them into two would give the document a
  * structure that only the rendering needs; [[HtmlShape.Element]] carries inner tags for exactly
  * this case.
  *
  * ==The language as a class==
  *
  * `class="language-scala"` on the `<pre>` is the convention every highlighter reads. §19.1
  * excludes "beliebige CSS-Strings als Dokumentformat", and this is not one: the value is built
  * here from a [[CodeLanguage]] that has already been checked for whitespace and backticks, and
  * nothing else may write the attribute.
  *
  * The `meta` half of the info string is '''not''' rendered. It is toolchain configuration --
  * `{highlight=3-5}` and its kin -- and belongs in the Markdown fence it came from (§18.2), not in
  * HTML that a reader gets. It stays in the document, so P18 can write it back.
  */
object CodeSupport:

  val codeBlock: HtmlSemantics[CodeBlockNode] = new HtmlSemantics[CodeBlockNode]:
    val nodeType: NodeType[CodeBlockNode] = CodeBlockNode

    def shapeOf(node: CodeBlockNode, profile: RenderProfile): HtmlShape =
      HtmlShape.Element(
        "pre",
        Identity.of(node.id, profile) ++ language(node),
        Vector("code")
      )

  private def language(node: CodeBlockNode): Vector[HtmlAttribute] =
    node.info.language.toVector.map(value => HtmlAttribute("class", s"language-${value.value}"))

  val semantics: HtmlSupport = HtmlSupport.of(codeBlock)

  /** Everything the standard profile offers: rich text, lists, links and code. */
  val everything: HtmlSupport = LinkSupport.withEverything ++ semantics

  val views: ViewSupport = ViewSupport.semantic(everything)
