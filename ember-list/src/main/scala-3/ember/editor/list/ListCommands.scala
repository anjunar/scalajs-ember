package ember.editor.list

import ember.editor.core.*
import ember.editor.richtext.*

/** The commands a list contributes. Values, not names (§12). */
object ListCommands:

  /** Makes the block at the caret a list of this kind, or unwraps it if it already is one. */
  val ToggleList: EditorCommand[ListKind] = EditorCommand.of[ListKind]("list.toggle")

  /** Moves the item at the caret one level deeper. */
  val Indent: EditorCommand[Unit] = EditorCommand.unit("list.indent")

  /** Moves the item at the caret one level out, or out of the list entirely. */
  val Outdent: EditorCommand[Unit] = EditorCommand.unit("list.outdent")

/** Turning blocks into lists and back, and moving items between levels.
  *
  * ==Everything here is a move==
  *
  * Not a replacement. §11's mapping table says what that buys: `Move` keeps the node, so every
  * point inside it survives untouched, and a caret in the third word of an indented paragraph
  * is still in the third word afterwards. Rebuilding the item instead would be simpler to write
  * and would lose the caret on every Tab.
  *
  * The risk line of P13 names the other side of it: "Reparenting kann mehrmals dieselbe Grenze
  * verschieben." Each operation below therefore reads the positions it needs *before* it starts
  * moving, and never mixes a read of the old structure with a write to the new one.
  */
