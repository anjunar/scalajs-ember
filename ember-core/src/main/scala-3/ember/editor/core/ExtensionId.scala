package ember.editor.core

/** Name einer Extension. Eindeutig innerhalb einer Sitzung. */
opaque type ExtensionId = String

object ExtensionId:

  def apply(value: String): ExtensionId =
    if value.nonEmpty && !value.exists(c => c.isWhitespace || c.isControl) then value
    else throw EditorContractViolation(s"Keine gueltige ExtensionId: `$value`")

  given Ordering[ExtensionId] = Ordering.String

  extension (id: ExtensionId) def value: String = id
