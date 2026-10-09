package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.{ContainerElement, DocumentView, TextRunElement}
import ui.core.render.DomNodes
import org.scalajs.dom

/** A DOM position: a container node and an offset in it -- the shape a `Range` endpoint has. */
final case class DomPosition(node: dom.Node, offset: Int)
