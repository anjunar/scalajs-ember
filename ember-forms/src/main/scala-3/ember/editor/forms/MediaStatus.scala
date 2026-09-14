package ember.editor.forms

import ember.editor.core.*

enum MediaAvailability:
  case Ready, SourceBusy, CompositionBusy, ReadOnly

enum MediaPhase:
  case Uploading
  case Waiting(reason: MediaAvailability)
  case Inserted(node: NodeId)
  case Failed(reason: String)
  case Cancelled
  case Discarded(reason: String)

final case class MediaStatus(id: Long, phase: MediaPhase, progress: Double, preview: Option[String])

/** Captured by its coordinator; session identity and generation never cross a wire. */
final class MediaTarget private[forms] (
    private[forms] val owner: AnyRef,
    private[forms] val selection: RangeSelection,
    private[forms] val revision: Revision,
    private[forms] val generation: Long,
    private[forms] val original: Option[Set[EditorNode]]
)
