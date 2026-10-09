package ember.editor.core

/** Eine Menge ganzer Knoten, etwa mehrere ausgewaehlte Bilder.
  *
  * Ausdruecklich nicht dasselbe wie mehrere unabhaengige Text-Carets und nicht dasselbe wie eine
  * Tabellenauswahl (§3.2). Normalisiert: ein Nachfahre eines bereits ausgewaehlten Vorfahren ist
  * redundant und wird entfernt.
  */
final case class NodeSelection(nodes: Set[NodeId]) extends Selection:

  def isEmpty: Boolean = nodes.isEmpty

  /** Entfernt Nachfahren bereits ausgewaehlter Vorfahren (§11). */
  def normalized(document: DocumentRead): NodeSelection =
    NodeSelection(nodes.filterNot(node => document.ancestorsOf(node).exists(nodes.contains)))
