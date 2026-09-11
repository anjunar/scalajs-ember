package ember.editor.browser

import ember.editor.core.*
import ember.editor.ui.DocumentView
import org.scalajs.dom

import scala.scalajs.js

/** Why focus is being restored. The caller knows; this type makes them say so.
  *
  * §22: "Schliessen stellt Fokus nur im passenden Interaktionskontext wieder her. Hintergrund-
  * updates stehlen weder Page- noch Textarea-Fokus." There is no way to work out from a bookmark
  * alone which of those a call is, so the intent is a parameter and not a guess.
  */
enum FocusIntent:

  /** Put the selection back, never the focus. What a background update is allowed to do. */
  case SelectionOnly

  /** Focus only if the editor had it when the bookmark was taken. The dialog case. */
  case IfItWasOurs

  /** An explicit user gesture asking for the editor -- a "back to the text" button. */
  case Always

/** What a restore did. */
enum RestoreOutcome:

  case Restored(selection: RangeSelection, focused: Boolean)

  /** The bookmark can no longer be mapped to this document (§11). */
  case Expired(error: ExpiredBookmark)

  /** The selection could not be written. The bookmark itself was fine. */
  case NotWritten(write: SelectionWrite)

/** A remembered selection with the state it belonged to, plus whether the editor had focus.
  *
  * Two bookmarks and not one: §11 maps anchor and focus '''independently''', and a pair that had
  * been collapsed into a single position would come back as a caret even where the user had a
  * range. The focus flag is what makes [[FocusIntent.IfItWasOurs]] answerable at all.
  */
final case class SelectionBookmark(anchor: Bookmark, focus: Bookmark, hadFocus: Boolean):

  def revision: Revision = anchor.revision

  /** Brings the bookmark forward to the document the mapping ends at.
    *
    * Uses [[Bookmark.resolveOrFallback]] -- the variant that accepts the replacement boundary.
    * Its own documentation names this case: "Fuer Aufrufer, denen eine ungefaehre Stelle genuegt
    * -- etwa das Wiederherstellen des Fokus nach einem Dialog (§22)." An insertion may not do
    * that; putting a caret back may.
    */
  def resolve(mapping: RevisionMapping): Either[ExpiredBookmark, RangeSelection] =
    for
      anchorPoint <- anchor.resolveOrFallback(mapping)
      focusPoint  <- focus.resolveOrFallback(mapping)
    yield RangeSelection(anchorPoint, focusPoint)

object SelectionBookmark:

  /** Takes a bookmark of a range at a revision. */
  def of(selection: RangeSelection, revision: Revision, hadFocus: Boolean): SelectionBookmark =
    SelectionBookmark(
      Bookmark(selection.anchor, revision),
      Bookmark(selection.focus, revision),
      hadFocus
    )

/** When focus may be moved, as a rule rather than as a sequence of calls.
  *
  * The interesting half is negative, and it is the one §22 spends its sentence on: a background
  * update must not take the focus, and neither must a dialog that was never given it. Getting
  * this wrong is not a visual glitch -- it moves the keyboard away from wherever someone was
  * actually typing.
  */
object FocusPolicy:

  def mayTakeFocus(
      intent: FocusIntent,
      bookmarkHadFocus: Boolean,
      focusWithin: Boolean,
      focusable: Boolean
  ): Boolean =
    if focusWithin then false          // already here; moving it again would only scroll
    else if !focusable then false      // §22: focusability is a separate decision from editability
    else
      intent match
        case FocusIntent.SelectionOnly => false
        case FocusIntent.IfItWasOurs   => bookmarkHadFocus
        case FocusIntent.Always        => true

  /** Which write intent a restore implies.
    *
    * A restore that is allowed to take the focus writes the selection whether or not the focus
    * has arrived yet; one that is not may only write into a host that is already focused.
    */
  def writeIntent(takesFocus: Boolean): WriteIntent =
    if takesFocus then WriteIntent.Explicit else WriteIntent.FollowFocus

/** Focus as it relates to selection: who has it, what to remember, and when to give it back.
  *
  * ==Why this is not part of the port==
  *
  * Because "put the selection back without taking the focus" has to be sayable. A toolbar that
  * formats the selected text needs exactly that: the selection survives, the focus goes back to
  * the text, and neither implies the other. Folding focus into [[SelectionPort.write]] would make
  * every write a focus decision, and the port would then have to guess which one.
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
  private var listeners = Vector.empty[(Long, Boolean => Unit)]
  private var nextHandle = 0L
  private var disposedFlag = false

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
    * The order matters: focus first, then the selection. A host that takes focus with no
    * selection in it gets one from the browser -- usually the start of its content -- and writing
    * afterwards replaces that. Writing first and focusing after would show the browser's guess
    * for one frame.
    */
  def restore(bookmark: SelectionBookmark, intent: FocusIntent): RestoreOutcome =
    session.mappingSince(bookmark.revision).flatMap(bookmark.resolve) match
      case Left(expired) => RestoreOutcome.Expired(expired)
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
      val leaving: js.Function1[dom.Event, Unit] = _ =>
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
