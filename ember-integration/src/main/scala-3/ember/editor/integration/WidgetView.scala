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
private object WidgetView extends NodeView[WidgetNode]:

  val nodeType: NodeType[WidgetNode] = WidgetNode

  def create(node: WidgetNode, profile: RenderProfile): AbstractComponent =
    new WidgetComponent(node)

  def accepts(component: AbstractComponent, node: WidgetNode, profile: RenderProfile): Boolean =
    component.isInstanceOf[WidgetComponent]

  def update(component: AbstractComponent, node: WidgetNode, profile: RenderProfile): Unit = ()
