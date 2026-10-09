package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** What a rule decides about one element.
  *
  * §19.1: "`HtmlImportRule[N]` definiert Tag-/Attributerkennung, Prioritaet, Node-Erzeugung sowie
  * Verarbeitung von Kindern." These five cases are that last part -- the children are handled
  * differently for each, and a rule says which by choosing one.
  */
enum HtmlImportDecision:

  /** A node whose children are this element's children.
    *
    * The children are imported '''first''' and their ids handed over, so that a rule never has to
    * know how importing works -- it receives a list and builds one node.
    */
  case Container(
      create: Vector[NodeId] => EditorNode,
      level: NodeLevel = NodeLevel.Block,
      children: ChildMode = ChildMode.Inline,
      whitespace: Whitespace = Whitespace.Collapse
  )

  /** A node with no children: an image, a break. */
  case Leaf(node: EditorNode, level: NodeLevel = NodeLevel.Inline)

  /** An inline wrapper that contributes a mark to the text inside it (§8.2). */
  case Marked(mark: TextMark)

  /** Keep the children, drop the element.
    *
    * §19.1: "unbekannte harmlose Wrapper werden mit erhaltenem Text aufgeloest." The default for
    * everything nobody claims, and the reason a paste from an unknown page still arrives as
    * readable prose.
    */
  case Unwrap

  /** Drop the element and everything in it, and say why. */
  case Discard(reason: String)
