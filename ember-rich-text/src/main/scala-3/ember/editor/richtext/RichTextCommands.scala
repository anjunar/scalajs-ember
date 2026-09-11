package ember.editor.richtext

import ember.editor.core.*

/** The block-level commands: heading, quote, breaks.
  *
  * ==What they have in common==
  *
  * Every one of them works on the *block containing the caret*, not on the caret's run. Finding
  * that block is the only shared piece of work, and it is the reason this file exists as something
  * other than four unrelated functions.
  *
  * ==What they deliberately do not do==
  *
  * They do not touch the DOM, and they do not decide how a heading looks. A command changes the
  * document; what an `h2` renders as is `ember-standard`'s answer, and it is a different answer for
  * SSR, for the editor and for Markdown.
  */
object BlockFormatting:

  /** Turns the block at the caret into a heading, or back into a paragraph with `None`.
    *
    * ==Why replace and not rekey==
    *
    * The block keeps its ID and its children; only its type changes. `Operation.Replace` is exactly
    * that operation -- "Ersetzt den Inhalt eines Knotens unter Beibehaltung von Identitaet und
    * Kindern" -- so points inside the block survive untouched. Removing the old block and inserting
    * a new one would move every child and invalidate every bookmark in it.
    */
  def setHeading(
      scope: TransformScope,
      level: Option[HeadingLevel]
  ): Either[UpdateError, Unit] =
    blockAtCaret(scope) match
      case None        => Right(())
      case Some(block) =>
        (scope.document.node(block), level) match
          case (Some(heading: HeadingNode), Some(wanted)) if heading.level == wanted => Right(())
          case (Some(element: ElementNode), Some(wanted))                            =>
            scope.replace(block, HeadingNode(block, element.children, wanted))
          case (Some(_: ParagraphNode), None)     => Right(())
          case (Some(element: ElementNode), None) =>
            scope.replace(block, ParagraphNode(block, element.children))
          case _ => Right(())

  /** Wraps the block at the caret in a quote.
    *
    * A quote holds blocks (§8.2), so this inserts a container and moves the block into it -- it
    * does not change the block's type. Quoting an already quoted block nests it, which is what
    * every editor does and what Markdown writes as `>>`.
    */
  def quote(
      scope: TransformScope,
      generator: NodeIdGenerator
  ): Either[UpdateError, Unit] =
    (for
      block     <- blockAtCaret(scope)
      container <- scope.document.parentOf(block)
      index     <- scope.document.indexOfChild(block)
    yield (block, container, index)) match
      case None                            => Right(())
      case Some((block, container, index)) =>
        val quoteId = generator.nextFor(scope.document)
        for
          _ <- scope.insert(container, index, QuoteNode.empty(quoteId))
          _ <- scope.move(block, quoteId, 0)
        yield ()

  /** Takes the block at the caret back out of its quote.
    *
    * The block moves to where the quote sits; a quote left with no children is removed. Nothing
    * happens if the block is not in a quote -- `Unquote` is not an error, it is a no-op, and
    * `CommandResult` says so at the call site.
    */
  def unquote(scope: TransformScope): Either[UpdateError, Unit] =
    (for
      block     <- blockAtCaret(scope)
      quoteId   <- scope.document.parentOf(block)
      _         <- scope.document.node(quoteId).collect { case value: QuoteNode => value }
      container <- scope.document.parentOf(quoteId)
      index     <- scope.document.indexOfChild(quoteId)
    yield (block, quoteId, container, index)) match
      case None                                     => Right(())
      case Some((block, quoteId, container, index)) =>
        for
          _ <- scope.move(block, container, index)
          _ <- dropIfEmpty(scope, quoteId)
        yield ()

  private def dropIfEmpty(scope: TransformScope, quoteId: NodeId): Either[UpdateError, Unit] =
    scope.document.node(quoteId).collect { case value: QuoteNode => value } match
      case Some(value) if value.children.isEmpty => scope.remove(quoteId)
      case _                                     => Right(())

  /** Inserts a break at the caret, splitting the run around it.
    *
    * The caret lands *after* the break, on the second half -- that is where the next character
    * belongs. A break at the very end of a run leaves no second half, so one is created: a caret
    * has to stand somewhere, and a child position after an atom is not a text position.
    */
  def insertBreak(
      scope: TransformScope,
      generator: NodeIdGenerator,
      kind: BreakKind
  ): Either[UpdateError, Unit] =
    (for
      caret          <- caretOf(scope)
      (node, offset) <- textPositionOf(caret)
      block          <- scope.document.parentOf(node)
      index          <- scope.document.indexOfChild(node)
    yield (node, offset, block, index)) match
      case None                               => Right(())
      case Some((node, offset, block, index)) =>
        val run     = textOf(scope.document, node)
        val breakId = generator.nextFor(scope.document)

        val split =
          // Split first, so the break lands between two runs rather than inside one.
          if offset > 0 && offset < run.length then
            scope.splitText(node, offset, generator.nextFor(scope.document))
          else Right(())

        for
          _ <- split
          at = if offset == 0 then index else index + 1
          _ <- scope.insert(block, at, BreakNode(breakId, kind))
          _ <- caretAfterBreak(scope, generator, block, at + 1)
        yield ()

  private def caretAfterBreak(
      scope: TransformScope,
      generator: NodeIdGenerator,
      block: NodeId,
      index: Int
  ): Either[UpdateError, Unit] =
    scope.document
      .childrenOf(block)
      .lift(index)
      .flatMap(id => scope.document.node(id).collect { case run: TextNode => run }) match
      case Some(run) => scope.select(RangeSelection.caret(Point.textBefore(run.id, 0)))
      case None      =>
        val created = generator.nextFor(scope.document)
        for
          _ <- scope.insert(block, index, TextNode(created, ""))
          _ <- scope.select(RangeSelection.caret(Point.textBefore(created, 0)))
        yield ()

  /** Inserts a thematic break as a sibling of the block at the caret.
    *
    * Block level, so it goes next to the block and not into it (see [[ThematicBreakNode]]). It is
    * inserted *after* the current block, and the caret stays where it was: the rule is a divider
    * between what was written and what comes next, and the author is still writing the first part.
    */
  def insertThematicBreak(
      scope: TransformScope,
      generator: NodeIdGenerator
  ): Either[UpdateError, Unit] =
    (for
      block     <- blockAtCaret(scope)
      container <- scope.document.parentOf(block)
      index     <- scope.document.indexOfChild(block)
    yield (container, index)) match
      case None                     => Right(())
      case Some((container, index)) =>
        scope.insert(container, index + 1, ThematicBreakNode(generator.nextFor(scope.document)))

  // -----------------------------------------------------------------------------------------
  // Shared lookups
  // -----------------------------------------------------------------------------------------

  /** The block the caret is in: the parent of the run it stands on.
    *
    * A caret on a child position is its own owner's business -- that happens in an empty block, and
    * then the block *is* the owner.
    */
  private[richtext] def blockAtCaret(scope: TransformScope): Option[NodeId] =
    caretOf(scope).flatMap {
      case Point.Text(node, _, _)       => scope.document.parentOf(node)
      case Point.Children(parent, _, _) => Some(parent)
    }

  private def caretOf(scope: TransformScope): Option[Point] =
    scope.selection.collect { case range: RangeSelection => range.focus }

  private def textPositionOf(point: Point): Option[(NodeId, Int)] = point match
    case Point.Text(node, offset, _) => Some((node, offset))
    case _                           => None

  private def textOf(document: DocumentRead, node: NodeId): String =
    document.node(node).collect { case run: TextNode => run.text }.getOrElse("")
