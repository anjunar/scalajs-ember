package ember.editor.standard

import ember.editor.code.{CodeBlockNode, CodeInfo}
import ember.editor.core.*
import ember.editor.html.*
import ember.editor.image.{ImageNode, MediaReference, MediaUrlPolicy, PositivePixels}
import ember.editor.link.{LinkNode, LinkTarget, LinkUrlPolicy}
import ember.editor.list.{ListItemNode, ListKind, ListNode}
import ember.editor.richtext.*

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
