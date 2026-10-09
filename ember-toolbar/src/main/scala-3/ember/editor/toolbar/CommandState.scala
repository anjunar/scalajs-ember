package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.richtext.*

final case class CommandState(enabled: Boolean, pressed: Option[String] = None)
