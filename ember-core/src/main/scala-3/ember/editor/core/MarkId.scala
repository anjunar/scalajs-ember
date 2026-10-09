package ember.editor.core

/** Bezeichner einer Markierungsart, versioniert wie ein [[NodeTypeId]]. */
opaque type MarkId = String

object MarkId:

  def apply(value: String): MarkId =
    parse(value).getOrElse(
      throw EditorContractViolation(s"Keine gueltige MarkId: `$value`")
    )

  def parse(value: String): Option[MarkId] =
    if value.nonEmpty && !value.exists(c => c.isWhitespace || c.isControl) then Some(value)
    else None

  given Ordering[MarkId] = Ordering.String

  extension (id: MarkId) def value: String = id
