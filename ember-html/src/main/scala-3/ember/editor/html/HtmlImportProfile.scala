package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** What a profile supplies that HTML cannot say for itself.
  *
  * Pasted HTML has loose text at the top level, inline elements outside any block, and blocks
  * inside inline wrappers. A document has none of those. Something has to make a paragraph out of a
  * bare sentence -- and "paragraph" is a rich-text idea (§6 keeps it out of this module), so it
  * comes in from outside.
  */
trait HtmlImportProfile:

  /** A block for content that arrived without one. */
  def paragraph(children: Vector[NodeId], scope: HtmlImportScope): EditorNode

  /** A run of text carrying the marks that were open around it. */
  def text(value: String, marks: MarkSet, scope: HtmlImportScope): EditorNode

  /** The document root. */
  def root(children: Vector[NodeId], id: NodeId): EditorNode
