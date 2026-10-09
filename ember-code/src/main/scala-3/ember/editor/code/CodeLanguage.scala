package ember.editor.code

import ember.editor.core.*

/** The name of a language, as it appears in a fence's info string.
  *
  * Validated rather than free text: it becomes a class name in HTML and the first word of a fence
  * in Markdown, and neither survives whitespace. A backtick is refused as well -- CommonMark
  * forbids one in the info string of a backtick fence, and a value that cannot be written back is
  * not a value this document may hold.
  */
opaque type CodeLanguage = String

object CodeLanguage:

  def parse(value: String): Option[CodeLanguage] =
    val trimmed = value.trim
    if trimmed.nonEmpty && !trimmed.exists(isForbidden) then Some(trimmed) else None

  def apply(value: String): CodeLanguage =
    parse(value).getOrElse(
      throw EditorContractViolation(s"Keine gueltige Sprachangabe: `$value`")
    )

  private def isForbidden(character: Char): Boolean =
    character.isWhitespace || character.isControl || character == '`'

  given Ordering[CodeLanguage] = Ordering.String

  extension (language: CodeLanguage) def value: String = language
