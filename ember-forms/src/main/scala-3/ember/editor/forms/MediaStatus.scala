package ember.editor.forms

import ember.editor.core.*

final case class MediaStatus(id: Long, phase: MediaPhase, progress: Double, preview: Option[String])
