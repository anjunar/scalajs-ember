package ember.editor.forms

import ember.editor.core.*
import ember.editor.json.*
import ember.editor.markdown.*

/** A block type this suite invents, so that nothing here needs the rich-text profile.
  *
  * §6 gives `ember-forms` the core, `markdown`, `json`, `html` and `ui` -- no node module. If the
  * field contracts ever needed a `ParagraphNode`, this file would stop compiling.
  */
final case class Box(id: NodeId, children: Vector[NodeId]) extends ElementNode

object Box extends ElementNodeType[Box]:
  val typeId: NodeTypeId = NodeTypeId("test.box/1")

  def project(node: EditorNode): Option[Box] = node match
    case box: Box => Some(box)
    case _        => None

  def rekey(node: Box, id: NodeId): Box                      = node.copy(id = id)
  def withChildren(node: Box, children: Vector[NodeId]): Box = node.copy(children = children)
