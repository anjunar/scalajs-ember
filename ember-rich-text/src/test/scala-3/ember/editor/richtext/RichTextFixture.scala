package ember.editor.richtext

import ember.editor.core.*

/** A session with the rich-text profile installed, on a document the test describes.
  *
  * The generator is sequential, so new IDs are predictable and a test can name them.
  */
final class RichTextFixture(paragraphs: String*):

  val generator: NodeIdGenerator = NodeIdGenerator.sequential("n")

  val resolved: ResolvedExtensions =
    ExtensionResolver.resolve(Vector(RichText(generator))) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

  val root: NodeId = NodeId("root")

  private val blocks =
    (if paragraphs.isEmpty then Vector("") else paragraphs.toVector).zipWithIndex

  val session: EditorSession =
    EditorSession.create(
      Document.unsafe(
        resolved.schema,
        root,
        RootNode(root, blocks.map((_, index) => NodeId(s"p$index"))) +:
          blocks.flatMap { (content, index) =>
            Vector(
              ParagraphNode(NodeId(s"p$index"), Vector(NodeId(s"t$index"))),
              TextNode(NodeId(s"t$index"), content)
            )
          }
      ),
      resolved,
      resolved.sessionConfig()
    ) match
      case Right(value) => value
      case Left(errors) => throw new IllegalStateException(errors.map(_.render).mkString("; "))

  // -----------------------------------------------------------------------------------------
  // Reading
  // -----------------------------------------------------------------------------------------

  def document: Document = session.document

  def node(id: String): Option[EditorNode] = document.node(NodeId(id))

  /** Every text run of a block, in document order. */
  def runs(block: String = "p0"): Vector[TextNode] =
    document
      .childrenOf(NodeId(block))
      .flatMap(id => document.node(id).collect { case run: TextNode => run })

  /** The runs of a block as `text` plus sorted mark names -- what a normalisation test compares. */
  def shape(block: String = "p0"): Vector[(String, Vector[String])] =
    runs(block).map(run => (run.text, run.marks.markIds.map(_.value.split("/").head.split('.').last)))

  def textOf(block: String = "p0"): String = runs(block).map(_.text).mkString

  def caret: Option[(String, Int)] =
    session.selection
      .collect { case range: RangeSelection if range.isCollapsed => range.focus }
      .collect { case Point.Text(node, offset, _) => (node.value, offset) }

  def typingMarks: TypingMarksValue = session.state.fields(TypingMarks)

  /** The text the selection covers, across runs.
    *
    * The endpoints are sorted by document order, not by which is anchor and which is focus --
    * a backward selection covers the same characters as a forward one (§11).
    */
  def selectedText: String =
    session.selection match
      case Some(range: RangeSelection) =>
        val order = document.subtreeOf(document.rootId).toVector
        val ends = Vector(range.anchor, range.focus).collect {
          case Point.Text(node, offset, _) => (node, offset)
        }
        if ends.length != 2 then ""
        else
          val sorted                   = ends.sortBy((node, offset) => (order.indexOf(node), offset))
          val (startNode, startOffset) = sorted.head
          val (endNode, endOffset)     = sorted.last
          RangeFormatting.runsIn(document, range).map { run =>
            val from = if run.id == startNode then startOffset else 0
            val to   = if run.id == endNode then endOffset else run.text.length
            run.text.substring(from, to)
          }.mkString
      case _ => ""

  // -----------------------------------------------------------------------------------------
  // Changing
  // -----------------------------------------------------------------------------------------

  def caretAt(node: String, offset: Int): Unit =
    edit(_.select(RangeSelection.caret(Point.textBefore(NodeId(node), offset))): Unit)

  /** A range from one text position to another. Direction is preserved -- §11. */
  def selectRange(from: (String, Int), to: (String, Int)): Unit =
    edit(
      _.select(
        RangeSelection(
          Point.textBefore(NodeId(from._1), from._2),
          Point.textBefore(NodeId(to._1), to._2)
        )
      ): Unit
    )

  def edit(body: Transaction => Unit): Unit =
    session.update(body) match
      case Right(_)    => ()
      case Left(error) => throw new AssertionError(s"abgewiesen: ${error.render}")

  def dispatch[A](command: EditorCommand[A], payload: A): Boolean =
    session.dispatch(command, payload) match
      case Right(outcome) => outcome.wasHandled
      case Left(error)    => throw new AssertionError(s"abgewiesen: ${error.render}")

  def dispatch(command: EditorCommand[Unit]): Boolean = dispatch(command, ())

  def toggle(mark: TextMark): Boolean = dispatch(RichText.ToggleMark, mark)

  /** Selects a whole run by its text. IDs of split runs come from the generator and are not
    * worth guessing in a test -- what the test means is "the bold part".
    */
  def selectRun(text: String, block: String = "p0"): Unit =
    val run = runs(block).find(_.text == text).getOrElse(
      throw new AssertionError(s"kein Lauf `$text` in $block: ${shape(block)}")
    )
    selectRange((run.id.value, 0), (run.id.value, run.text.length))

  /** Toggles a mark over a whole run, found by its text. */
  def toggleRun(text: String, mark: TextMark, block: String = "p0"): Unit =
    selectRun(text, block)
    toggle(mark): Unit

  def typeText(text: String): Boolean = dispatch(RichText.InsertText, text)
