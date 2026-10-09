package ember.editor.ui

import ember.editor.core.*
import ember.editor.html.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.TextComponent
import ui.core.render.{Cursor, DomNodes, HostElement, HostNode}

/** One tag that only describes: a mark around a text run, or `<code>` inside a `<pre>`.
  *
  * Carries no attributes and no identity. Neither a mark (§8.2) nor a presentational wrapper is a
  * node -- they have no ID, and nothing outside their chain ever needs to find them again.
  */
private final class MarkElement(val tagName: String, inner: AbstractComponent)
    extends AbstractComponent:

  override def compose(cursor: Cursor): Unit = Runtime.mount(inner, cursor, Some(this)): Unit
