package ember.editor.image

import ember.editor.core.*

/** The application's own identifier for a stored file.
  *
  * Optional, and deliberately opaque to this module: what it means -- a database key, a hash, an S3
  * object name -- is the application's business (§20, "Uploads sind ein Anwendungsservice"). What
  * matters here is that it is a stable name and not a file, a handle or an object URL.
  */
opaque type MediaId = String

object MediaId:

  def parse(value: String): Option[MediaId] =
    val trimmed = value.trim
    if trimmed.nonEmpty && !trimmed.exists(character => character.isControl) then Some(trimmed)
    else None

  def apply(value: String): MediaId =
    parse(value).getOrElse(
      throw EditorContractViolation(s"Keine gueltige MediaId: `$value`")
    )

  extension (id: MediaId) def value: String = id
