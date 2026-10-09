package ember.editor.core

/** Versionierter Name einer Node-Art, wie er in Wire-Formaten steht.
  *
  * Wire-IDs sind versionierte Namen (§8.1). Commands verwenden davon unabhaengig Objektidentitaet
  * -- ein `NodeTypeId` ist also kein Dispatch-Schluessel, sondern ein Persistenz- und
  * Diagnosebezeichner.
  */
opaque type NodeTypeId = String

object NodeTypeId:

  def apply(value: String): NodeTypeId =
    parse(value).getOrElse(
      throw EditorContractViolation(s"Keine gueltige NodeTypeId: `$value`")
    )

  def parse(value: String): Option[NodeTypeId] =
    if value.nonEmpty && !value.exists(c => c.isWhitespace || c.isControl) then Some(value)
    else None

  given Ordering[NodeTypeId] = Ordering.String

  extension (id: NodeTypeId) def value: String = id
