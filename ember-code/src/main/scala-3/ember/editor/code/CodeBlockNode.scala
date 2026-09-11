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

/** What a fence says about its content.
  *
  * §18.2 asks for "Info-/Sprachmetadaten typisiert behandeln", and the split below is what that
  * means in practice. Markdown writes one info string; its '''first word''' is the language and
  * every renderer treats it as such, while the rest is whatever the author's toolchain wanted:
  *
  * {{{
  * ```scala {highlight=3-5}
  *      ^^^^^ language   ^^^^^^^^^^^^^^ meta
  * }}}
  *
  * Keeping them apart lets [[CodeSupport]] write `language-scala` without inventing a parser, and
  * lets P18 write the info string back unchanged. Keeping them together as one string would force
  * every consumer to split it again, each in its own slightly different way.
  */
final case class CodeInfo(language: Option[CodeLanguage] = None, meta: Option[String] = None):

  /** The info string as Markdown writes it. Empty when there is nothing to say. */
  def render: String =
    (language.map(_.value).toVector ++ meta.toVector).mkString(" ")

object CodeInfo:

  val empty: CodeInfo = CodeInfo()

  def of(language: String): CodeInfo = CodeInfo(CodeLanguage.parse(language))

  /** Reads an info string: first word is the language, the rest is meta.
    *
    * Never fails. An info string that names no valid language is not an error -- it is an info
    * string without a language, and the text is kept as meta so that a round trip does not lose it
    * (§18.2: "Inhalt einschliesslich innerer Leerzeilen erhalten").
    */
  def parse(info: String): CodeInfo =
    val trimmed = info.trim
    if trimmed.isEmpty then empty
    else
      val (head, rest) = trimmed.span(!_.isWhitespace)
      val meta         = Some(rest.trim).filter(_.nonEmpty)

      CodeLanguage.parse(head) match
        case Some(language) => CodeInfo(Some(language), meta)
        case None           => CodeInfo(None, Some(trimmed))

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
