package ember.editor.link

import ember.editor.core.*

/** Where a link points, and what it is called.
  *
  * Together rather than as two parameters because they belong to the same decision: a dialog that
  * asks for an address usually asks for a title in the same breath, and `SetLink` takes one
  * payload.
  */
final case class LinkTarget(url: LinkUrl, title: Option[String] = None)
