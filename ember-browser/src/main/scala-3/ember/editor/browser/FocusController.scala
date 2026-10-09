package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** Focus as it relates to selection: who has it, what to remember, and when to give it back.
  *
  * ==Why this is not part of the port==
  *
  * Because "put the selection back without taking the focus" has to be sayable. A toolbar that
  * formats the selected text needs exactly that: the selection survives, the focus goes back to the
  * text, and neither implies the other. Folding focus into [[SelectionPort.write]] would make every
  * write a focus decision, and the port would then have to guess which one.
  *
  * ==What it observes==
  *
  * `focusin` and `focusout`, not `focus`/`blur`: the first pair bubbles, and an editor host
  * contains things that can take focus on their own -- a media control inside an atom, a nested
  * input. Focus moving to one of those is still focus inside the editor, and `blur` on the host
  * would claim otherwise.
  */
final class FocusController private (
    session: EditorSession,
    view: DocumentView,
    port: SelectionPort
):

  private var focusIn: js.Function1[dom.Event, Unit]  = null
  private var focusOut: js.Function1[dom.Event, Unit] = null
  private var listeners                               = Vector.empty[(Long, Boolean => Unit)]
  private var nextHandle                              = 0L
  private var disposedFlag                            = false

  def scope: BrowserScope = port.scope

  def isDisposed: Boolean = disposedFlag

  /** Whether the focus is inside the editing host right now. */
  def focusWithin: Boolean = scope.focusWithin

  /** Remembers the current selection and whether the editor holds the focus.
    *
    * §22: "Oeffnen eines Dialogs speichert ein gemapptes Selection-Bookmark." Taken from the
    * '''session''', not from the DOM: the model selection is the one that can be mapped through
    * later edits, and by the time a dialog closes the document may have moved on.
    */
  def capture(): Option[SelectionBookmark] =
    session.selection match
      case Some(range: RangeSelection) =>
        Some(SelectionBookmark.of(range, session.state.revision, focusWithin))
      case _ => None

  /** Puts a remembered selection back, and the focus with it if the rule allows.
    *
    * The order matters: focus first, then the selection. A host that takes focus with no selection
    * in it gets one from the browser -- usually the start of its content -- and writing afterwards
    * replaces that. Writing first and focusing after would show the browser's guess for one frame.
    */
  def restore(bookmark: SelectionBookmark, intent: FocusIntent): RestoreOutcome =
    session.mappingSince(bookmark.revision).flatMap(bookmark.resolve) match
      case Left(expired)    => RestoreOutcome.Expired(expired)
      case Right(selection) =>
        val takesFocus =
          FocusPolicy.mayTakeFocus(intent, bookmark.hadFocus, focusWithin, scope.focusable)

        if takesFocus then scope.focus()

        port.write(Some(selection), FocusPolicy.writeIntent(takesFocus)) match
          case SelectionWrite.Written => RestoreOutcome.Restored(selection, takesFocus)
          // Nothing to write is not a failure to restore. The caret is already where the
          // bookmark points -- usually because the core mapped the live selection through the
          // same edits -- and the post-condition a caller asked for holds either way.
          case SelectionWrite.Skipped(SkipReason.AlreadyThere) =>
            RestoreOutcome.Restored(selection, takesFocus)
          case other => RestoreOutcome.NotWritten(other)

  /** Starts observing focus movement in and out of the host. */
  def attach(): Unit =
    if !disposedFlag && focusIn == null then
      val entering: js.Function1[dom.Event, Unit] = _ => announce(true)
      val leaving: js.Function1[dom.Event, Unit]  = _ =>
        // `focusout` fires before the new element is active, so asking now would always say
        // "gone". One turn later the document knows where the focus actually went -- and it may
        // well be another element inside the same host.
        js.timers.setTimeout(0.0)(if !disposedFlag then announce(focusWithin)): Unit

      focusIn = entering
      focusOut = leaving
      scope.host.addEventListener("focusin", entering)
      scope.host.addEventListener("focusout", leaving)

  /** Notified when the focus enters or leaves the host. */
  def onFocusChange(listener: Boolean => Unit): Subscription =
    if disposedFlag then Subscription.cancelled
    else
      nextHandle += 1
      val handle = nextHandle
      listeners = listeners :+ (handle, listener)
      Subscription(() => listeners = listeners.filterNot(_._1 == handle))

  private def announce(inside: Boolean): Unit =
    listeners.foreach { (_, listener) =>
      try listener(inside)
      catch case _: Throwable => ()
    }

  def dispose(): Unit =
    if !disposedFlag then
      disposedFlag = true
      if focusIn != null then scope.host.removeEventListener("focusin", focusIn)
      if focusOut != null then scope.host.removeEventListener("focusout", focusOut)
      focusIn = null
      focusOut = null
      listeners = Vector.empty

object FocusController:

  def attachTo(session: EditorSession, view: DocumentView, port: SelectionPort): FocusController =
    val controller = new FocusController(session, view, port)
    controller.attach()
    controller

  /** Without the native listeners. For a caller that drives it, and for tests. */
  def detached(session: EditorSession, view: DocumentView, port: SelectionPort): FocusController =
    new FocusController(session, view, port)
