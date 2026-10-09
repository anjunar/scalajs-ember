package ember.editor.clipboard

import ember.editor.browser.*
import ember.editor.core.*
import org.scalajs.dom
import scala.scalajs.js
import scala.util.control.NonFatal

/** The synchronous event adapter. A cut is confirmed only when every offered format was accepted by
  * DataTransfer; partial writes never authorize deletion.
  */
final class BrowserClipboardPort(transfer: dom.DataTransfer) extends ClipboardPort:
  def read(): Either[ClipboardError, ClipboardData] =
    try
      Right(ClipboardData(ClipboardMime.priority.flatMap { mime =>
        Option(transfer.getData(mime)).filter(_.nonEmpty).map(mime -> _)
      }.toMap))
    catch
      case NonFatal(error) =>
        Left(ClipboardError(s"Clipboard konnte nicht gelesen werden: ${error.getMessage}"))

  def write(data: ClipboardData): Either[ClipboardError, Unit] =
    try
      data.formats.foreach((mime, value) => transfer.setData(mime, value))
      if data.formats.forall((mime, value) => transfer.getData(mime) == value) then Right(())
      else Left(ClipboardError("Der Browser hat nicht alle Clipboard-Formate übernommen."))
    catch
      case NonFatal(error) =>
        Left(ClipboardError(s"Clipboard konnte nicht geschrieben werden: ${error.getMessage}"))
