package ember.editor.image

import ember.editor.core.*

/** A media source that has passed a [[MediaUrlPolicy]].
  *
  * Same construction as `LinkUrl` in `ember-link`, and for the same reason: there is no way to
  * build a [[MediaReference]] without one, and no way to build one except through a policy. A
  * document therefore cannot hold a source that nothing checked.
  */
opaque type MediaUrl = String

object MediaUrl:

  private[image] def trusted(value: String): MediaUrl = value

  extension (url: MediaUrl) def value: String = url

  given Ordering[MediaUrl] = Ordering.String
