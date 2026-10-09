package ember.editor.codehighlighting

import ember.editor.core.*

import scala.collection.mutable

/** Text in, token ranges out (X02: "Reiner Text→Tokenbereich-Service").
  *
  * Asynchronous by contract, even though the local implementation answers at once: a worker adapter
  * answers later or never, and a caller that relied on an immediate reply would break the day one
  * is plugged in. The subscription cancels a pending answer; one already given is not taken back.
  */
trait Highlighter:

  def highlight(request: HighlightRequest, reply: HighlightResult => Unit): Subscription

  /** The block is gone or no longer code; whatever is cached for it can go too. */
  def forget(block: NodeId): Unit
