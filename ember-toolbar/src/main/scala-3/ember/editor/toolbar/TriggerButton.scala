package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.browser.*
import org.scalajs.dom
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.{Cursor, DomNodes}
import ui.core.layout.{Button, TextComponent}
import ui.core.state.{Disposable, Property}
import scala.scalajs.js
private final class TriggerButton(val group: ToolbarGroup) extends Button:
  def refresh(): Unit = disabled = !group.actions.exists(_.state().enabled)
