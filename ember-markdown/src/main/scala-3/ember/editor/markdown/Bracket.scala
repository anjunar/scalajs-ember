package ember.editor.markdown

import scala.collection.mutable

/** A `[` or `![` waiting for its `]`. */
private[markdown] final class Bracket(
    val node: PendingText,
    val previous: Bracket | Null,
    val previousDelimiter: Delimiter | Null,
    val index: Int,
    val isImage: Boolean,
    var active: Boolean,
    var bracketAfter: Boolean
)
