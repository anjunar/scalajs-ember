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

/** An atom whose inside is not editor text.
  *
  * §15.2 names the case: "native Inputs/Textareas in Atom-Views, unmanaged Bereiche und
  * verschachtelte Editoren gehoeren nicht automatisch zum aeusseren Editor." A picture is the usual
  * atom and makes a poor test of it -- `<img>` is void, so nothing can be selected inside. This one
  * holds a real textarea, which is exactly the shape the rule is about.
  */
final case class WidgetNode(id: NodeId, label: String) extends AtomNode

object WidgetNode extends NodeType[WidgetNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.it.widget/1")

  def project(node: EditorNode): Option[WidgetNode] = node match
    case widget: WidgetNode => Some(widget)
    case _                  => None

  def rekey(node: WidgetNode, id: NodeId): WidgetNode = node.copy(id = id)
