package ember.editor.profiles

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.list.*
import ember.editor.link.*
import ember.editor.image.*
import ember.editor.code.*
import ember.editor.history.*
import ember.editor.markdown.*
import ember.editor.standard.*
import ember.editor.browsersupport.EditorBindings
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("profile")
object StandardProfileApp
    extends ProfileApi(
      "standard",
      StandardSetup.resolved,
      ImageSupport.views,
      StandardSetup.decode,
      StandardSetup.encode,
      EditorBindings.everything,
      EditorBindings.everythingKeyboard
    )
