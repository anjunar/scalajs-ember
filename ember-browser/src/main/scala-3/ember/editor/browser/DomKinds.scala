package ember.editor.browser

import org.scalajs.dom

import scala.scalajs.js

/** What kind of DOM node something is, asked in a way that survives a second document.
  *
  * ==Why not `isInstanceOf`==
  *
  * Because it is wrong across realms, and silently so. A Scala.js type test on a facade type
  * compiles to `instanceof`, and `instanceof Text` checks against the '''current window's''' `Text`
  * constructor. A text node belonging to an iframe's document is an instance of that frame's
  * `Text`, not of this one -- so the test fails, the node is classified as "something else", and a
  * selection port reports a position it cannot map.
  *
  * That is exactly what happened: every mapping in an iframe came back as "not projected", with the
  * right elements sitting in the DOM the whole time. §15.4 asks for `ownerDocument` and
  * `defaultView` to be used for an editor host in an iframe, and this is the half of that rule that
  * is easy to miss -- it is not only about which `window` is asked, but about not assuming there is
  * only one.
  *
  * `nodeType` is a number defined by the DOM specification. It means the same thing in every realm,
  * which is the whole point.
  */
private[browser] object DomKinds:

  private val ElementNode = 1
  private val TextNode    = 3
  private val CommentNode = 8

  def isElement(node: dom.Node): Boolean = node != null && node.nodeType == ElementNode

  def isText(node: dom.Node): Boolean = node != null && node.nodeType == TextNode

  def isComment(node: dom.Node): Boolean = node != null && node.nodeType == CommentNode

  def asElement(node: dom.Node): Option[dom.Element] =
    Option.when(isElement(node))(node.asInstanceOf[dom.Element])

  def asText(node: dom.Node): Option[dom.Text] =
    Option.when(isText(node))(node.asInstanceOf[dom.Text])

  /** How many UTF-16 units or children a node holds -- the upper bound of a DOM offset. */
  def extentOf(node: dom.Node): Int =
    if isText(node) then node.asInstanceOf[dom.Text].length
    else node.childNodes.length

  /** Whether an element can take focus, asked of the object rather than of its class.
    *
    * Same reason as above: an `HTMLElement` from another document does not pass `instanceof
    * HTMLElement` here. What matters is whether it has the behaviour, and a DOM object answers that
    * directly.
    */
  def isFocusable(element: dom.Element): Boolean =
    val dynamic  = element.asInstanceOf[js.Dynamic]
    val editable = dynamic.isContentEditable
    (!js.isUndefined(editable) && editable.asInstanceOf[Boolean]) ||
    element.hasAttribute("tabindex")

  /** Calls `focus()` if the element has one. */
  def focus(element: dom.Element): Unit =
    val dynamic = element.asInstanceOf[js.Dynamic]
    if js.typeOf(dynamic.focus) == "function" then dynamic.focus(): Unit
