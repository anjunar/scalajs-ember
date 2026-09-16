package ember.editor.table

import ember.editor.core.*
import ember.editor.history.History
import ember.editor.richtext.*
import org.scalatest.Assertions.fail

/** A session with rich text, tables and history, and a way to read tables back as text.
  *
  * {{{
  * root
  *   p0  "Vorher"
  *   t   table (header)
  *     r0  c00 "A"   c01 "B"
  *     r1  c10 "1"   c11 "2"
  *   p1  "Nachher"
  * }}}
  */
object TableTestKit:

  val root: NodeId = NodeId("root")

  def open(withTable: Boolean = true): (EditorSession, History) =
    val generator = NodeIdGenerator.sequential("g")
    val history   = new History()
    val resolved  = ExtensionResolver
      .resolve(Vector(RichText(generator), TableExtension(generator), history))
      .getOrElse(fail("extensions"))

    def cell(id: String, text: String, header: Boolean = false): Vector[EditorNode] =
      Vector(
        TableCellNode(NodeId(id), Vector(NodeId(s"$id-p")), header),
        ParagraphNode(NodeId(s"$id-p"), Vector(NodeId(s"$id-t"))),
        TextNode(NodeId(s"$id-t"), text)
      )

    val nodes =
      if withTable then
        Vector(
          RootNode(root, Vector(NodeId("p0"), NodeId("t"), NodeId("p1"))),
          ParagraphNode(NodeId("p0"), Vector(NodeId("p0-t"))),
          TextNode(NodeId("p0-t"), "Vorher"),
          TableNode(
            NodeId("t"),
            Vector(NodeId("r0"), NodeId("r1")),
            header = true,
            Vector(ColumnAlignment.Default, ColumnAlignment.Default)
          ),
          TableRowNode(NodeId("r0"), Vector(NodeId("c00"), NodeId("c01"))),
          TableRowNode(NodeId("r1"), Vector(NodeId("c10"), NodeId("c11")))
        ) ++ cell("c00", "A", header = true) ++ cell("c01", "B", header = true) ++
          cell("c10", "1") ++ cell("c11", "2") ++ Vector(
            ParagraphNode(NodeId("p1"), Vector(NodeId("p1-t"))),
            TextNode(NodeId("p1-t"), "Nachher")
          )
      else
        Vector(
          RootNode(root, Vector(NodeId("p0"))),
          ParagraphNode(NodeId("p0"), Vector(NodeId("p0-t"))),
          TextNode(NodeId("p0-t"), "Vorher")
        )

    val session = EditorSession
      .create(
        Document.unsafe(resolved.schema, root, nodes),
        resolved,
        resolved.sessionConfig(errorSink = error => fail(error.render))
      )
      .getOrElse(fail("session"))
    (session, history)

  def caret(session: EditorSession, run: String, offset: Int): Unit =
    session.update(
      _.select(RangeSelection.caret(Point.textBefore(NodeId(run), offset))): Unit
    ) match
      case Right(_)    => ()
      case Left(error) => fail(error.render)

  def range(session: EditorSession, from: (String, Int), to: (String, Int)): Unit =
    session.update(
      _.select(
        RangeSelection(
          Point.textBefore(NodeId(from._1), from._2),
          Point.textBefore(NodeId(to._1), to._2)
        )
      ): Unit
    ) match
      case Right(_)    => ()
      case Left(error) => fail(error.render)

  def exec[A](session: EditorSession, command: EditorCommand[A], payload: A): CommandResult =
    session.dispatch(command, payload) match
      case Right(outcome) => outcome.result
      case Left(error)    => fail(error.render)

  def exec(session: EditorSession, command: EditorCommand[Unit]): CommandResult =
    exec(session, command, ())

  /** The first table as rows of cell texts: `A|B / 1|2`. Header cells are marked with `*`. */
  def table(session: EditorSession): String =
    val document = session.document
    document.inDocumentOrder.collectFirst { case table: TableNode => table } match
      case None        => "-"
      case Some(table) =>
        Tables.grid(document, table.id).fold("?") { grid =>
          grid.cells
            .map(
              _.map(cell => (if cell.header then "*" else "") + textOf(document, cell.id))
                .mkString("|")
            )
            .mkString(" / ")
        }

  /** Every top-level block as text, tables as `[table]`. */
  def blocks(session: EditorSession): String =
    val document = session.document
    document
      .childrenOf(document.rootId)
      .map { id =>
        document.node(id) match
          case Some(_: TableNode) => "[table]"
          case _                  => textOf(document, id)
      }
      .mkString(" · ")

  def textOf(document: DocumentRead, id: NodeId): String =
    document.node(id) match
      case Some(run: TextNode)        => run.text
      case Some(element: ElementNode) =>
        val parts = element.children.map(textOf(document, _))
        element match
          case _: TableCellNode => parts.mkString("¶")
          case _                => parts.mkString
      case _ => ""

  def caretText(session: EditorSession): String =
    session.selection match
      case Some(RangeSelection(anchor, focus)) if anchor == focus =>
        focus match
          case Point.Text(node, offset, _) =>
            val cell = Tables.cellOf(session.document, node).map(_.value).getOrElse("-")
            s"$cell@$offset"
          case other => other.toString
      case Some(cells: TableSelection) => s"cells(${cells.anchor.value}..${cells.focus.value})"
      case other                       => other.toString
