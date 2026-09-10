package ember.editor.link

import ember.editor.core.*
import ember.editor.richtext.*

/** The commands a link contributes. Values, not names (§12). */
object LinkCommands:

  /** Links the selection, or retargets the link the caret stands in. */
  val SetLink: EditorCommand[LinkTarget] = EditorCommand.of[LinkTarget]("link.set")

  /** Takes the link away and keeps its content. */
  val RemoveLink: EditorCommand[Unit] = EditorCommand.unit("link.remove")

/** Putting links around content and taking them off again.
  *
  * ==Everything is a move, again==
  *
  * The same reason as in `ember-list`: §11 says a move keeps the node, so every point inside
  * the linked stretch survives. Linking a word must not disturb a caret sitting in it, and
  * unlinking must not touch its marks -- P14's test list asks for exactly that ("Unlink erhaelt
  * Marks/Text").
  *
  * It falls out of the model rather than needing care: marks live on the runs, the link wraps
  * the runs, and neither operation reads the other's data.
  */
object LinkEditing:

  /** Links the selection, or retargets an existing link.
    *
    * Three cases, and they are genuinely different:
    *
    *   - '''A caret inside a link.''' Nothing is selected, so nothing can be wrapped -- the
    *     author means the link they are standing in. Its target changes.
    *   - '''A caret anywhere else.''' There is nothing to link. Some editors insert the URL as
    *     text here; that is a decision for a UI, not for the model, and doing it silently would
    *     put text into the document that the author did not type.
    *   - '''A range.''' The covered runs are cut at both ends and wrapped.
    */
  def setLink(
      scope: TransformScope,
      generator: NodeIdGenerator,
      target: LinkTarget
  ): CommandResult =
    scope.selection match
      case Some(range: RangeSelection) if range.isCollapsed =>
        Links.linkAt(scope.document, range.focus) match
          case Some(link) =>
            scope.replace(link.id, link.copy(target = target)): Unit
            CommandResult.Handled
          case None => CommandResult.Pass

      case Some(range: RangeSelection) =>
        wrap(scope, generator, range, target)
        CommandResult.Handled

      case _ => CommandResult.Pass

  /** Cuts the runs at the range's ends and puts a link around what is between.
    *
    * The cutting is [[RangeFormatting]]'s job -- it already splits a run at a boundary, handles
    * a backward range and leaves the selection mapped afterwards. Calling it with a rewrite
    * that changes nothing uses exactly that and nothing else: the marks come out as they went
    * in, and what this function gets back is a selection whose ends are run boundaries.
    */
  private def wrap(
      scope: TransformScope,
      generator: NodeIdGenerator,
      range: RangeSelection,
      target: LinkTarget
  ): Unit =
    RangeFormatting.applyToRange(scope, generator, range, identity): Unit

    val covered = scope.selection match
      case Some(mapped: RangeSelection) => RangeFormatting.runsIn(scope.document, mapped)
      case _                            => Vector.empty

    // Runs in different blocks cannot share one link -- a link is inline (§8.2), and a node has
    // one parent. Each block gets its own.
    covered
      .groupBy(run => scope.document.parentOf(run.id))
      .toVector
      .flatMap((parent, runs) => parent.map(_ -> runs))
      .sortBy((parent, _) => scope.document.indexOfChild(parent).getOrElse(0))
      .foreach((parent, runs) => wrapInside(scope, generator, parent, runs, target))

  private def wrapInside(
      scope: TransformScope,
      generator: NodeIdGenerator,
      parent: NodeId,
      runs: Vector[TextNode],
      target: LinkTarget
  ): Unit =
    val document = scope.document
    val ordered  = runs.sortBy(run => document.indexOfChild(run.id).getOrElse(0))

    ordered.headOption.flatMap(run => document.indexOfChild(run.id)).foreach { at =>
      // Already inside a link with the same target: nothing to do, and wrapping would nest.
      if !document.parentOf(parent).exists(isLink(document, _)) && !isLink(document, parent) then
        val linkId = generator.nextFor(document)
        scope.insert(parent, at, LinkNode.empty(linkId, target)): Unit
        ordered.zipWithIndex.foreach { (run, offset) =>
          scope.move(run.id, linkId, offset): Unit
        }
      else
        // The runs sit in a link already. Retarget it rather than building a second one.
        document
          .node(parent)
          .collect { case link: LinkNode => link }
          .foreach(link => scope.replace(link.id, link.copy(target = target)): Unit)
    }

  private def isLink(document: DocumentRead, node: NodeId): Boolean =
    document.node(node).exists(_.isInstanceOf[LinkNode])

  /** Takes the link at the caret away and leaves its content where it was.
    *
    * The runs keep their ids, their text and their marks -- they are moved, not rebuilt. Once
    * they sit next to their former neighbours, the rich-text normalisation from P12 merges what
    * matches: unlinking a word in the middle of a sentence leaves one run again, not three.
    */
  def removeLink(scope: TransformScope): CommandResult =
    scope.selection
      .collect { case range: RangeSelection => range.focus }
      .flatMap(Links.linkAt(scope.document, _)) match
      case None => CommandResult.Pass
      case Some(link) =>
        Links.unwrap(scope, link)
        CommandResult.Handled
