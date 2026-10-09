package ember.editor.forms

import ember.editor.core.*
import ember.editor.image.*
import scala.concurrent.Future
import scala.util.control.NonFatal

trait MediaPreviews[F]:
  def create(file: F): Option[String]
  def revoke(url: String): Unit

object MediaPreviews:
  def none[F]: MediaPreviews[F] = new MediaPreviews[F]:
    def create(file: F)           = None
    def revoke(url: String): Unit = ()
