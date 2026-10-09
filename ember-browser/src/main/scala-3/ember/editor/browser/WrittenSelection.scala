package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** What the port last wrote, and for which revision. */
private final case class WrittenSelection(
    anchorNode: dom.Node,
    anchorOffset: Int,
    focusNode: dom.Node,
    focusOffset: Int,
    revision: Revision
)
