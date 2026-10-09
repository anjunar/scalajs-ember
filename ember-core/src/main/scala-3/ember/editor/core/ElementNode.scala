package ember.editor.core

/** Ein Knoten mit Kindern.
  *
  * Kinder sind ausschliesslich referenzierte IDs, keine eingebetteten Objekte (§8.2). Dadurch
  * kostet eine Textaenderung tief im Baum keine rekursive Kopie aller Vorfahren, und die
  * Elternzuordnung bleibt genau eine Wahrheit -- der abgeleitete Index in [[Document]].
  */
trait ElementNode extends EditorNode:

  def children: Vector[NodeId]
