package ember.editor.browser

import ember.editor.core.*
import ember.editor.html.HtmlSupport
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** Whether this editor accepts changes at all.
  *
  * §22 keeps two things apart that look like one: "Readonly-Policy verhindert auch
  * programmgesteuerte User-Editing-Commands; Fokusfaehigkeit und Editierbarkeit sind getrennte
  * Entscheidungen." A readonly editor is still focusable, still selectable, still readable by a
  * screen reader -- it just refuses to change.
  */
enum EditorMode:
  case Editable, ReadOnly
