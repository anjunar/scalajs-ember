package ember.editor.core

import scala.collection.mutable

/** Ein Listener hat beim Verarbeiten eines Commits geworfen. */
final case class ListenerFailed(listener: String, cause: String) extends EditorError:
  def message: String = s"Listener `$listener` ist gescheitert: $cause"
