package ember.editor.clipboard

import ember.editor.core.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

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
