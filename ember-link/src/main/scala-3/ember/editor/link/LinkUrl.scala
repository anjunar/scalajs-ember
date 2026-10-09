package ember.editor.link

import ember.editor.core.*

/** A URL that has passed a [[LinkUrlPolicy]].
  *
  * ==Why this is a type and not a String==
  *
  * P14's acceptance says "URL-Validierung identisch bei Command/Import". A convention would make
  * that a promise; a type makes it a fact. There is no way to build a [[LinkNode]] without one of
  * these, and no way to build one of these except through a policy -- so the command path and the
  * import path cannot drift apart, because there is only one door.
  *
  * The value it carries is the '''normalised''' form, not what the caller typed. See
  * [[LinkUrlPolicy.normalise]] for what that means and why it has to happen before any check.
  */
opaque type LinkUrl = String

object LinkUrl:

  private[link] def trusted(value: String): LinkUrl = value

  extension (url: LinkUrl) def value: String = url

  given Ordering[LinkUrl] = Ordering.String
