package ember.editor.forms

import ember.editor.core.*
import ember.editor.image.*
import scala.concurrent.Future
import scala.util.control.NonFatal

final case class MediaFailure(message: String) extends EditorError

/** Storage, transport and backend validation belong to the application. */
trait MediaService[F]:
  def upload(file: F, cancellation: MediaCancellation): Future[MediaReference]

/** A service can connect its AbortController and report progress through this token. */
final class MediaCancellation private[forms] (progress: Double => Unit):
  private var stopped                              = false
  private var cancelled                            = false
  private var listeners                            = Vector.empty[() => Unit]
  def isCancelled: Boolean                         = cancelled
  def onCancel(callback: () => Unit): Subscription =
    if cancelled then
      safely(callback)
      Subscription.cancelled
    else if stopped then Subscription.cancelled
    else
      listeners :+= callback
      Subscription(() => listeners = listeners.filterNot(_ eq callback))
  def reportProgress(fraction: Double): Unit =
    if !stopped && !fraction.isNaN && !fraction.isInfinity then progress(fraction.max(0).min(1))
  private[forms] def cancel(): Unit =
    if !stopped then
      stopped = true
      cancelled = true
      val callbacks = listeners
      listeners = Vector.empty
      callbacks.foreach(safely)
  private[forms] def release(): Unit =
    stopped = true
    listeners = Vector.empty
  private def safely(callback: () => Unit): Unit =
    try callback()
    catch case NonFatal(_) => ()

trait MediaPreviews[F]:
  def create(file: F): Option[String]
  def revoke(url: String): Unit

object MediaPreviews:
  def none[F]: MediaPreviews[F] = new MediaPreviews[F]:
    def create(file: F)           = None
    def revoke(url: String): Unit = ()
