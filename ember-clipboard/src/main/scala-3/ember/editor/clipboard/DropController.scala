package ember.editor.clipboard

import ember.editor.browser.*
import ember.editor.core.*
import org.scalajs.dom
import scala.scalajs.js
import scala.util.control.NonFatal

/** Local drag identity stays outside the serialized fragment. Other editors and external drags
  * always copy through the validated codec, never delete a source.
  */
final class DropController(
    session: EditorSession,
    selection: SelectionPort,
    input: BrowserInputController,
    codec: ClipboardCodec,
    receive: (dom.DataTransfer, Option[Point], String) => Either[ClipboardError, Vector[String]],
    report: Either[ClipboardError, Vector[String]] => Unit,
    claim: String => Unit
):
  private case class Drag(token: String, selected: Selection, revision: Revision)
  private var active                               = Option.empty[Drag]
  private val tokenMime                            = "application/x-ember-drag-token"
  private val host                                 = selection.scope.host
  private val start: js.Function1[dom.Event, Unit] = event => onStart(event)
  private val over: js.Function1[dom.Event, Unit]  = event =>
    if input.ownsEvent(
        event
      ) && input.state == ControllerState.Ready && input.mode == EditorMode.Editable
    then event.preventDefault()
  private val drop: js.Function1[dom.Event, Unit] = event => onDrop(event)
  private val end: js.Function1[dom.Event, Unit]  = _ => active = None
  host.addEventListener("dragstart", start)
  host.addEventListener("dragover", over)
  host.addEventListener("drop", drop)
  host.addEventListener("dragend", end)

  def dispose(): Unit =
    active = None
    host.removeEventListener("dragstart", start)
    host.removeEventListener("dragover", over)
    host.removeEventListener("drop", drop)
    host.removeEventListener("dragend", end)

  private def onStart(event: dom.Event): Unit =
    active = None
    if !event.defaultPrevented && input.ownsEvent(event) && input.state == ControllerState.Ready
    then
      val data = event.asInstanceOf[dom.DragEvent].dataTransfer
      Option(data).foreach { transfer =>
        selection.importNative(): Unit
        new ClipboardService(session, codec).copy() match
          case Left(_)        => ()
          case Right(encoded) =>
            new BrowserClipboardPort(transfer).write(encoded) match
              case Left(error) => event.preventDefault(); report(Left(error))
              case Right(_)    =>
                try
                  val bytes = new js.typedarray.Uint32Array(4)
                  selection.scope.window.get.asInstanceOf[js.Dynamic].crypto.getRandomValues(bytes)
                  val token =
                    (0 until bytes.length).map(i => bytes(i).toLong.toHexString).mkString("-")
                  transfer.setData(tokenMime, token)
                  transfer.effectAllowed = dom.DataTransferEffectAllowedKind.copyMove
                  active = Some(Drag(token, session.selection.get, session.state.documentRevision))
                catch
                  case NonFatal(error) =>
                    event.preventDefault()
                    report(
                      Left(
                        ClipboardError(s"Drag konnte nicht vorbereitet werden: ${error.getMessage}")
                      )
                    )
      }

  private def onDrop(event: dom.Event): Unit =
    if !event.defaultPrevented && input.ownsEvent(event) && event.cancelable then
      event.preventDefault()
      event.stopImmediatePropagation()
      val drag   = event.asInstanceOf[dom.DragEvent]
      val source = active
      active = None
      val result = if input.state != ControllerState.Ready || input.mode != EditorMode.Editable then
        Left(ClipboardError("Drop ist im aktuellen Editorzustand nicht möglich."))
      else
        for
          transfer <- Option(drag.dataTransfer).toRight(ClipboardError("Drop-Daten fehlen."))
          point    <- pointAt(drag.clientX, drag.clientY).toRight(
            ClipboardError("Drop-Ziel konnte nicht ermittelt werden.")
          )
          result <- source.filter(s => transfer.getData(tokenMime) == s.token) match
            case Some(local) if !drag.ctrlKey && !drag.altKey =>
              if local.revision != session.state.documentRevision then
                Left(ClipboardError("Das Drag-Quelldokument wurde geändert."))
              else
                session
                  .dispatch(ClipboardCommands.meta("drag-move"))(
                    ClipboardCommands.Move,
                    FragmentMove(local.selected, point)
                  )
                  .left
                  .map(error => ClipboardError(error.render))
                  .map { _ => selection.sync(): Unit; Vector.empty[String] }
            case _ => receive(transfer, Some(point), "drop")
        yield result
      claim("insertFromDrop")
      report(result)

  private def pointAt(x: Double, y: Double): Option[Point] =
    val document = selection.scope.ownerDocument.asInstanceOf[js.Dynamic]
    val native   =
      if js.typeOf(document.selectDynamic("caretPositionFromPoint")) == "function" then
        val at = document.caretPositionFromPoint(x, y)
        if at == null then None
        else Some(DomPosition(at.offsetNode.asInstanceOf[dom.Node], at.offset.asInstanceOf[Int]))
      else if js.typeOf(document.selectDynamic("caretRangeFromPoint")) == "function" then
        val at = document.caretRangeFromPoint(x, y)
        if at == null then None
        else
          Some(
            DomPosition(at.startContainer.asInstanceOf[dom.Node], at.startOffset.asInstanceOf[Int])
          )
      else None
    native
      .filter(at => selection.positions.atomAround(at.node, session.document).isEmpty)
      .flatMap(selection.positions.toPoint(_, session.document).toOption)
