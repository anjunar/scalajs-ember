package ember.editor.clipboard

import ember.editor.core.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait ClipboardPort:
  def read(): Either[ClipboardError, ClipboardData]
  def write(data: ClipboardData): Either[ClipboardError, Unit]
