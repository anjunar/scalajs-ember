package ember.editor.code

import ember.editor.core.*

/** A block of code.
  *
  * ==What it may contain==
  *
  * §8.2: "CodeBlock erlaubt Text mit Zeilenumbruechen, aber keine beliebigen Rich-Text-Kinder."
  * Exactly one [[TextNode]], whose text may contain newlines, and no marks on it.
  *
  * The single run is not an accident of implementation. A code block's content is '''one string'''
  * -- that is what a fence writes, what a compiler reads and what an author copies. Several runs
  * would need a merge rule of their own, and would leave a door open for marks that this node type
  * exists to keep shut.
  *
  * [[CodeNormalization]] collapses whatever ends up inside into that one run rather than rejecting
  * the document: content pasted into a code block should become code, not an error.
  *
  * ==What it does not have==
  *
  * A highlighter. P15's acceptance is explicit: "Kein Syntax-Highlighter und kein CodeMirror als
  * Produktionsabhaengigkeit." The language is a '''metadatum''' -- it says what the text is, not
  * how it looks. Highlighting is a rendering concern, and the risk line says why it has to stay
  * one: "Sichtbares Highlighting darf spaeter keine persistente Mark-Zerlegung jeder Codezeile
  * erzwingen." A document whose every token became a marked run would be unreadable as a document
  * and unwritable as a fence.
  */
final case class CodeBlockNode(
    id: NodeId,
    children: Vector[NodeId],
    info: CodeInfo = CodeInfo.empty
) extends ElementNode

object CodeBlockNode extends ElementNodeType[CodeBlockNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.code.block/1")

  def project(node: EditorNode): Option[CodeBlockNode] = node match
    case value: CodeBlockNode => Some(value)
    case _                    => None

  def rekey(node: CodeBlockNode, id: NodeId): CodeBlockNode = node.copy(id = id)

  def withChildren(node: CodeBlockNode, children: Vector[NodeId]): CodeBlockNode =
    node.copy(children = children)

  def of(id: NodeId, textId: NodeId, info: CodeInfo = CodeInfo.empty): CodeBlockNode =
    CodeBlockNode(id, Vector(textId), info)

  /** The code as one string. Empty when the block has no run yet. */
  def textOf(node: CodeBlockNode, document: DocumentRead): String =
    node.children.headOption
      .flatMap(document.node)
      .collect { case run: TextNode => run.text }
      .getOrElse("")