object ListEditing:

  // -----------------------------------------------------------------------------------------
  // Wrapping and unwrapping
  // -----------------------------------------------------------------------------------------

  /** Wraps the block at the caret in a list, or unwraps it if it is already one of that kind.
    *
    * Toggling to a *different* kind changes the existing list rather than unwrapping and
    * re-wrapping -- a numbered list that becomes a bulleted one is the same list, and treating
    * it as a new one would throw away its items' identities for nothing.
    */
  def toggle(
      scope: TransformScope,
      generator: NodeIdGenerator,
      kind: ListKind
  ): Either[UpdateError, Unit] =
    Lists.contextAt(scope) match
      case Some(context) if context.kind == kind => unwrap(scope, generator, context)
      case Some(context)                         => changeKind(scope, context, kind)
      case None                                  => wrap(scope, generator, kind)

  private def changeKind(
      scope: TransformScope,
      context: ListContext,
      kind: ListKind
  ): Either[UpdateError, Unit] =
    scope.document.node(context.list).collect { case value: ListNode => value } match
      case Some(list) => scope.replace(list.id, list.copy(kind = kind))
      case None       => Right(())

  private def wrap(
      scope: TransformScope,
      generator: NodeIdGenerator,
      kind: ListKind
  ): Either[UpdateError, Unit] =
    (for
      block     <- Lists.blockAtCaret(scope)
      container <- scope.document.parentOf(block)
      index     <- scope.document.indexOfChild(block)
    yield (block, container, index)) match
      case None => Right(())
      case Some((block, container, index)) =>
        val ids    = generator.nextBatch(2, scope.document.contains)
        val listId = ids.head
        val itemId = ids.last

        for
          _ <- scope.insert(container, index, ListNode.empty(listId, kind))
          _ <- scope.insert(listId, 0, ListItemNode.empty(itemId))
          _ <- scope.move(block, itemId, 0)
        yield ()

  /** Takes the item's blocks out of the list, splitting the list around them.
    *
    * ==Three things happen, and all three are necessary==
    *
    *   1. The item's blocks move out, directly after the list.
    *   1. The now empty item is removed. Leaving it behind would give [[ListNormalization]] an
    *      item with nothing in it, which it would dutifully fill with a fresh paragraph -- an
    *      empty bullet nobody asked for, right where one was just removed.
    *   1. The items *after* it move into a new list behind the extracted blocks. Without this
    *      they would stay in the first list, and the document would read "Eins, Drei, Zwei"
    *      when the author took the middle one out.
    *
    * The order matters: every move shifts what comes after it, so the positions are computed
    * once from the state before any of them and then counted forward. That is the "dieselbe
    * Grenze mehrmals verschieben" from P13's risk line.
    */
  private def unwrap(
      scope: TransformScope,
      generator: NodeIdGenerator,
      context: ListContext
  ): Either[UpdateError, Unit] =
    val document = scope.document

    (for
      container <- document.parentOf(context.list)
      listIndex <- document.indexOfChild(context.list)
    yield (container, listIndex)) match
      case None => Right(())
      case Some((container, listIndex)) =>
        val blocks    = document.childrenOf(context.item)
        val followers = document.childrenOf(context.list).drop(context.indexInList + 1)

        for
          _ <- moveAll(scope, blocks, container, listIndex + 1)
          _ <- scope.remove(context.item)
          _ <- splitOff(scope, generator, context, container, listIndex + 1 + blocks.length,
                 followers)
        yield ()

  /** Moves the items that came after into a list of their own.
    *
    * A numbered list keeps counting: the followers begin at their original number, so taking
    * the second item out of `1. 2. 3.` leaves `1.` and `3.` and not `1.` and `1.` -- §18.2 asks
    * for the start number to survive, and this is where it would otherwise be lost.
    *
    * An unordered list keeps its default. `start` means nothing there ([[ListNode]]), and
    * carrying a number into it would put a `start="3"` on a `<ul>` -- an attribute HTML ignores
    * and every diff of the document shows.
    */
  private def splitOff(
      scope: TransformScope,
      generator: NodeIdGenerator,
      context: ListContext,
      container: NodeId,
      at: Int,
      followers: Vector[NodeId]
  ): Either[UpdateError, Unit] =
    if followers.isEmpty then Right(())
    else
      scope.document.node(context.list).collect { case value: ListNode => value } match
        case None => Right(())
        case Some(list) =>
          val listId = generator.nextFor(scope.document)
          for
            _ <- scope.insert(
                   container,
                   at,
                   ListNode(
                     listId,
                     Vector.empty,
                     list.kind,
                     if list.kind == ListKind.Ordered then list.start + context.indexInList + 1
                     else list.start,
                     list.tight
                   )
                 )
            _ <- moveAll(scope, followers, listId, 0)
          yield ()

  // -----------------------------------------------------------------------------------------
  // Indent and outdent
  // -----------------------------------------------------------------------------------------

  /** Moves the item one level deeper, into the item above it.
    *
    * ==Why the first item cannot be indented==
    *
    * Because there is nothing to indent it *into*. A nested list is a child of an item, so the
    * item above is the only possible new home -- and the first item has none. Every editor
    * behaves this way, and the alternative (inventing an empty parent item) produces a bullet
    * that the author never typed.
    *
    * If the item above already ends with a list of the same kind, the item joins it. Otherwise
    * a new one is created there. Without that check, indenting three items in a row would
    * produce three nested lists of one item each.
    */
  def indent(
      scope: TransformScope,
      generator: NodeIdGenerator
  ): Either[UpdateError, Unit] =
    Lists.contextAt(scope) match
      case Some(context) if context.indexInList > 0 =>
        val document = scope.document
        val previous = document.childrenOf(context.list)(context.indexInList - 1)

        lastSublist(document, previous, context.kind) match
          case Some(sublist) =>
            scope.move(context.item, sublist, document.childrenOf(sublist).length)
          case None =>
            val listId = generator.nextFor(document)
            for
              _ <- scope.insert(previous, document.childrenOf(previous).length,
                     ListNode.empty(listId, context.kind))
              _ <- scope.move(context.item, listId, 0)
            yield ()

      case _ => Right(())

  private def lastSublist(
      document: DocumentRead,
      item: NodeId,
      kind: ListKind
  ): Option[NodeId] =
    document
      .childrenOf(item)
      .lastOption
      .flatMap(document.node)
      .collect { case list: ListNode if list.kind == kind => list.id }

  /** Moves the item one level out.
    *
    * Two cases, and they are genuinely different:
    *
    *   - '''Nested.''' The list sits inside an item, so this item becomes that item's next
    *     sibling. It stays a list item; only its depth changes.
    *   - '''Top level.''' There is nothing to be a sibling of, so the item's blocks leave the
    *     list altogether -- the same thing [[unwrap]] does, and the same code.
    */
  def outdent(scope: TransformScope, generator: NodeIdGenerator): Either[UpdateError, Unit] =
    Lists.contextAt(scope) match
      case None => Right(())
      case Some(context) =>
        val document = scope.document

        val enclosing = for
          parentItem <- document.parentOf(context.list)
          _          <- document.node(parentItem).collect { case value: ListItemNode => value }
          parentList <- document.parentOf(parentItem)
          index      <- document.indexOfChild(parentItem)
        yield (parentList, index)

        enclosing match
          case Some((parentList, index)) =>
            val followers = document.childrenOf(context.list).drop(context.indexInList + 1)
            for
              _ <- scope.move(context.item, parentList, index + 1)
              _ <- adopt(scope, generator, context, followers)
            yield ()
          case None => unwrap(scope, generator, context)

  /** Takes the items that came after along, as a sublist of the outdented one.
    *
    * ==Why they cannot simply stay==
    *
    * Because the item leaves through the *bottom* of its parent item -- it becomes the parent's
    * next sibling, which in reading order is after everything still nested inside it. Leaving
    * the followers behind would move them in front of the item they used to follow: outdent
    * "Zwei" from a sublist `[Zwei, Drei]` and the document would read "Drei, Zwei".
    *
    * Making them children of the outdented item keeps the order and keeps the nesting depth
    * they had relative to it. It is also what makes indent and outdent inverse: indent a run of
    * items, outdent the first, and the shape is back.
    */
  private def adopt(
      scope: TransformScope,
      generator: NodeIdGenerator,
      context: ListContext,
      followers: Vector[NodeId]
  ): Either[UpdateError, Unit] =
    if followers.isEmpty then Right(())
    else
      scope.document.node(context.list).collect { case value: ListNode => value } match
        case None => Right(())
        case Some(list) =>
          val listId = generator.nextFor(scope.document)
          val start =
            if list.kind == ListKind.Ordered then list.start + context.indexInList + 1
            else list.start

          for
            _ <- scope.insert(
                   context.item,
                   scope.document.childrenOf(context.item).length,
                   ListNode(listId, Vector.empty, list.kind, start, list.tight)
                 )
            _ <- moveAll(scope, followers, listId, 0)
          yield ()

  // -----------------------------------------------------------------------------------------
  // Enter and Backspace
  // -----------------------------------------------------------------------------------------

  /** Enter inside a list item.
    *
    * An '''empty''' item means the author is done with the list: the item moves out one level,
    * and at the top level that ends the list. A '''full''' item splits, and the part after the
    * caret becomes a new item.
    *
    * Returns `Pass` when the caret is not in a list, so the rich-text handler behind it does
    * the ordinary thing. That is what §12's priority chain is for -- this module does not
    * replace paragraph splitting, it takes precedence where lists are involved.
    */
  def insertParagraph(
      scope: TransformScope,
      generator: NodeIdGenerator
  ): CommandResult =
    Lists.contextAt(scope) match
      case None => CommandResult.Pass
      case Some(context) =>
        if Lists.isEmpty(scope.document, context.item) then
          outdent(scope, generator): Unit
          CommandResult.Handled
        else
          splitItem(scope, generator, context)
          CommandResult.Handled

  /** Splits the item at the caret.
    *
    * ==Why it calls the function and not the command==
    *
    * The block split lives in the rich-text profile, handles marks and caret placement, and a
    * second implementation here would drift from it. But it cannot be reached by dispatching
    * `RichText.InsertParagraph`: §10 gives command handlers a [[TransformScope]] precisely so
    * they *cannot* start another dispatch -- "eine Command-Kette, die sich selbst verlaengert,
    * ist genau die verdeckte Reentranz, die §10 ausschliesst".
    *
    * So the shared code is called as what it is: a function on the draft. Same behaviour, no
    * chain, and the restriction stays intact rather than being worked around.
    */
  private def splitItem(
      scope: TransformScope,
      generator: NodeIdGenerator,
      context: ListContext
  ): Unit =
    val before = scope.document.childrenOf(context.item)

    TextEditing.insertParagraph(scope, generator): Unit

    // The split produced one more block in this item; everything from the caret's block onwards
    // belongs to the new item.
    val after = scope.document.childrenOf(context.item)
    val fresh = after.drop(before.indexOf(context.block) + 1)

    if fresh.nonEmpty then
      val itemId = generator.nextFor(scope.document)
      for
        _ <- scope.insert(context.list, context.indexInList + 1, ListItemNode.empty(itemId))
        _ <- moveAll(scope, fresh, itemId, 0)
      yield ()

  /** Backspace at the very start of a list item.
    *
    * The key means "undo the indentation" before it means "delete a character" -- there is no
    * character to the left inside this item, and joining with the item above would silently
    * merge two bullets the author still wants apart.
    *
    * Only at the start of the item's '''first''' block, and only for a collapsed caret: a
    * selection means the author asked for a deletion.
    */
  def deleteBackward(scope: TransformScope, generator: NodeIdGenerator): CommandResult =
    Lists.contextAt(scope) match
      case Some(context)
          if Lists.atBlockStart(scope) &&
            scope.document.childrenOf(context.item).headOption.contains(context.block) =>
        outdent(scope, generator): Unit
        CommandResult.Handled
      case _ => CommandResult.Pass

  // -----------------------------------------------------------------------------------------
  // Shared
  // -----------------------------------------------------------------------------------------

  /** Moves several nodes to consecutive positions, keeping their order.
    *
    * Each move shifts the ones after it, so the index counts up as we go. Getting this wrong is
    * the "dieselbe Grenze mehrmals verschieben" from the risk line, and it shows up as blocks
    * arriving in reverse.
    */
  private def moveAll(
      scope: TransformScope,
      nodes: Vector[NodeId],
      target: NodeId,
      from: Int
  ): Either[UpdateError, Unit] =
    nodes.zipWithIndex.foldLeft[Either[UpdateError, Unit]](Right(())) {
      case (accumulated, (node, offset)) =>
        accumulated.flatMap(_ => scope.move(node, target, from + offset))
    }
