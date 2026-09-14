package ember.editor.forms

import ember.editor.browser.*
import ember.editor.clipboard.*
import ember.editor.core.*
import org.scalajs.dom
import scala.scalajs.js
import scala.util.control.NonFatal

final case class MediaFilePolicy(
    maxBytes: Double = 10 * 1024 * 1024,
    maxFiles: Int = 8,
    acceptedTypes: Set[String] = Set("image/png", "image/jpeg", "image/gif", "image/webp")
):
  require(maxBytes > 0 && maxFiles > 0)
  def validate(files: Vector[dom.File]): Either[MediaFailure, Unit] =
    if files.isEmpty || files.size > maxFiles then Left(MediaFailure("Ungültige Anzahl Dateien."))
    else if files.exists(f => f.size <= 0 || f.size > maxBytes || !acceptedTypes.contains(f.`type`))
    then Left(MediaFailure("Dateityp oder Dateigröße nicht erlaubt."))
    else Right(())

object BrowserMediaPreviews:
  def in(window: dom.Window): MediaPreviews[dom.File] = new MediaPreviews[dom.File]:
    private def urls                           = window.asInstanceOf[js.Dynamic].URL
    def create(file: dom.File): Option[String] = Some(
      urls.createObjectURL(file).asInstanceOf[String]
    )
    def revoke(url: String): Unit = urls.revokeObjectURL(url): Unit

/** An application-owned file input and clipboard callback share exactly one coordinator. */
final class BrowserMediaPicker(
    input: dom.HTMLInputElement,
    session: EditorSession,
    selection: SelectionPort,
    coordinator: MediaCoordinator[dom.File],
    alt: () => String,
    policy: MediaFilePolicy = MediaFilePolicy(),
    report: Either[MediaFailure, Vector[Long]] => Unit = _ => ()
):
  private var live                                    = true
  private var remembered                              = Option.empty[MediaTarget]
  private val listener: js.Function1[dom.Event, Unit] = _ =>
    if live then
      val picked = Option(input.files).toVector.flatMap(list => (0 until list.length).map(list(_)))
      if picked.nonEmpty then
        report(remembered.toRight(MediaFailure("Picker-Ziel fehlt.")).flatMap(start(picked, _)))
      remembered = None
      input.value = ""
  input.addEventListener("change", listener)

  /** Capture before focus moves into the picker; selecting the same file again is allowed. */
  def open(): Either[MediaFailure, Unit] =
    if !live then Left(MediaFailure("Dateiauswahl ist beendet."))
    else
      selection.importNative(): Unit
      session.selection
        .collect { case range: RangeSelection => range }
        .toRight(MediaFailure("Keine Texteinfugestelle ausgewählt."))
        .flatMap(coordinator.capture)
        .flatMap { target =>
          remembered = Some(target)
          try
            input.click()
            Right(())
          catch
            case NonFatal(error) =>
              remembered = None
              Left(MediaFailure(error.getMessage))
        }

  def receive(intent: ClipboardFileIntent): Either[ClipboardError, Unit] =
    if !live then Left(ClipboardError("Dateiauswahl ist beendet."))
    else
      val target = if intent.source != "drop" then
        session.selection
          .collect { case range: RangeSelection => range }
          .map(coordinator.capture)
          .getOrElse(coordinator.capture(intent.destination))
      else coordinator.capture(intent.destination)
      val result = target.flatMap(start(intent.files, _))
      report(result)
      result.left.map(error => ClipboardError(error.message)).map(_ => ())

  private def start(
      files: Vector[dom.File],
      target: MediaTarget
  ): Either[MediaFailure, Vector[Long]] =
    policy.validate(files).flatMap { _ =>
      // Reject the batch before starting if capacity would truncate it.
      if files.size > coordinator.availableSlots then
        Left(MediaFailure("Zu viele parallele Uploads."))
      else
        files.zipWithIndex.foldLeft[Either[MediaFailure, Vector[Long]]](Right(Vector.empty)) {
          case (result, (file, index)) =>
            // The first file replaces the range; later files insert at its trailing edge.
            val destination = if index == 0 then target
            else
              new MediaTarget(
                target.owner,
                RangeSelection.caret(target.selection.focus),
                target.revision,
                target.generation,
                None
              )
            result.flatMap(ids => coordinator.upload(file, destination, alt()).map(ids :+ _))
        }
    }
  def dispose(): Unit =
    live = false
    remembered = None
    input.removeEventListener("change", listener)
