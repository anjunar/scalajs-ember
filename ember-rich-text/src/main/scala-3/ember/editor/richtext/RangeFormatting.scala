package ember.editor.richtext

import ember.editor.core.*

/** Applying marks to a range of text.
  *
  * ==Marks are node data, not a DOM command==
  *
  * §12, acceptance: "Formatierung wird durch Nodes/Marks bestimmt; kein Browser-execCommand."
  * Everything here rewrites [[TextNode]]s through the primitive operations from §10. Nothing
  * reads or writes a DOM; the projection finds out through the change set like every other edit.
  *
  * ==The shape of the work==
  *
  * A range rarely lines up with run boundaries. `"Hallo Welt!"` with `"Welt"` selected is one
  * run and three pieces, so the first thing to do is cut: split at the range's start, split at
  * its end, and only then set marks on the runs in between. §8.2 spells out the result:
  *
  * {{{
  * Ausgang:              Text("Hallo Welt!", {})
  * "Welt" fett:          Text("Hallo ", {}), Text("Welt", {Strong}), Text("!", {})
  * Fett wieder entfernt: Text("Hallo Welt!", {})
  * }}}
  *
  * The last line is not this file's doing -- [[TextRunNormalization]] puts the pieces back
  * together, in the same transaction, so removing a format costs no extra undo step.
  *
  * ==Toggle means "all or nothing"==
  *
  * A toggle over a range where some runs are bold and some are not turns everything bold. The
  * alternative -- inverting each run separately -- makes a second toggle a no-op for the user:
  * press it twice and the selection looks exactly as it did, only with the pieces swapped. Every
  * editor worth the name resolves this the same way, and §8.2 leaves the choice to the profile.
  */
