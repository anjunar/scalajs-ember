package ember.editor.integration

import ember.editor.browser.*
import ember.editor.code.{CodeBlockNode, CodeExtension}
import ember.editor.core.*
import ember.editor.html.{HtmlAttribute, RenderProfile}
import ember.editor.ui.{DocumentView, NodeView, ViewSupport}
import ember.editor.richtext.*
import ember.editor.standard.ImageSupport
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.TextArea.textArea
import ui.core.render.{Cursor, DomCursor}
import org.scalajs.dom

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** The component for [[WidgetNode]]: an element with a native control inside it. */
private final class WidgetComponent(node: WidgetNode) extends AbstractComponent:

  val tagName = "span"

  override def compose(cursor: Cursor): Unit =
    host.setAttribute("data-ember-node", node.id.value)
    host.setAttribute("data-widget", "")
    DslLayer.render(this, cursor) {
      val area = textArea(node.label) {}
      area.setAttribute("data-widget-input", "")
      // Out of the tab order. §22 wants atomic media reachable by keyboard, but that is the
      // media view's own affordance (P26) -- a bare control inside an atom would simply be what
      // Tab hits first, and the tests about the editing surface's own Tab behaviour would then
      // be testing this textarea instead.
      area.setAttribute("tabindex", "-1")
    }
