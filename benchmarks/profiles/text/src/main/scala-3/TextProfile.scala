package ember.editor.profiles

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.standard.ParagraphSupport
import ember.editor.browsersupport.RichTextBindings
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("profile")
object TextProfile
    extends ProfileApi(
      "text",
      TextSetup.resolved,
      ParagraphSupport.views,
      TextSetup.decode,
      document => document.inDocumentOrder.collect { case t: TextNode => t.text }.mkString,
      RichTextBindings.input,
      RichTextBindings.keyboard
    )
