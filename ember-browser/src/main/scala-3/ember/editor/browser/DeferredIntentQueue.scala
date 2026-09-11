package ember.editor.browser

import ember.editor.core.*

/** What happens to a document change that arrives while a composition is running.
  *
  * §15.3 offers two answers and leaves the choice to the application, because both are defensible
  * and neither is free: refusing tells the caller now, deferring finishes the work later.
  */
enum BusyPolicy:

  /** Say no. The caller sees [[CompositionBusy]] and decides for itself. */
  case Reject

  /** Put it in the queue and run it when the composition ends. */
  case Defer

/** A change that waited for a composition to finish.
  *
  * ==Why a thunk and not a value==
  *
  * §15.3: "Es werden keine bereits berechneten Drafts spaeter angewendet." A queued change is
  * '''re-validated''' against the document that exists when it runs, not replayed against the one
  * it was written for. An upload that completed during a composition has to insert its picture
  * where the bookmark now points -- and to fail if that place is gone, rather than to insert it
  * somewhere else.
  *
  * The same reasoning as §16's deferred form intents, and deliberately the same shape.
  *
  * @param bookmark
  *   where the change wanted to happen, if it cared. Mapped forward when the queue is released; an
  *   expired one is reported, never guessed at.
  */
final case class DeferredIntent(
    label: String,
    bookmark: Option[Bookmark],
    run: (EditorSession, Option[Point]) => Either[UpdateError, Unit]
)

/** What a released intent came to. */
enum IntentOutcome:
  case Applied(label: String)
  case Rejected(label: String, error: String)

  /** The bookmark no longer points at anything (§11). The change did not happen. */
  case Expired(label: String)

/** The changes a composition made wait.
  *
  * ==Why it is bounded==
  *
  * Because the queue is a courtesy, not a buffer. §15.3 says "in eine '''begrenzte''' Queue", and
  * the reason is the failure mode: a composition that never ends -- a browser that loses its
  * `compositionend`, a user who walks away mid-word -- would otherwise collect work without limit
  * and apply all of it at once, minutes later, to a document nobody recognises.
  *
  * When it is full, the next intent is refused like any other. A refusal the caller sees beats a
  * promise nobody keeps.
  */
final class DeferredIntentQueue(val limit: Int = DeferredIntentQueue.DefaultLimit):

  private var pending = Vector.empty[DeferredIntent]

  def size: Int = pending.length

  def isEmpty: Boolean = pending.isEmpty

  /** Takes an intent, or says why not. */
  def offer(intent: DeferredIntent, busy: CompositionBusy): Either[CompositionBusy, Unit] =
    if pending.length >= limit then Left(busy)
    else
      pending = pending :+ intent
      Right(())

  /** Runs everything in order, mapping each bookmark to the document as it is now.
    *
    * The session is handed in rather than held, so the queue survives a session it does not own and
    * so that the caller decides when "now" is.
    */
  def release(session: EditorSession): Vector[IntentOutcome] =
    val waiting = pending
    pending = Vector.empty

    waiting.map { intent =>
      resolve(session, intent.bookmark) match
        case Left(_)      => IntentOutcome.Expired(intent.label)
        case Right(point) =>
          intent.run(session, point) match
            case Right(_)    => IntentOutcome.Applied(intent.label)
            case Left(error) => IntentOutcome.Rejected(intent.label, error.render)
    }

  /** Drops everything without running it. For dispose, and for a discarded composition. */
  def clear(): Vector[String] =
    val dropped = pending.map(_.label)
    pending = Vector.empty
    dropped

  private def resolve(
      session: EditorSession,
      bookmark: Option[Bookmark]
  ): Either[ExpiredBookmark, Option[Point]] =
    bookmark match
      case None       => Right(None)
      case Some(mark) =>
        // The strict resolution, not the fallback: §11 is explicit that an insertion may not take
        // the replacement boundary -- "wuerde eine Einfuegung an der Grenze das Ergebnis an einer
        // voellig anderen Stelle einsetzen". A caret may fall back; an upload may not.
        session.mappingSince(mark.revision).flatMap(mark.resolve).map(Some.apply)

object DeferredIntentQueue:

  /** Enough for the handful of things that can arrive during one word. */
  val DefaultLimit: Int = 16

  /** Runs an intent straight away, resolving its bookmark against the current document.
    *
    * The same path a queued intent takes when it is released -- so an intent behaves the same
    * whether it happened to arrive during a composition or not, which is the point of queueing it
    * rather than dropping it.
    */
  def runNow(session: EditorSession, intent: DeferredIntent): IntentOutcome =
    intent.bookmark match
      case None =>
        intent.run(session, None) match
          case Right(_)    => IntentOutcome.Applied(intent.label)
          case Left(error) => IntentOutcome.Rejected(intent.label, error.render)
      case Some(mark) =>
        session.mappingSince(mark.revision).flatMap(mark.resolve) match
          case Left(_)      => IntentOutcome.Expired(intent.label)
          case Right(point) =>
            intent.run(session, Some(point)) match
              case Right(_)    => IntentOutcome.Applied(intent.label)
              case Left(error) => IntentOutcome.Rejected(intent.label, error.render)
