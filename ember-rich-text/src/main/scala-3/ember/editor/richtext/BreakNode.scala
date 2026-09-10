package ember.editor.richtext

import ember.editor.core.*

/** Which kind of line break this is.
  *
  * §8.2 is explicit that the two must not collapse into one: "SoftBreak und HardBreak bleiben
  * unterscheidbar, damit Markdown und semantisches HTML ihre Bedeutung erhalten."
  *
  * The difference is real in both formats. In Markdown a soft break is a newline that renders as
  * a space; a hard break is two trailing spaces or a backslash and renders as `<br>`. Storing
  * them as the same node would make the export a guess, and §18 does not promise a
  * source-identical round trip but does promise to keep the supported semantics.
  */
enum BreakKind:

  /** A line break in the source that is not one in the output. */
  case Soft

  /** A break the author asked for. */
  case Hard

/** A line break inside a block.
  *
  * An [[AtomNode]]: it has no children and no inside to edit. §8.1 keeps atoms deliberately
  * narrower than Lexical's `DecoratorNode` -- "keine Slots, keine editierbaren Teilbereiche" --
  * and a break is the smallest possible case of that.
  *
  * ==Why it is not a `\n` in a text run==
  *
  * Because a text run's text is addressed in UTF-16 offsets (§11), and a caret can stand on
  * either side of a break. As a character, the two sides are the same offset with different
  * affinity; as a node, they are two distinct child positions. Selection, mapping and
  * normalisation all become simpler for it -- and the merge rule below gets a boundary it can
  * actually see.
  */
final case class BreakNode(id: NodeId, kind: BreakKind) extends AtomNode

object BreakNode extends NodeType[BreakNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.rich-text.break/1")

  def project(node: EditorNode): Option[BreakNode] = node match
    case value: BreakNode => Some(value)
    case _                => None

  def rekey(node: BreakNode, id: NodeId): BreakNode = node.copy(id = id)

  def soft(id: NodeId): BreakNode = BreakNode(id, BreakKind.Soft)
  def hard(id: NodeId): BreakNode = BreakNode(id, BreakKind.Hard)

/** A thematic break -- the horizontal rule. A block-level atom.
  *
  * Block-level, unlike [[BreakNode]]: it stands between blocks, not inside one. That is why it
  * is a separate type rather than a third [[BreakKind]] -- the two live at different levels of
  * the tree, and a schema rule that allowed both in both places would allow documents that
  * neither Markdown nor HTML can express.
  */
final case class ThematicBreakNode(id: NodeId) extends AtomNode

object ThematicBreakNode extends NodeType[ThematicBreakNode]:

  val typeId: NodeTypeId = NodeTypeId("ember.rich-text.thematic-break/1")

  def project(node: EditorNode): Option[ThematicBreakNode] = node match
    case value: ThematicBreakNode => Some(value)
    case _                        => None

  def rekey(node: ThematicBreakNode, id: NodeId): ThematicBreakNode = node.copy(id = id)
