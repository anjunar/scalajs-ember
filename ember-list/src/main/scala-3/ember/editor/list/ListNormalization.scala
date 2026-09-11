package ember.editor.list

import ember.editor.core.*
import ember.editor.richtext.*

/** The rules that keep lists legal while they are being edited.
  *
  * ==What they are for==
  *
  * Every list command is a move, and a move can leave a structure that no renderer accepts: an item
  * with nothing in it, a list with nothing in it, a paragraph that landed directly in a list. §8.2
  * fixes the shape ("eine Liste ListItems, ein ListItem Blockinhalte") and P13's acceptance names
  * the one that matters most: "Kein nackter Paragraph direkt in ListNode."
  *
  * ==Why they repair instead of reject==
  *
  * The commands could each be careful enough never to produce these. They would then each be
  * careful, separately, for as long as anyone remembers -- and the first foreign module that moves
  * a node into a list would not be. §3.2 puts invariant repair in transforms for exactly this
  * reason: the rule holds regardless of who caused the problem.
  *
  * ==Termination==
  *
  * P13's acceptance asks for it, and the risk is real: a rule that wraps loose children and a rule
  * that unwraps empty items can feed each other forever, and §10's budget would abort the
  * transaction after 32 rounds. Each rule below therefore either removes a node or reduces the
  * number of misplaced ones, and none of them creates work for another.
  */
private[list] object ListNormalization:

  /** A block that landed directly in a list gets an item around it.
    *
    * The usual way it happens is an outdent that moved a paragraph one level too far, or a foreign
    * module inserting where it should not. Wrapping is the repair that keeps the content: rejecting
    * would fail the whole transaction, and dropping would lose text.
    *
    * One child per pass. `insert` and `move` change the list underneath, so a loop here would walk
    * a stale vector -- the transform loop comes back while the node stays dirty (§10).
    */
  def looseChildNeedsItem(generator: NodeIdGenerator): Transform[ListNode] =
    new Transform[ListNode]:
      val name           = "list.loose-child-needs-item"
      val nodeType       = ListNode
      override val phase = TransformPhase.Early

      def transform(node: ListNode, scope: TransformScope): Unit =
        val document = scope.document

        node.children.zipWithIndex
          .find((child, _) => !document.node(child).exists(_.isInstanceOf[ListItemNode]))
          .foreach { (child, index) =>
            val itemId = generator.nextFor(document)
            for
              _ <- scope.insert(node.id, index, ListItemNode.empty(itemId))
              // The child is now one position further along.
              _ <- scope.move(child, itemId, 0)
            yield ()
          }

  /** An empty item gets a paragraph, so that there is somewhere to put a caret.
    *
    * The same reason as `BlockNeedsText` in the rich-text profile, one level up: a caret needs a
    * text position, and a child position in an empty item is not one.
    */
  def emptyItemNeedsBlock(generator: NodeIdGenerator): Transform[ListItemNode] =
    new Transform[ListItemNode]:
      val name           = "list.empty-item-needs-block"
      val nodeType       = ListItemNode
      override val phase = TransformPhase.Late

      def transform(node: ListItemNode, scope: TransformScope): Unit =
        if node.children.isEmpty then
          scope.insert(node.id, 0, ParagraphNode.empty(generator.nextFor(scope.document))): Unit

  /** A list with no items disappears.
    *
    * What is left behind after the last item is outdented. Keeping it would leave an invisible
    * `<ul></ul>` in the output and an empty bullet in every renderer that draws one.
    *
    * '''After''' [[emptyItemNeedsBlock]] in the phase order, so the two cannot chase each other:
    * this one only ever removes, and it looks at items, not at their contents.
    */
  val emptyListGoes: Transform[ListNode] = new Transform[ListNode]:
    val name           = "list.empty-list-goes"
    val nodeType       = ListNode
    override val phase = TransformPhase.Late

    def transform(node: ListNode, scope: TransformScope): Unit =
      if node.children.isEmpty then scope.remove(node.id): Unit

  /** Two adjacent lists of the same kind become one.
    *
    * ==Why this is needed and not merely tidy==
    *
    * Outdenting an item out of the middle of a list splits it in two, and indenting the item after
    * it produces a second nested list next to the first. Without this rule, a few keystrokes leave
    * a document whose HTML has three `<ul>` where the author sees one list -- and whose Markdown
    * export renumbers, because each list starts again.
    *
    * Only the same kind, and only direct neighbours. A numbered list next to a bulleted one is two
    * lists in every format, and this rule is deliberately blind to anything further away: the left
    * list's `start` and `tight` win, because it is the one that was there first.
    *
    * ==Why it looks backwards==
    *
    * Because the dirty node has to be the one that acts. Wrapping a paragraph creates a *new* list
    * next to an existing one: the new one is in `ChangeSet.created`, the old one is untouched and
    * never becomes a transform candidate (§3.4). A rule that looked forward would be asked only on
    * the node that has nothing after it, and would sit there doing nothing -- the same mistake the
    * text-run merge made in P12, in the same shape.
    */
  val adjacentListsJoin: Transform[ListNode] = new Transform[ListNode]:
    val name           = "list.adjacent-lists-join"
    val nodeType       = ListNode
    override val phase = TransformPhase.Late

    def transform(node: ListNode, scope: TransformScope): Unit =
      val document = scope.document

      val predecessor = for
        parent <- document.parentOf(node.id)
        index  <- document.indexOfChild(node.id)
        if index > 0
        before <- document.childrenOf(parent).lift(index - 1)
        list   <- document.node(before).collect {
          case value: ListNode if value.kind == node.kind => value
        }
      yield list

      predecessor.foreach { earlier =>
        val target = earlier.children.length
        node.children.zipWithIndex.foreach { (item, offset) =>
          scope.move(item, earlier.id, target + offset): Unit
        }
      }

  /** Every rule, in the order they should be registered. */
  def all(generator: NodeIdGenerator): Vector[Transform[?]] =
    Vector(
      looseChildNeedsItem(generator),
      emptyItemNeedsBlock(generator),
      adjacentListsJoin,
      emptyListGoes
    )
