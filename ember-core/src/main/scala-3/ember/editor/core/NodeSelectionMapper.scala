package ember.editor.core

/** Eingebauter Mapper fuer [[NodeSelection]]. */
object NodeSelectionMapper extends SelectionMapper[NodeSelection]:

  def project(selection: Selection): Option[NodeSelection] = selection match
    case nodes: NodeSelection => Some(nodes)
    case _                    => None

  /** §11: geloeschte IDs entfernen, verschobene bewahren, Nachfahren normalisieren.
    *
    * Verschobene Knoten bleiben ausgewaehlt, weil sie noch existieren -- ihre Identitaet ueberlebt
    * einen Move (§8.3). Nur `removedNodes` der Abbildung entscheidet.
    */
  def map(
      selection: NodeSelection,
      mapping: PositionMapping,
      after: DocumentRead
  ): Option[Selection] =
    val surviving = NodeSelection(
      (selection.nodes -- mapping.removedNodes).filter(after.contains)
    ).normalized(after)
    Option.when(surviving.nodes.nonEmpty)(surviving)

  def validate(selection: NodeSelection, document: DocumentRead): Vector[Violation] =
    selection.nodes.toVector.sorted
      .filterNot(document.contains)
      .map(node =>
        Violation.NodeRejected(
          node,
          "Ausgewaehlter Knoten fehlt im Dokument.",
          DiagnosticPath.node(node.value)
        )
      )
