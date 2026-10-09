package ember.editor.forms

import ember.editor.core.*
import ember.editor.image.*
import scala.concurrent.Future
import scala.util.control.NonFatal

/** Storage, transport and backend validation belong to the application. */
trait MediaService[F]:
  def upload(file: F, cancellation: MediaCancellation): Future[MediaReference]
