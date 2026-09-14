package ember.editor.clipboard

import ember.editor.core.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait ClipboardPort:
  def read(): Either[ClipboardError, ClipboardData]
  def write(data: ClipboardData): Either[ClipboardError, Unit]

trait AsyncClipboardPort:
  def write(data: ClipboardData): Future[Either[ClipboardError, Unit]]

/** A confirmed write is the only route from a prepared cut to deletion. A change in
  * documentRevision (including edit + Undo) invalidates it; selection-only changes can be resolved
  * through the original bookmarks.
  */
final class PendingCut private[clipboard] (
    session: EditorSession,
    val data: ClipboardData,
    selection: Selection,
    revision: Revision,
    documentRevision: Revision
):
  private var completed                                                            = false
  def confirm(written: Either[ClipboardError, Unit]): Either[ClipboardError, Unit] =
    if completed then Left(ClipboardError("Cut wurde bereits abgeschlossen."))
    else
      completed = true
      written.flatMap { _ =>
        if session.isDisposed || session.state.documentRevision != documentRevision then
          Left(
            ClipboardError("Das Dokument wurde während des Schreibens geändert; nichts gelöscht.")
          )
        else
          val checked: Either[ClipboardError, Selection] = selection match
            case range: RangeSelection =>
              (for
                mapping <- session.mappingSince(revision)
                a       <- Bookmark(range.anchor, revision).resolve(mapping)
                b       <- Bookmark(range.focus, revision).resolve(mapping)
              yield RangeSelection(a, b)).left.map(error => ClipboardError(error.render))
            case other => Right(other)
          checked.flatMap { selected =>
            session
              .update(ClipboardCommands.meta("cut")) { tx =>
                tx.select(selected): Unit
                if tx.dispatch(ClipboardCommands.DeleteSelection) != CommandResult.Handled then
                  tx.reject(ClipboardError("ClipboardExtension ist nicht installiert.")): Unit
              }
              .left
              .map(error => ClipboardError(error.render))
              .map(_ => ())
          }
      }

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
