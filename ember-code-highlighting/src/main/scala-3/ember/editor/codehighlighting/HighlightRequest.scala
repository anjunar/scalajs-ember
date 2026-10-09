package ember.editor.codehighlighting

import ember.editor.core.*

import scala.collection.mutable

/** One block's text at one revision, and the language it claims to be. */
final case class HighlightRequest(
    block: NodeId,
    revision: Revision,
    language: Option[String],
    text: String
)
