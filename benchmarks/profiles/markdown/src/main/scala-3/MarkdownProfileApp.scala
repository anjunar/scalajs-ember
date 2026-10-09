package ember.editor.profiles

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.markdown.*
import ember.editor.standard.*
import ember.editor.browsersupport.RichTextBindings
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("profile")
object MarkdownProfileApp
    extends ProfileApi(
      "markdown",
      MarkdownSetup.resolved,
      RichTextSupport.views,
      MarkdownSetup.decode,
      MarkdownSetup.encode,
      RichTextBindings.input,
      RichTextBindings.keyboard
    )
