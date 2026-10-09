package ember.editor.codehighlighting

import ember.editor.core.*

import scala.collection.mutable

/** The tokens for one request.
  *
  * Carries its revision and its text back, and that is the point: §5 counts the result of an
  * external effect as valid only for the revision it was computed for. A worker that answers late
  * answers a question nobody is asking any more, and [[HighlightScheduler]] can only tell because
  * the answer says which question it was.
  */
final case class HighlightResult(
    block: NodeId,
    revision: Revision,
    text: String,
    tokens: Vector[Token],
    skipped: Option[HighlightSkip]
)
