package ember.editor.clipboard

import ember.editor.core.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

final class ClipboardService(session: EditorSession, codec: ClipboardCodec):
  def copy(): Either[ClipboardError, ClipboardData] =
    if session.isDisposed then Left(ClipboardError("Die Sitzung ist entsorgt."))
    else
      session.selection
        .toRight(ClipboardError("Keine Auswahl."))
        .flatMap(DocumentFragment.extract(session.document, _))
        .flatMap { fragment =>
          if fragment.isEmpty then Left(ClipboardError("Die Auswahl ist leer."))
          else codec.encode(fragment)
        }

  def prepareCut(): Either[ClipboardError, PendingCut] = copy().map { data =>
    new PendingCut(
      session,
      data,
      session.selection.get,
      session.state.revision,
      session.state.documentRevision
    )
  }

  def cut(port: ClipboardPort): Either[ClipboardError, Unit] =
    prepareCut().flatMap(pending => pending.confirm(port.write(pending.data)))

  def cutAsync(port: AsyncClipboardPort)(using
      ec: ExecutionContext
  ): Future[Either[ClipboardError, Unit]] =
    prepareCut() match
      case Left(error)    => Future.successful(Left(error))
      case Right(pending) =>
        val written = try port.write(pending.data)
        catch case NonFatal(error) => Future.successful(Left(ClipboardError(error.getMessage)))
        written
          .recover { case NonFatal(error) => Left(ClipboardError(error.getMessage)) }
          .map(pending.confirm)

  def paste(data: ClipboardData): Either[ClipboardError, ClipboardDecoded] =
    if session.isDisposed then Left(ClipboardError("Die Sitzung ist entsorgt."))
    else
      codec.decode(data).flatMap { decoded =>
        session
          .dispatch(ClipboardCommands.meta("paste"))(ClipboardCommands.Paste, decoded.fragment)
          .left
          .map(error => ClipboardError(error.render))
          .flatMap { dispatched =>
            if dispatched.result == CommandResult.Handled then Right(decoded)
            else Left(ClipboardError("ClipboardExtension ist nicht installiert."))
          }
      }
