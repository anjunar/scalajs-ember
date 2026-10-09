package ember.editor.ui

import ember.editor.core.*
import ember.editor.html.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.TextComponent
import ui.core.render.{Cursor, DomNodes, HostElement, HostNode}

/** Ein Textlauf mit stabilem Wrapper (§15.1).
  *
  * Das Textkind ueberlebt jede Textaenderung: [[spliceText]] reicht bis zu
  * `CharacterData.replaceData` durch, und ein neuer Textknoten naehme Caret und Selection mit ins
  * Grab. Das ist der Unterschied, um den es §15.1 bei langen Absaetzen geht.
  *
  * Genau eine Aenderung tauscht es doch aus, und §15.1 erlaubt sie ausdruecklich: ein Wechsel der
  * Markierungen ([[setMarkTags]]). Dort steht, warum es nicht anders geht.
  */
final class TextRunElement(tagName: String) extends SemanticElement(tagName):

  private var content                  = new TextComponent()
  private var tags                     = Vector.empty[String]
  private var chain: AbstractComponent = null

  override def compose(cursor: Cursor): Unit =
    super.compose(cursor)
    chain = Runtime.mount(build(), cursor, Some(this))

  def setText(value: String): Unit =
    ensureTextHost()
    content.setText(value)

  /** The DOM text node of this run, once mounted.
    *
    * The `SelectionPort` needs exactly this node: a `Point.Text` offset is a UTF-16 offset into it
    * (§11). Finding it from the outside by descending [[markTags]] levels would be a second
    * description of a structure this component already holds -- and the two would part ways the
    * first time a mark renders as something other than one element.
    */
  def textHost: Option[HostNode] = content.physicalHosts.headOption

  def spliceText(start: Int, deleteCount: Int, inserted: String): Unit =
    ensureTextHost()
    content.spliceText(start, deleteCount, inserted)

  /** Native tooling can replace an identical Text node before the observer runs. Rebuild through UI
    * before writing; never mutate the detached former host. SSR has no DOM host and needs no
    * attachment check.
    */
  private def ensureTextHost(): Unit =
    if isBound then
      DomNodes.option(host).foreach { wrapper =>
        if !textHost.flatMap(DomNodes.option).exists(wrapper.contains) then
          resetText(content.getText)
      }

  def text: String = content.getText

  def markTags: Vector[String] = tags

  /** Replaces the inner semantic tags.
    *
    * §15.1 allows this and names its price in the same sentence: "Mark-Aenderungen koennen
    * semantische Innentags ersetzen und benoetigen Selection-Restoration." The text node inside
    * really is a new one afterwards -- there is no way to turn `<em>` into `<strong>` in place --
    * so a caret standing in it has to be put back. That restoration is the `SelectionPort`'s job
    * (P21); until it exists, this is the one operation in the projection that does not preserve a
    * caret, and it is the only one §15.1 permits not to.
    *
    * The wrapper itself survives. That is the point of having one: the node ID stays on an element
    * that formatting does not touch.
    */
  def setMarkTags(next: Vector[String]): Unit =
    if next != tags then
      tags = next
      if isBound then rebuild()

  /** Rebuilds this run's DOM from a known-good value.
    *
    * §15.4's sanctioned repair: "laesst UI diesen Bereich aus dem gueltigen State neu aufbauen." It
    * exists because a browser can leave more in the wrapper than the one text node the projection
    * owns -- Firefox splits a run into three when an astral character is inserted natively, and a
    * splice afterwards would write into one of them while the others stand.
    *
    * Unlike [[setMarkTags]] this also clears what the projection did not put there. That is the
    * point: the wrapper is the boundary of what this component owns, and after a repair it holds
    * exactly what the document says.
    */
  def resetText(value: String): Unit =
    if isBound then
      if chain != null then Runtime.unmount(chain)
      host.clearChildren()
      content = new TextComponent(value)
      chain = Runtime.mount(build(), Runtime.contentCursor(this), Some(this))
    else content.setText(value)

  private def build(): AbstractComponent =
    tags.foldRight[AbstractComponent](content)((tag, inner) => new MarkElement(tag, inner))

  private def rebuild(): Unit =
    val carried = content.getText
    if chain != null then Runtime.unmount(chain)
    content = new TextComponent(carried)
    chain = Runtime.mount(build(), Runtime.contentCursor(this), Some(this))
