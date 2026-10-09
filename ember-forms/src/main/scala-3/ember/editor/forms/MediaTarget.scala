package ember.editor.forms

import ember.editor.core.*

/** Captured by its coordinator; session identity and generation never cross a wire. */
final class MediaTarget private[forms] (
    private[forms] val owner: AnyRef,
    private[forms] val selection: RangeSelection,
    private[forms] val revision: Revision,
    private[forms] val generation: Long,
    private[forms] val original: Option[Set[EditorNode]]
)