object RangeFormatting:

  /** Toggles a mark across the current selection.
    *
    * At a collapsed caret this changes no text at all -- it records the wish in [[TypingMarks]],
    * and §11 requires exactly that: "Ein Toggle am kollabierten Caret aendert dieses Feld, ohne
    * Text zu erzeugen. Die naechste Eingabe verwendet diese Marks."
    */
  def toggleMark(
      scope: TransformScope,
      generator: NodeIdGenerator,
      mark: TextMark
  ): Either[UpdateError, Unit] =
    scope.selection match
      case Some(range: RangeSelection) if range.isCollapsed => toggleAtCaret(scope, range, mark)
      case Some(range: RangeSelection)                      => toggleRange(scope, generator, range, mark)
      case _                                                => Right(())

  private def toggleAtCaret(
      scope: TransformScope,
      range: RangeSelection,
      mark: TextMark
  ): Either[UpdateError, Unit] =
    val current = TypingMarks.effective(scope)
    scope.setField(
      TypingMarks,
      TypingMarksValue.Explicit(StandardMarks.toggle(current, mark), range.focus)
    )
    Right(())

  private def toggleRange(
      scope: TransformScope,
      generator: NodeIdGenerator,
      range: RangeSelection,
      mark: TextMark
  ): Either[UpdateError, Unit] =
    // Whether this adds or removes is decided *before* any splitting: afterwards the runs are
    // different nodes, and asking them would be asking about the result of our own work.
    val covered = runsIn(scope.document, range)
    val removing = covered.nonEmpty && covered.forall(_.marks.contains(mark.markId))

    applyToRange(scope, generator, range, marks =>
      if removing then marks - mark.markId else StandardMarks.add(marks, mark)
    )

  /** Rewrites the marks of every run the range touches.
    *
    * Runs are cut at both ends first, then rewritten. The order matters: cutting invalidates the
    * offsets of everything to its right, so the end is cut before the start.
    */
  def applyToRange(
      scope: TransformScope,
      generator: NodeIdGenerator,
      range: RangeSelection,
      rewrite: MarkSet => MarkSet
  ): Either[UpdateError, Unit] =
    boundsOf(scope.document, range) match
      case None => Right(())
      case Some(bounds) =>
        for
          // The end first: cutting invalidates every offset to its right, and the start is to
          // the left of the end by construction.
          _ <- splitAt(scope, generator, bounds.endNode, bounds.endOffset)
          _ <- splitAt(scope, generator, bounds.startNode, bounds.startOffset)
          // The range that came in no longer describes the same text -- `t0` is a shorter run
          // now, and its offset 10 means nothing. The transaction has carried the selection
          // through both splits (§10, step 3), so the mapped one is read back rather than
          // remembered. Getting this wrong marks the run *before* the selection.
          _ <- rewriteRuns(scope, rewrite)
        yield ()

  private def rewriteRuns(
      scope: TransformScope,
      rewrite: MarkSet => MarkSet
  ): Either[UpdateError, Unit] =
    scope.selection match
      case Some(range: RangeSelection) =>
        runsIn(scope.document, range).foldLeft[Either[UpdateError, Unit]](Right(())) {
          (accumulated, run) =>
            accumulated.flatMap { _ =>
              val marks = rewrite(run.marks)
              if marks == run.marks then Right(())
              else scope.replace(run.id, run.copy(marks = marks))
            }
        }
      case _ => Right(())

  /** Splits a run at `offset`, unless the offset is already a boundary. */
  private def splitAt(
      scope: TransformScope,
      generator: NodeIdGenerator,
      node: NodeId,
      offset: Int
  ): Either[UpdateError, Unit] =
    scope.document.node(node).collect { case run: TextNode => run } match
      case Some(run) if offset > 0 && offset < run.text.length =>
        scope.splitText(node, offset, generator.nextFor(scope.document))
      case _ => Right(())

  // -----------------------------------------------------------------------------------------
  // Where a range starts and ends
  // -----------------------------------------------------------------------------------------

  private final case class Bounds(
      startNode: NodeId,
      startOffset: Int,
      endNode: NodeId,
      endOffset: Int
  )

  /** The range's endpoints in document order.
    *
    * §11: "Anchor/Focus werden niemals nur zugunsten sortierter Endpunkte ueberschrieben.
    * Vorwaerts/rueckwaerts ergibt sich aus der aktuellen Dokumentordnung." So the selection keeps
    * its direction and this function sorts a local copy -- a backward selection formats the same
    * text as a forward one.
    */
  private def boundsOf(document: DocumentRead, range: RangeSelection): Option[Bounds] =
    for
      anchor <- textPointOf(range.anchor)
      focus  <- textPointOf(range.focus)
      order = runOrder(document)
      (start, end) =
        if precedes(order, anchor, focus) then (anchor, focus) else (focus, anchor)
    yield Bounds(start._1, start._2, end._1, end._2)

  private def textPointOf(point: Point): Option[(NodeId, Int)] = point match
    case Point.Text(node, offset, _) => Some((node, offset))
    case _                           => None

  private def precedes(
      order: Vector[NodeId],
      left: (NodeId, Int),
      right: (NodeId, Int)
  ): Boolean =
    val leftIndex  = order.indexOf(left._1)
    val rightIndex = order.indexOf(right._1)
    if leftIndex == rightIndex then left._2 <= right._2 else leftIndex < rightIndex

  /** Every text run in document order. The order §11 means -- tree order, never ID order. */
  private def runOrder(document: DocumentRead): Vector[NodeId] =
    document
      .subtreeOf(document.rootId)
      .filter(id => document.node(id).exists(_.isInstanceOf[TextNode]))
      .toVector

  /** The runs the range covers, in document order, including partially covered ones.
    *
    * A run at an endpoint counts only if the range actually contains some of its text: a caret
    * sitting at the end of a run selects nothing of it.
    */
  def runsIn(document: DocumentRead, range: RangeSelection): Vector[TextNode] =
    boundsOf(document, range) match
      case None => Vector.empty
      case Some(bounds) =>
        val order = runOrder(document)
        val from  = order.indexOf(bounds.startNode)
        val to    = order.indexOf(bounds.endNode)
        if from < 0 || to < 0 then Vector.empty
        else
          order
            .slice(from, to + 1)
            .flatMap(id => document.node(id).collect { case run: TextNode => run })
            .filter(run => coversSomeOf(bounds, run, order))

  private def coversSomeOf(bounds: Bounds, run: TextNode, order: Vector[NodeId]): Boolean =
    val start = if run.id == bounds.startNode then bounds.startOffset else 0
    val end   = if run.id == bounds.endNode then bounds.endOffset else run.text.length
    end > start

  // -----------------------------------------------------------------------------------------
  // Reading
  // -----------------------------------------------------------------------------------------

  /** The marks every covered run has. What a toolbar shows as "active".
    *
    * ==Why it reads a state and not a draft==
    *
    * Because a toolbar has one. `EditorState` is readable outside every update closure (§9),
    * and asking for a running transaction would force every consumer to open one just to find
    * out whether the bold button should look pressed.
    *
    * At a collapsed caret the answer is [[TypingMarks]]', not the run's -- otherwise the button
    * would keep showing the old state after a toggle that has not been typed into yet.
    */
  def activeMarks(state: EditorState): MarkSet =
    state.selection match
      case Some(range: RangeSelection) if range.isCollapsed =>
        TypingMarks.marksFor(state.fields(TypingMarks), state.document, Some(range.focus))
      case Some(range: RangeSelection) =>
        runsIn(state.document, range) match
          case runs if runs.isEmpty => MarkSet.empty
          case runs =>
            runs.map(_.marks).reduce((left, right) =>
              MarkSet.from(left.marks.filter(mark => right.contains(mark.markId)))
            )
      case _ => MarkSet.empty
