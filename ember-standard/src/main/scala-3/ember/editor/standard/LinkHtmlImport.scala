package ember.editor.standard

import ember.editor.code.{CodeBlockNode, CodeInfo}
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.{ImageNode, MediaReference, MediaUrlPolicy, PositivePixels}
import ember.editor.link.{LinkNode, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListItemNode, ListKind, ListNode}
import ember.editor.richtext.*

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
