package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.browser.*
import org.scalajs.dom
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.{Cursor, DomNodes}
import ui.core.layout.TextComponent
import ui.core.state.{Disposable, Property}
import scala.scalajs.js

/** A labelled ribbon group; actions still share the toolbar's single roving Tab stop. */
final case class ToolbarGroup(label: String, actions: Vector[ToolbarAction])
