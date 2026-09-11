package ember.editor.browser

import org.scalajs.dom

import scala.scalajs.js

/** Which document, window and selection an editing host actually belongs to.
  *
  * ==Why not `dom.window`==
  *
  * §15.4 is explicit: "Fuer Editor-Hosts in iframes werden ownerDocument/defaultView verwendet." A
  * host inside an iframe has its own document and its own selection, and the global `window` of the
  * outer page knows nothing about either. Code that reaches for `dom.window.getSelection()` works
  * until someone embeds the editor, and then it silently reads an empty selection instead of
  * failing.
  *
  * Everything the selection and focus machinery touches goes through this object, so there is
  * exactly one place where that decision is made.
  */
final class BrowserScope private (val host: dom.Element):

  /** The document the host lives in -- not necessarily the one this code was loaded into. */
  def ownerDocument: dom.Document = host.ownerDocument

  /** The same document, seen as HTML.
    *
    * `defaultView` and `activeElement` live on `HTMLDocument` in the `scalajs-dom` 2.8.1 facade,
    * not on `Document`. An editing host is an HTML element in an HTML document -- that is what
    * `contenteditable` means -- so the cast states a fact rather than hoping for one.
    */
  private def htmlDocument: dom.HTMLDocument = host.ownerDocument.asInstanceOf[dom.HTMLDocument]

  /** The window of that document.
    *
    * `None` for a detached or foreign-document host: a node created by `DOMParser` or belonging to
    * an iframe that has since been removed has an `ownerDocument` without a `defaultView`. There is
    * nothing to select in it, and saying so is better than reaching for a window that belongs to
    * someone else.
    */
  def window: Option[dom.Window] = Option(htmlDocument.defaultView)

  /** The active element of this scope.
    *
    * Inside a shadow root that is the root's own `activeElement`; the document reports the shadow
    * '''host''' instead, which is true and useless -- it never names anything inside.
    */
  def activeElement: Option[dom.Element] =
    shadowRoot match
      case Some(root) => Option(root.activeElement)
      case None       => Option(htmlDocument.activeElement)

  /** Whether the focus is inside the editing host.
    *
    * The question every write has to ask first (§22: "Hintergrundupdates stehlen weder Page- noch
    * Textarea-Fokus"). Inside, not equal to: the active element may be a nested control.
    */
  def focusWithin: Boolean = activeElement.exists(contains)

  /** Whether a node belongs to this host's subtree.
    *
    * `host.contains(host)` is `true` in the DOM, and that is what is wanted: a selection anchored
    * on the host element itself is a selection in the editor.
    */
  def contains(node: dom.Node): Boolean = host.contains(node)

  /** Whether the host can take focus at all.
    *
    * §22 keeps the two apart: "Fokusfaehigkeit und Editierbarkeit sind getrennte Entscheidungen." A
    * readonly rich view may well be focusable, and an editable one has to be. `tabindex` is read as
    * an attribute because the `scalajs-dom` 2.8.1 facade has no `tabIndex`; the attribute is what
    * an editor sets anyway.
    */
  def focusable: Boolean = DomKinds.isFocusable(host)

  /** Moves the focus to the host. Called only where a rule allows it -- see [[FocusPolicy]]. */
  def focus(): Unit = DomKinds.focus(host)

  /** What this scope can do about selection, honestly stated.
    *
    * §15.4: "Shadow-DOM-Selection ist ein eigener Capability-Test; es wird nicht behauptet, globale
    * `window.getSelection` loese diesen Fall."
    */
  def capability: SelectionCapability =
    shadowRoot match
      case None =>
        if window.isDefined then SelectionCapability.Document else SelectionCapability.Detached
      case Some(root) =>
        if js.typeOf(root.asInstanceOf[js.Dynamic].getSelection) == "function" then
          SelectionCapability.ShadowNative
        else SelectionCapability.ShadowUnsupported

  /** The selection object to read and write, or `None` when there is none to be had.
    *
    * A shadow root without its own `getSelection` returns `None` rather than the document's
    * selection. The document's selection in that case reports the '''shadow host''' as the anchor
    * and hides everything inside -- a value that looks plausible and means nothing.
    */
  def selection: Option[dom.Selection] =
    capability match
      case SelectionCapability.Document =>
        window.flatMap(view => Option(view.getSelection()))
      case SelectionCapability.ShadowNative =>
        shadowRoot
          .map(_.asInstanceOf[js.Dynamic].getSelection())
          .filterNot(js.isUndefined)
          .filterNot(_ == null)
          .map(_.asInstanceOf[dom.Selection])
      case SelectionCapability.ShadowUnsupported | SelectionCapability.Detached => None

  /** The shadow root containing the host, if it is in one.
    *
    * `getRootNode` is not in the `scalajs-dom` 2.8.1 facade; every target engine has it. The
    * dynamic access is this file's one narrow spot, next to `getSelection` on a shadow root, which
    * is not standard at all.
    */
  private def shadowRoot: Option[dom.ShadowRoot] =
    val dynamic = host.asInstanceOf[js.Dynamic]
    if js.typeOf(dynamic.getRootNode) != "function" then None
    else
      val root = dynamic.getRootNode()
      if js.isUndefined(root) || root == null then None
      else
        val node = root.asInstanceOf[dom.Node]
        // 11 is DOCUMENT_FRAGMENT_NODE. A host outside any shadow root reports its document.
        Option.when(node.nodeType == 11 && (node ne ownerDocument))(
          node.asInstanceOf[dom.ShadowRoot]
        )

object BrowserScope:

  def of(host: dom.Element): BrowserScope = new BrowserScope(host)

/** What a scope can do about selection. Reported, never assumed. */
enum SelectionCapability:

  /** The ordinary case: a host in a document with a window. */
  case Document

  /** A host in a shadow root whose engine offers `ShadowRoot.getSelection` (Chromium today). */
  case ShadowNative

  /** A host in a shadow root without one. Selection inside it cannot be read (§15.4). */
  case ShadowUnsupported

  /** A detached host, or a document without a window. */
  case Detached

  def isSupported: Boolean = this == Document || this == ShadowNative
