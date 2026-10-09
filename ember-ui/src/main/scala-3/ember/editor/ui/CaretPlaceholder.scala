package ember.editor.ui

import ember.editor.core.*
import ember.editor.html.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.TextComponent
import ui.core.render.{Cursor, DomNodes, HostElement, HostNode}
private final class CaretPlaceholder extends AbstractComponent:
  val tagName: String                        = "br"
  override def compose(cursor: Cursor): Unit =
    host.setAttribute("data-ember-caret", "")
