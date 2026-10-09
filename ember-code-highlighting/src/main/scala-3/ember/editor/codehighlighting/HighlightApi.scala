package ember.editor.codehighlighting

import ember.editor.code.CodeBlockNode
import ember.editor.core.*
import ember.editor.ui.{DocumentView, TextRunElement}
import org.scalajs.dom
import ui.core.render.DomNodes

import scala.collection.mutable
import scala.scalajs.js

/** The few calls into the Highlight API. `scalajs-dom` 2.8.1 has no facade for it. */
private object HighlightApi:

  /** The window a node belongs to -- an iframe's own, not the page's (§11, P21). */
  def windowOf(node: dom.Node): Option[js.Dynamic] =
    Option(node.ownerDocument).flatMap { document =>
      val window = document.asInstanceOf[js.Dynamic].defaultView
      if js.isUndefined(window) || window == null then None else Some(window)
    }

  def supported(window: js.Dynamic): Boolean =
    val css = window.CSS
    !js.isUndefined(css) && css != null && !js.isUndefined(css.highlights) &&
    !js.isUndefined(window.Highlight)

  def add(window: js.Dynamic, registry: js.Dynamic, name: String, range: dom.Range): Unit =
    val existing  = registry.get(name)
    val highlight =
      if js.isUndefined(existing) then
        val created = js.Dynamic.newInstance(window.Highlight)()
        registry.set(name, created)
        created
      else existing
    highlight.add(range): Unit

  def remove(registry: js.Dynamic, name: String, range: dom.Range): Unit =
    val existing = registry.get(name)
    if !js.isUndefined(existing) then
      existing.delete(range)
      if existing.size.asInstanceOf[Int] == 0 then registry.delete(name): Unit
