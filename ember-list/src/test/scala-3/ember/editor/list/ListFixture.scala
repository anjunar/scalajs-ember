package ember.editor.list

import ember.editor.core.*
import ember.editor.richtext.*

/** A session with the rich-text profile and lists, on plain paragraphs.
  *
  * Every test starts from paragraphs and builds its lists with the commands. Constructing a list
  * by hand would test the assertions against a structure the commands never produce.
  */
final class ListFixture(paragraphs: String*):

  val generator: NodeIdGenerator = NodeIdGenerator.sequential("n")

  private val resolved: ResolvedExtensions =
    ExtensionResolver.resolve(Vector(RichText(generator), ListExtension(generator))) match
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

  def caret: Option[(String, Int)] =
    session.selection
      .collect { case range: RangeSelection if range.isCollapsed => range.focus }
      .collect { case Point.Text(node, offset, _) => (node.value, offset) }

  /** The document as an indented outline -- the shape a list test is really about.
    *
    * `ol`/`ul` for lists, `li` for items, the text for anything with text in it. Comparing this
    * against a literal is far more readable than walking ids, and it fails with a picture of
    * what actually happened.
    */
  def outline: Vector[String] =
    def walk(id: NodeId, depth: Int): Vector[String] =
      val indent = "  " * depth
      document.node(id) match
        case Some(list: ListNode) =>
          val tag   = if list.kind == ListKind.Ordered then "ol" else "ul"
          val start = if list.start != 1 then s"(${list.start})" else ""
          s"$indent$tag$start" +: list.children.flatMap(walk(_, depth + 1))
        case Some(item: ListItemNode) =>
          s"${indent}li" +: item.children.flatMap(walk(_, depth + 1))
        case Some(paragraph: ParagraphNode) =>
          Vector(s"$indent\"${textOf(id)}\"")
        case Some(element: ElementNode) =>
          s"$indent?" +: element.children.flatMap(walk(_, depth + 1))
        case _ => Vector.empty

    document.childrenOf(root).flatMap(walk(_, 0))

  def textOf(id: NodeId): String = document.node(id) match
    case Some(run: TextNode)        => run.text
    case Some(element: ElementNode) => element.children.map(textOf).mkString
    case _                          => ""

  /** The first list in the document, for tests that need to look at its fields. */
  def firstList: Option[ListNode] =
    document.inDocumentOrder.collectFirst { case list: ListNode => list }

  // -----------------------------------------------------------------------------------------
  // Changing
  // -----------------------------------------------------------------------------------------

  def caretAt(node: String, offset: Int): Unit =
    edit(_.select(RangeSelection.caret(Point.textBefore(NodeId(node), offset))): Unit)

  /** Puts the caret at the start of the block whose text is `text`. */
  def caretIn(text: String, offset: Int = 0): Unit =
    val run = document.inDocumentOrder
      .collectFirst { case node: TextNode if node.text == text => node }
      .getOrElse(throw new AssertionError(s"kein Lauf `$text`:\n${outline.mkString("\n")}"))
    caretAt(run.id.value, offset)

  def edit(body: Transaction => Unit): Unit =
    session.update(body) match
      case Right(_)    => ()
      case Left(error) => throw new AssertionError(s"abgewiesen: ${error.render}")

  def dispatch[A](command: EditorCommand[A], payload: A): Boolean =
    session.dispatch(command, payload) match
      case Right(outcome) => outcome.wasHandled
      case Left(error)    => throw new AssertionError(s"abgewiesen: ${error.render}")

  def dispatch(command: EditorCommand[Unit]): Boolean = dispatch(command, ())

  def bullets(): Boolean  = dispatch(ListCommands.ToggleList, ListKind.Unordered)
  def numbers(): Boolean  = dispatch(ListCommands.ToggleList, ListKind.Ordered)
  def indent(): Boolean   = dispatch(ListCommands.Indent)
  def outdent(): Boolean = dispatch(ListCommands.Outdent)
  def enter(): Boolean    = dispatch(RichText.InsertParagraph)
  def backspace(): Boolean = dispatch(RichText.DeleteBackward)
  def typeText(text: String): Boolean = dispatch(RichText.InsertText, text)
