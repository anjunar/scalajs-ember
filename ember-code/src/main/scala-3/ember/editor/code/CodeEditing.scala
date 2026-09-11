package ember.editor.code

import ember.editor.core.*
import ember.editor.richtext.*

/** The commands a code block contributes. */
object CodeCommands:

  /** Turns the block at the caret into a code block, or back into paragraphs. */
  val ToggleCodeBlock: EditorCommand[CodeInfo] = EditorCommand.of[CodeInfo]("code.toggle")

  /** Sets the info string of the code block at the caret. */
  val SetCodeInfo: EditorCommand[CodeInfo] = EditorCommand.of[CodeInfo]("code.set-info")

  /** Indents the line at the caret by one unit. */
  val IndentLine: EditorCommand[Unit] = EditorCommand.unit("code.indent-line")

  /** Removes one unit of indentation from the line at the caret. */
  val OutdentLine: EditorCommand[Unit] = EditorCommand.unit("code.outdent-line")

/** Turning blocks into code and editing inside one. */
object CodeEditing:

  /** What one press of the indent command inserts. Two spaces, and not a tab.
    *
    * A tab renders at whatever width the reader's viewer chooses, which is the one thing code
    * indentation must not do. Two spaces is a choice, not a law -- but it has to be *a* choice, and
    * one that round-trips through Markdown unchanged.
    */
  val indentUnit: String = "  "

  // -----------------------------------------------------------------------------------------
  // Converting
  // -----------------------------------------------------------------------------------------

  /** Turns the block at the caret into a code block, or a code block back into paragraphs. */
  def toggle(
      scope: TransformScope,
      generator: NodeIdGenerator,
      info: CodeInfo
  ): CommandResult =
    blockAtCaret(scope) match
      case None        => CommandResult.Pass
      case Some(block) =>
        scope.document.node(block) match
          case Some(code: CodeBlockNode) =>
            toParagraphs(scope, generator, code)
            CommandResult.Handled
          case Some(element: ElementNode) =>
            scope.replace(block, CodeBlockNode(block, element.children, info)): Unit
            CommandResult.Handled
          case _ => CommandResult.Pass

  /** Sets the info string of the code block at the caret. */
  def setInfo(scope: TransformScope, info: CodeInfo): CommandResult =
    blockAtCaret(scope)
      .flatMap(scope.document.node)
      .collect { case code: CodeBlockNode => code } match
      case Some(code) =>
        scope.replace(code.id, code.copy(info = info)): Unit
        CommandResult.Handled
      case None => CommandResult.Pass

  /** One paragraph per line.
    *
    * ==Why not one paragraph with breaks==
    *
    * Because a newline inside a paragraph's run is a document no renderer shows correctly: HTML
    * collapses it to a space, and the content would silently change meaning on its way out. The
    * alternative would be hard breaks (§8.2), which is defensible -- but lines of code are lines,
    * and a reader who converts a listing back to prose expects paragraphs, not one paragraph
    * pretending to be several.
    *
    * A block that holds one line converts to one paragraph, which is the common case and the exact
    * inverse of turning that paragraph into code.
    */
  private def toParagraphs(
      scope: TransformScope,
      generator: NodeIdGenerator,
      code: CodeBlockNode
  ): Unit =
    val lines = CodeBlockNode.textOf(code, scope.document).split("\n", -1).toVector

    // The block itself becomes the first paragraph -- keeping its id keeps every bookmark that
    // pointed at it, and `Replace` preserves the children (§10).
    scope.replace(code.id, ParagraphNode(code.id, code.children)): Unit

    code.children.headOption.foreach { run =>
      scope.document.node(run).collect { case value: TextNode => value }.foreach { existing =>
        if lines.headOption.contains(existing.text) then ()
        else scope.spliceText(run, 0, existing.text.length, lines.headOption.getOrElse("")): Unit
      }
    }

    (for
      container <- scope.document.parentOf(code.id)
      at        <- scope.document.indexOfChild(code.id)
    yield (container, at)).foreach { (container, at) =>
      lines.tail.zipWithIndex.foreach { (line, offset) =>
        val paragraph = generator.nextFor(scope.document)
        val text      = generator.nextFor(scope.document)
        scope.insert(
          container,
          at + offset + 1,
          ParagraphNode(paragraph, Vector(text)),
          Vector(TextNode(text, line))
        ): Unit
      }
    }

  // -----------------------------------------------------------------------------------------
  // Editing inside
  // -----------------------------------------------------------------------------------------

  /** Enter inside a code block.
    *
    * ==A newline, not a new block==
    *
    * Inside code, Enter means what it means in a text editor. Splitting the block would turn one
    * listing into two, which is never what the author wanted -- and §8.2 lets the content carry
    * newlines precisely so that it does not have to.
    *
    * ==Except when it means "let me out"==
    *
    * Pressing Enter on an empty last line leaves the block. That convention exists in every editor
    * that has code blocks, and for a good reason: a code block is a container with no edge a caret
    * can step over, so without it there is no way back out by typing.
    *
    * The trailing newline that led here is removed on the way -- it was the author's request to
    * leave, not part of their code.
    */
  def insertParagraph(
      scope: TransformScope,
      generator: NodeIdGenerator
  ): CommandResult =
    caretInCode(scope) match
      case None                      => CommandResult.Pass
      case Some((code, run, offset)) =>
        val text = textOf(scope.document, run)

        if leavesBlock(text, offset) then
          exit(scope, generator, code, run, offset)
          CommandResult.Handled
        else
          scope.spliceText(run, offset, 0, "\n"): Unit
          scope.select(RangeSelection.caret(Point.textBefore(run, offset + 1))): Unit
          CommandResult.Handled

  /** At the end of the text, on a line that is empty. */
  private def leavesBlock(text: String, offset: Int): Boolean =
    offset == text.length && text.endsWith("\n")

  private def exit(
      scope: TransformScope,
      generator: NodeIdGenerator,
      code: CodeBlockNode,
      run: NodeId,
      offset: Int
  ): Unit =
    scope.spliceText(run, offset - 1, 1, ""): Unit

    (for
      container <- scope.document.parentOf(code.id)
      at        <- scope.document.indexOfChild(code.id)
    yield (container, at)).foreach { (container, at) =>
      val paragraph = generator.nextFor(scope.document)
      val text      = generator.nextFor(scope.document)
      scope.insert(
        container,
        at + 1,
        ParagraphNode(paragraph, Vector(text)),
        Vector(TextNode(text, ""))
      ): Unit
      scope.select(RangeSelection.caret(Point.textBefore(text, 0))): Unit
    }

  /** Adds one unit of indentation at the start of the caret's line. */
  def indentLine(scope: TransformScope): CommandResult =
    caretInCode(scope) match
      case None                   => CommandResult.Pass
      case Some((_, run, offset)) =>
        val start = lineStart(textOf(scope.document, run), offset)
        scope.spliceText(run, start, 0, indentUnit): Unit
        scope.select(RangeSelection.caret(Point.textBefore(run, offset + indentUnit.length))): Unit
        CommandResult.Handled

  /** Removes one unit of indentation, or as much of it as is there.
    *
    * As much as is there, not all whitespace: a line indented by three spaces loses two, not three.
    * Removing everything would make the command unable to undo a single press of its counterpart.
    */
  def outdentLine(scope: TransformScope): CommandResult =
    caretInCode(scope) match
      case None                   => CommandResult.Pass
      case Some((_, run, offset)) =>
        val text  = textOf(scope.document, run)
        val start = lineStart(text, offset)
        val width = text.drop(start).takeWhile(_ == ' ').length.min(indentUnit.length)

        if width == 0 then CommandResult.Handled
        else
          scope.spliceText(run, start, width, ""): Unit
          scope.select(
            RangeSelection.caret(Point.textBefore(run, math.max(start, offset - width)))
          ): Unit
          CommandResult.Handled

  private def lineStart(text: String, offset: Int): Int =
    text.lastIndexOf('\n', math.max(offset - 1, 0)) + 1

  // -----------------------------------------------------------------------------------------
  // Shared lookups
  // -----------------------------------------------------------------------------------------

  /** The code block, its run and the caret offset -- or `None` outside a code block. */
  private def caretInCode(scope: TransformScope): Option[(CodeBlockNode, NodeId, Int)] =
    for
      point         <- caretOf(scope)
      (run, offset) <- textPositionOf(point)
      block         <- scope.document.parentOf(run)
      code          <- scope.document.node(block).collect { case value: CodeBlockNode => value }
    yield (code, run, offset)

  private[code] def blockAtCaret(scope: TransformScope): Option[NodeId] =
    caretOf(scope).flatMap {
      case Point.Text(node, _, _)       => scope.document.parentOf(node)
      case Point.Children(parent, _, _) => Some(parent)
    }

  private def caretOf(scope: TransformScope): Option[Point] =
    scope.selection.collect { case range: RangeSelection if range.isCollapsed => range.focus }

  private def textPositionOf(point: Point): Option[(NodeId, Int)] = point match
    case Point.Text(node, offset, _) => Some((node, offset))
    case _                           => None

  private def textOf(document: DocumentRead, run: NodeId): String =
    document.node(run).collect { case value: TextNode => value.text }.getOrElse("")
