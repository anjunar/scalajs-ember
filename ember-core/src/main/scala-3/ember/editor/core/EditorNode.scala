package ember.editor.core

/** Ein Knoten des Dokumentbaums.
  *
  * '''Bewusst nicht `sealed`.''' Ein ueber alle Features geschlossener Node-Vertrag wuerde
  * Erweiterungen in fremden Dateien und Modulen verhindern -- Paragraph, Heading, Liste, Link, Bild
  * und Tabelle liegen aber alle ausserhalb des Kerns (§8.1). Geschlossen sind stattdessen die
  * strukturellen Kategorien ([[ElementNode]], [[AtomNode]]), die Operationen und die
  * Validierungsergebnisse.
  *
  * Implementierungen sind unveraenderliche Case Classes. Ein gelesener Node ist genau der gelesene
  * Snapshot und loest sich nie selbst gegen einen neueren Stand auf -- Lexicals
  * `getLatest`/`getWritable` wird ausdruecklich nicht uebernommen (§3.2).
  */
trait EditorNode:

  /** Dokumentlokale Identitaet. Innerhalb eines Dokuments eindeutig. */
  def id: NodeId

/** Ein Knoten mit Kindern.
  *
  * Kinder sind ausschliesslich referenzierte IDs, keine eingebetteten Objekte (§8.2). Dadurch
  * kostet eine Textaenderung tief im Baum keine rekursive Kopie aller Vorfahren, und die
  * Elternzuordnung bleibt genau eine Wahrheit -- der abgeleitete Index in [[Document]].
  */
trait ElementNode extends EditorNode:

  def children: Vector[NodeId]

/** Ein atomarer Knoten: sein Inneres ist kein normaler Textbereich.
  *
  * Entspricht Lexicals `DecoratorNode`, aber bewusst enger (§3.4): keine Slots, keine editierbaren
  * Teilbereiche. Eine Bildunterschrift ist ein fachlicher Container, nicht ein impliziter UI-Slot
  * eines atomaren Blatts.
  */
trait AtomNode extends EditorNode

/** Die Dokumentwurzel. Genau eine je Dokument, nie Kind eines anderen Knotens (§8.2).
  *
  * Der Kern erlaubt eine leere Wurzel. Dass eine editierbare Flaeche mindestens einen Paragraph und
  * eine gueltige Caretposition braucht, ist eine Regel des Rich-Text-Profils, keine des Kerns.
  */
final case class RootNode(id: NodeId, children: Vector[NodeId]) extends ElementNode

object RootNode extends ElementNodeType[RootNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.core.root/1")

  def project(node: EditorNode): Option[RootNode] = node match
    case root: RootNode => Some(root)
    case _              => None

  def rekey(node: RootNode, id: NodeId): RootNode = node.copy(id = id)

  def withChildren(node: RootNode, children: Vector[NodeId]): RootNode =
    node.copy(children = children)

  def empty(id: NodeId): RootNode = RootNode(id, Vector.empty)

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
