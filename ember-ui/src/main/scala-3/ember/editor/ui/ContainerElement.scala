package ember.editor.ui

import ember.editor.core.*
import ember.editor.html.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.TextComponent
import ui.core.render.{Cursor, DomNodes, HostElement, HostNode}

/** Ein Container: seine Kinder haelt eine [[ui.core.statement.KeyedChildren]]-Gruppe.
  *
  * Die Gruppe haengt die Projektion vor dem Mount ein ([[attach]]), montiert wird sie hier --
  * waehrend `compose`, also im selben Durchgang wie der Container selbst. Ein Nachtragen hinterher
  * waere nicht nur umstaendlich, es waere falsch: der Cursor steht dann nicht mehr dort, wo die
  * Kinder hingehoeren.
  */
final class ContainerElement(tagName: String, val editingTextBlock: Boolean = false)
    extends SemanticElement(tagName):

  private var group: Option[AbstractComponent]     = None
  private var tags                                 = Vector.empty[String]
  private var contentOwner: AbstractComponent      = null
  private var caretPlaceholder: AbstractComponent  = null
  private var needsCaretPlaceholder: () => Boolean = () => false

  private[ui] def setCaretProbe(probe: () => Boolean): Unit = needsCaretPlaceholder = probe

  /** A render-only BR supplies the line box of an empty block or trailing newline. It follows the
    * document children and belongs to their UI content container.
    */
  private[ui] def refreshCaretPlaceholder(): Unit =
    if editingTextBlock && isBound then
      if needsCaretPlaceholder() then
        if caretPlaceholder == null then
          val owner = if contentOwner == null then this else contentOwner
          caretPlaceholder =
            Runtime.mount(new CaretPlaceholder(), Runtime.contentCursor(owner), Some(owner))
      else if caretPlaceholder != null then
        Runtime.unmount(caretPlaceholder)
        caretPlaceholder = null

  private[ui] def attach(children: AbstractComponent): Unit =
    require(group.isEmpty && !isBound, "Die Kindergruppe steht vor dem Mount fest.")
    group = Some(children)

  /** The tags between this element and its children -- `<pre>` around `<code>`, say.
    *
    * Fixed before the mount, like the group. A change would mean rebuilding the chain and with it
    * the children, which is a view replacement (§15.1) and goes through `NodeView.accepts`.
    */
  private[ui] def setInner(next: Vector[String]): Unit =
    require(!isBound, "Die inneren Tags stehen vor dem Mount fest.")
    tags = next

  def innerTags: Vector[String] = tags

  /** The element that actually holds the document children.
    *
    * With inner tags -- `<pre><code>` -- that is not this component's own host but the innermost of
    * them. A child boundary of this node sits in '''that''' element, and deriving it from the outer
    * host would be one level off for every code block (§11: "Bei DOM-Elementoffsets zaehlen
    * DOM-Kinder einschliesslich Renderhilfen anders als Dokumentkinder").
    */
  def contentHost: HostElement = if contentOwner == null then host else contentOwner.host

  override def compose(cursor: Cursor): Unit =
    super.compose(cursor)
    if editingTextBlock then host.setAttribute("style", "white-space: pre-wrap")
    group.foreach { children =>
      // Wie ein `foldRight`, behaelt aber den innersten Wrapper. Den kennt sonst niemand
      // wieder: er traegt keine Identitaet, und von aussen liesse er sich nur durch Abzaehlen
      // der Tags erraten.
      var nested: AbstractComponent = children
      tags.reverse.foreach { tag =>
        val wrapper = new MarkElement(tag, nested)
        if contentOwner == null then contentOwner = wrapper
        nested = wrapper
      }
      Runtime.mount(nested, cursor, Some(this)): Unit
    }
    refreshCaretPlaceholder()
