package ember.editor.link

import ember.editor.core.*
import ember.editor.richtext.*

/** Link normalisation.
  *
  * The same shape as the list rules, for the same reason (§3.2): the commands could each be careful
  * enough, and would then each be careful separately for as long as anyone remembers. A paste, a
  * move or a later feature would not be.
  */
private[link] object LinkNormalization:

  /** A link inside a link loses its wrapper.
    *
    * §8.2: "Ein Link enthaelt keine anderen Links." Unwrapping the '''inner''' one keeps the
    * stretch the author most likely meant -- the outer link covers more, so its target is the one
    * that survives, and no content is lost either way.
    *
    * The rule hangs on the inner node, which is the one whose parent changed and therefore the one
    * that is dirty. A rule on the outer link would look at children that a move made no claim about
    * (§3.4).
    */
  val noNestedLinks: Transform[LinkNode] = new Transform[LinkNode]:
    val name           = "link.no-nested-links"
    val nodeType       = LinkNode
    override val phase = TransformPhase.Early

    def transform(node: LinkNode, scope: TransformScope): Unit =
      val insideAnother = scope.document
        .parentOf(node.id)
        .flatMap(scope.document.node)
        .exists(_.isInstanceOf[LinkNode])

      if insideAnother then Links.unwrap(scope, node)

  /** A link with nothing in it is not a link.
    *
    * What is left when its last child is deleted or moved away. An empty `<a>` is invisible,
    * unclickable, and survives into every export.
    */
  val emptyLinkGoes: Transform[LinkNode] = new Transform[LinkNode]:
    val name           = "link.empty-link-goes"
    val nodeType       = LinkNode
    override val phase = TransformPhase.Late

    def transform(node: LinkNode, scope: TransformScope): Unit =
      if node.children.isEmpty then scope.remove(node.id): Unit

  /** Two neighbouring links to the same place become one.
    *
    * Linking two adjacent stretches separately leaves two anchors where a reader sees one clickable
    * region -- and where Markdown would write two identical link definitions.
    *
    * Backwards, like the list rule and for the same reason: the newly created link is the dirty
    * one, and a rule looking forward would only ever be asked on the node with nothing after it
    * (§3.4). That mistake has now been made twice; the third time it will be recognised on sight.
    */
  val adjacentLinksJoin: Transform[LinkNode] = new Transform[LinkNode]:
    val name           = "link.adjacent-links-join"
    val nodeType       = LinkNode
    override val phase = TransformPhase.Late

    def transform(node: LinkNode, scope: TransformScope): Unit =
      val document = scope.document

      val predecessor = for
        parent <- document.parentOf(node.id)
        index  <- document.indexOfChild(node.id)
        if index > 0
        before <- document.childrenOf(parent).lift(index - 1)
        link   <- document.node(before).collect {
          case value: LinkNode if value.target == node.target => value
        }
      yield link

      predecessor.foreach { earlier =>
        val target = earlier.children.length
        node.children.zipWithIndex.foreach { (child, offset) =>
          scope.move(child, earlier.id, target + offset): Unit
        }
      }

  val all: Vector[Transform[?]] = Vector(noNestedLinks, adjacentLinksJoin, emptyLinkGoes)
