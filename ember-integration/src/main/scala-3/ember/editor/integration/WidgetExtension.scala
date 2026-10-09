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
private object WidgetExtension extends Extension:

  val id: ExtensionId = ExtensionId("ember.it.widget")

  override def contribute: ExtensionContributions =
    ExtensionContributions(nodeTypes = Vector(WidgetNode))
