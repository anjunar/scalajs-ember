package ember.editor.clipboard

import ember.editor.core.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait AsyncClipboardPort:
  def write(data: ClipboardData): Future[Either[ClipboardError, Unit]]
