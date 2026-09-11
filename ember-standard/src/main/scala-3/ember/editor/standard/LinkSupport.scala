package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.ui.*
import ember.editor.link.*

/** Links as HTML.
  *
  * ==What "External-Link-Attribute bewusst gesetzt" means here==
  *
  * P14's acceptance asks for the decision to be deliberate, not for a particular answer. The
  * answer this adapter gives is: '''nothing by default'''.
  *
  * `target="_blank"` is an editorial choice, not a technical one. It overrides the reader's own
  * decision about how to open a link, it breaks the back button, and §16 wants the delivered
  * document to be what a reader expects. A library that set it silently would be making that
  * choice for every document written with it.
  *
  * An application that wants it says so -- and then `rel` comes along automatically, because the
  * two belong together: without `rel="noopener"` the opened page can reach back through
  * `window.opener`. Modern browsers imply it, older ones do not, and the attribute costs
  * nothing.
  *
  * `mailto:` and `tel:` never get either. They hand over to another application rather than to
  * another page, so there is no tab to open and no opener to protect.
  */
object LinkSupport:

  /** Links without any external-link attributes. The default. */
  val link: HtmlSemantics[LinkNode] = of(externalTarget = None)

  /** Links whose external targets open in a new tab, with `rel` set accordingly. */
  val openingExternally: HtmlSemantics[LinkNode] = of(externalTarget = Some("_blank"))

  def of(externalTarget: Option[String]): HtmlSemantics[LinkNode] =
    new HtmlSemantics[LinkNode]:
      val nodeType: NodeType[LinkNode] = LinkNode

      def shapeOf(node: LinkNode, profile: RenderProfile): HtmlShape =
        HtmlShape.Element(
          "a",
          Identity.of(node.id, profile) ++
            Vector(HtmlAttribute("href", node.url.value)) ++
            node.title.map(HtmlAttribute("title", _)).toVector ++
            external(node, externalTarget)
        )

  private def external(node: LinkNode, target: Option[String]): Vector[HtmlAttribute] =
    target match
      case Some(value) if LinkUrlPolicy.isExternal(node.url) =>
        Vector(
          HtmlAttribute("target", value),
          HtmlAttribute("rel", "noopener noreferrer")
        )
      case _ => Vector.empty

  val semantics: HtmlSupport = HtmlSupport.of(link)

  /** Links on top of the rich-text profile and lists -- the full standard set. */
  val withEverything: HtmlSupport = ListSupport.withRichText ++ semantics

  val views: ViewSupport = ViewSupport.semantic(withEverything)
