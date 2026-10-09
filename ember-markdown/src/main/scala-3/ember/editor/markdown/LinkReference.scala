package ember.editor.markdown

import scala.collection.mutable

/** A resolved link reference definition. */
private[markdown] final case class LinkReference(destination: String, title: Option[String])
