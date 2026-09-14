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

/** Files are effects for the application/media adapter, never document payload. */
final case class ClipboardFileIntent(files: Vector[dom.File], destination: Bookmark, source: String)

final class BrowserClipboardController(
    session: EditorSession,
    selection: SelectionPort,
    input: BrowserInputController,
    codec: ClipboardCodec,
    report: Either[ClipboardError, Vector[String]] => Unit = _ => (),
    files: ClipboardFileIntent => Either[ClipboardError, Unit] = _ =>
      Left(ClipboardError("Kein Medienservice für Dateien eingerichtet."))
):
  private val service                                       = new ClipboardService(session, codec)
  private val host                                          = selection.scope.host
  private val claims                                        = new InputOperationLog()
  private var live                                          = true
  private var epoch                                         = 0L
  private val copyListener: js.Function1[dom.Event, Unit]   = event => copyOrCut(event, false)
  private val cutListener: js.Function1[dom.Event, Unit]    = event => copyOrCut(event, true)
  private val pasteListener: js.Function1[dom.Event, Unit]  = event => pasteEvent(event)
  private val beforeListener: js.Function1[dom.Event, Unit] = event => beforeInput(event)
  host.addEventListener("copy", copyListener)
  host.addEventListener("cut", cutListener)
  host.addEventListener("paste", pasteListener)
  host.addEventListener("beforeinput", beforeListener, true)
  private val drops = new DropController(session, selection, input, codec, receive, report, claim)

  def dispose(): Unit =
    if live then
      live = false
      epoch += 1
      claims.clear()
      drops.dispose()
      host.removeEventListener("copy", copyListener)
      host.removeEventListener("cut", cutListener)
      host.removeEventListener("paste", pasteListener)
      host.removeEventListener("beforeinput", beforeListener, true)

  private def owned(event: dom.Event): Boolean =
    live && !event.defaultPrevented && input.ownsEvent(event)
  private def editable: Boolean =
    input.mode == EditorMode.Editable && input.state == ControllerState.Ready
  private def prevent(event: dom.Event): Unit =
    event.preventDefault()
    event.stopImmediatePropagation()

  private def claim(inputType: String): Unit =
    claims.clear()
    claims.record(inputType): Unit
    epoch += 1
    val current = epoch
    // The claim belongs to this event dispatch turn, never to a later user paste.
    js.Promise
      .resolve[Unit](())
      .`then`[Unit]((_: Unit) => {
        if current == epoch then claims.clear()
        ()
      }): Unit

  private def copyOrCut(event: dom.Event, cut: Boolean): Unit =
    if owned(event) && event.cancelable then
      if cut && !editable then
        prevent(event)
        report(Left(ClipboardError("Ausschneiden ist im aktuellen Editorzustand nicht möglich.")))
      else
        selection.importNative(): Unit
        Option(event.asInstanceOf[dom.ClipboardEvent].clipboardData).foreach { transfer =>
          val port   = new BrowserClipboardPort(transfer)
          val result = if cut then service.cut(port) else service.copy().flatMap(port.write)
          // An empty caret copy leaves the browser's ordinary behavior available.
          if result.isRight || cut then prevent(event)
          if result.isRight && cut then
            claim("deleteByCut")
            selection.sync(): Unit
          report(result.map(_ => Vector.empty))
        }

  private def pasteEvent(event: dom.Event): Unit =
    if owned(event) && event.cancelable then
      prevent(event)
      selection.importNative(): Unit
      val result = if !editable then
        Left(ClipboardError("Einfügen ist im aktuellen Editorzustand nicht möglich."))
      else
        Option(event.asInstanceOf[dom.ClipboardEvent].clipboardData)
          .toRight(ClipboardError("Clipboard-Daten fehlen."))
          .flatMap(transfer => receive(transfer, None, "paste"))
      // Refusals also own this operation; a second native route cannot bypass them.
      claim("insertFromPaste")
      report(result)

  private def beforeInput(event: dom.Event): Unit =
    if owned(event) then
      val native = event.asInstanceOf[dom.InputEvent]
      val kind   = native.inputType.toString
      if kind == "insertFromPaste" || kind == "insertFromDrop" || kind == "deleteByCut" || kind == "deleteByDrag"
      then
        if event.cancelable then
          prevent(event)
          if claims.consume(kind) then ()
          else if !editable then
            report(Left(ClipboardError("Clipboard-Bearbeitung ist derzeit gesperrt.")))
          else if kind == "deleteByCut" then
            report(Left(ClipboardError("Ausschneiden ohne bestätigtes Schreiben abgewiesen.")))
          // Internal moves already removed their source; cross-editor drags copy.
          else if kind == "deleteByDrag" then ()
          else
            selection.importNative(): Unit
            report(
              Option(native.dataTransfer)
                .toRight(ClipboardError("Clipboard-Daten fehlen."))
                .flatMap(transfer => receive(transfer, None, "beforeinput"))
            )
        // Noncancelable input remains the native-input controller's responsibility.

  private def receive(
      transfer: dom.DataTransfer,
      destination: Option[Point],
      source: String
  ): Either[ClipboardError, Vector[String]] =
    if !editable then Left(ClipboardError("Clipboard-Bearbeitung ist derzeit gesperrt."))
    else
      val picked =
        Option(transfer.files).toVector.flatMap(list => (0 until list.length).map(list(_)))
      val point = destination.orElse(session.selection.collect { case range: RangeSelection =>
        range.focus
      })
      if picked.nonEmpty then
        point.toRight(ClipboardError("Datei-Einfügeziel fehlt.")).flatMap { at =>
          files(ClipboardFileIntent(picked, Bookmark(at, session.state.revision), source))
            .map(_ => Vector.empty)
        }
      else
        new BrowserClipboardPort(transfer).read().flatMap { data =>
          destination match
            case None =>
              service.paste(data).map { decoded =>
                selection.sync(): Unit
                decoded.diagnostics
              }
            case Some(at) =>
              codec.decode(data).flatMap { decoded =>
                session
                  .update(ClipboardCommands.meta("drop")) { tx =>
                    tx.select(RangeSelection.caret(at)): Unit
                    tx.dispatch(ClipboardCommands.Paste, decoded.fragment): Unit
                  }
                  .left
                  .map(error => ClipboardError(error.render))
                  .map { _ =>
                    selection.sync(): Unit
                    decoded.diagnostics
                  }
              }
        }
