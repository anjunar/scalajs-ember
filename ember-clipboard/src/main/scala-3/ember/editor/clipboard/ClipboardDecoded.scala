package ember.editor.clipboard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.json.*
import ember.editor.richtext.*

final case class ClipboardDecoded(
    fragment: DocumentFragment,
    mime: String,
    diagnostics: Vector[String]
)
