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
