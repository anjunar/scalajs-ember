package ember.editor.core

/** Ein Textlauf mit normalisierten Markierungen.
  *
  * Blatt ohne Kinder und ohne Atom-Semantik. `text` ist ein gewoehnlicher Scala-String und wird in
  * UTF-16 adressiert -- demselben Mass wie DOM-`Text` (§11). Graphem-, Wort- und Dokumentgrenzen
  * entscheidet dieser Typ ausdruecklich nicht; dafuer gibt es den `TextBoundaryService` des
  * Rich-Text-Moduls.
  */
final case class TextNode(id: NodeId, text: String, marks: MarkSet) extends EditorNode

object TextNode extends NodeType[TextNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.core.text/1")

  def project(node: EditorNode): Option[TextNode] = node match
    case text: TextNode => Some(text)
    case _              => None

  def rekey(node: TextNode, id: NodeId): TextNode = node.copy(id = id)

  def apply(id: NodeId, text: String): TextNode = TextNode(id, text, MarkSet.empty)
