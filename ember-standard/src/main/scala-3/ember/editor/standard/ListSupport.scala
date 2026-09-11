package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.ui.*
import ember.editor.list.*

/** The semantic HTML of lists.
  *
  * Its own object, chosen separately: §6 warns that "Eager Sammelregistrierungen halten optionale
  * Module fest", and an application without lists should not link `ember-list` merely because it
  * wanted paragraphs.
  */
object ListSupport:

  /** `ol` or `ul`, with `start` when the list does not begin at one.
    *
    * §18.2 asks for the start number to survive. HTML has an attribute for it, and writing it only
    * when it differs from the default keeps the common case clean -- a `start="1"` on every list
    * would be noise that no reader and no diff wants.
    *
    * `tight` produces no attribute at all. It is a rendering difference in Markdown, and in HTML it
    * shows up inside the item -- see [[item]].
    */
  val list: HtmlSemantics[ListNode] = new HtmlSemantics[ListNode]:
    val nodeType: NodeType[ListNode] = ListNode

    def shapeOf(node: ListNode, profile: RenderProfile): HtmlShape =
      val tag = node.kind match
        case ListKind.Ordered   => "ol"
        case ListKind.Unordered => "ul"

      val start =
        if node.kind == ListKind.Ordered && node.start != 1 then
          Vector(HtmlAttribute("start", node.start.toString))
        else Vector.empty

      HtmlShape.Element(tag, Identity.of(node.id, profile) ++ start)

  /** `li`.
    *
    * ==What is not done here, and why==
    *
    * A tight list renders its items' single paragraph as bare inline content -- `<li>Text</li>`
    * rather than `<li><p>Text</p></li>`. That would mean suppressing a child, and a
    * [[HtmlSemantics]] describes one node without its children on purpose (§15.1): a shape that
    * could hide children would be a view tree, and something would have to diff it.
    *
    * So the paragraph stays. The tight/loose distinction is kept in the document, where §18.2 wants
    * it, and the Markdown writer in P18 -- which builds its own output rather than projecting
    * components -- is where it will show.
    */
  val item: HtmlSemantics[ListItemNode] = new HtmlSemantics[ListItemNode]:
    val nodeType: NodeType[ListItemNode] = ListItemNode

    def shapeOf(node: ListItemNode, profile: RenderProfile): HtmlShape =
      HtmlShape.Element("li", Identity.of(node.id, profile))

  val semantics: HtmlSupport = HtmlSupport.of(list, item)

  /** Lists on top of the rich-text profile -- the usual set for an application that wants both. */
  val withRichText: HtmlSupport = RichTextSupport.semantics ++ semantics

  val views: ViewSupport = ViewSupport.semantic(withRichText)
