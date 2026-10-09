package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*

/** The rules that keep a table a table, whoever changed it.
  *
  * ==The invariants==
  *
  *   - A table holds rows, a row holds cells, a cell holds blocks.
  *   - A cell is never empty: it has at least a paragraph, so there is a caret position in it.
  *   - Every row has as many cells as the widest row. A table is a rectangle.
  *   - The first row's cells are headers exactly when the table has a header; no other cell is.
  *   - Every cell carries its column's alignment, and the table has one alignment per column.
  *   - An empty row and an empty table disappear.
  *
  * ==Why repair and not reject==
  *
  * The same reason as the list rules (§3.2): a paste, a move from a foreign module or a range
  * deletion that cleared a cell all produce these shapes, and each of them would otherwise have to
  * be careful on its own. The rules hold regardless of who caused the problem.
  *
  * ==Termination==
  *
  * Each early rule wraps exactly one misplaced child per pass and so strictly reduces the number of
  * misplaced ones. The late rules only add cells to short rows, fill empty cells, remove empty
  * containers, or copy the table's column facts to its cells -- and none of them makes another one
  * necessary again.
  */
private[table] object TableNormalization:

  /** A child of a table that is not a row gets a row around it -- and a cell, if it is not one. */
  def tableChildrenAreRows(generator: NodeIdGenerator): Transform[TableNode] =
    new Transform[TableNode]:
      val name           = "table.children-are-rows"
      val nodeType       = TableNode
      override val phase = TransformPhase.Early

      def transform(node: TableNode, scope: TransformScope): Unit =
        val document = scope.document
        node.children.zipWithIndex
          .find((child, _) => !document.node(child).exists(_.isInstanceOf[TableRowNode]))
          .foreach { (child, index) =>
            document.node(child) match
              case Some(_: TableCellNode) =>
                val rowId = generator.nextFor(document)
                for
                  _ <- scope.insert(node.id, index, TableRowNode(rowId, Vector.empty))
                  _ <- scope.move(child, rowId, 0)
                yield ()
              case _ =>
                val Vector(rowId, cellId) = generator.nextBatch(2, document.contains): @unchecked
                for
                  _ <- scope.insert(
                    node.id,
                    index,
                    TableRowNode(rowId, Vector(cellId)),
                    Vector(TableCellNode(cellId, Vector.empty))
                  )
                  _ <- scope.move(child, cellId, 0)
                yield ()
          }

  /** A child of a row that is not a cell gets a cell around it. */
  def rowChildrenAreCells(generator: NodeIdGenerator): Transform[TableRowNode] =
    new Transform[TableRowNode]:
      val name           = "table.row-children-are-cells"
      val nodeType       = TableRowNode
      override val phase = TransformPhase.Early

      def transform(node: TableRowNode, scope: TransformScope): Unit =
        val document = scope.document
        node.children.zipWithIndex
          .find((child, _) => !document.node(child).exists(_.isInstanceOf[TableCellNode]))
          .foreach { (child, index) =>
            val cellId = generator.nextFor(document)
            for
              _ <- scope.insert(node.id, index, TableCellNode(cellId, Vector.empty))
              _ <- scope.move(child, cellId, 0)
            yield ()
          }

  /** Inline content directly in a cell goes into a paragraph.
    *
    * The usual cause is a caret on a child boundary of an empty cell: typing there creates a run
    * directly in the cell. A run of consecutive inline children becomes one paragraph, so that "a",
    * a link and "b" stay one line.
    */
  def cellHoldsBlocks(generator: NodeIdGenerator): Transform[TableCellNode] =
    new Transform[TableCellNode]:
      val name           = "table.cell-holds-blocks"
      val nodeType       = TableCellNode
      override val phase = TransformPhase.Early

      def transform(node: TableCellNode, scope: TransformScope): Unit =
        val document                      = scope.document
        def isInline(id: NodeId): Boolean = document.node(id) match
          case Some(_: TextNode)          => true
          case Some(_: InlineElementNode) => true
          case Some(_: ThematicBreakNode) => false
          case Some(_: AtomNode)          => true
          case _                          => false

        val start = node.children.indexWhere(isInline)
        if start >= 0 then
          val run         = node.children.drop(start).takeWhile(isInline)
          val paragraphId = generator.nextFor(document)
          for
            _ <- scope.insert(node.id, start, ParagraphNode(paragraphId, Vector.empty))
            _ <- run.zipWithIndex.foldLeft[Either[UpdateError, Unit]](Right(())) {
              case (result, (child, offset)) =>
                result.flatMap(_ => scope.move(child, paragraphId, offset))
            }
          yield ()

  /** An empty cell gets a paragraph with an empty run. */
  def emptyCellGetsParagraph(generator: NodeIdGenerator): Transform[TableCellNode] =
    new Transform[TableCellNode]:
      val name           = "table.empty-cell-gets-paragraph"
      val nodeType       = TableCellNode
      override val phase = TransformPhase.Late

      def transform(node: TableCellNode, scope: TransformScope): Unit =
        if node.children.isEmpty then
          val Vector(paragraph, text) =
            generator.nextBatch(2, scope.document.contains): @unchecked
          scope.insert(
            node.id,
            0,
            ParagraphNode(paragraph, Vector(text)),
            Vector(TextNode(text, ""))
          ): Unit

  val emptyRowGoes: Transform[TableRowNode] = new Transform[TableRowNode]:
    val name           = "table.empty-row-goes"
    val nodeType       = TableRowNode
    override val phase = TransformPhase.Late

    def transform(node: TableRowNode, scope: TransformScope): Unit =
      if node.children.isEmpty then scope.remove(node.id): Unit

  val emptyTableGoes: Transform[TableNode] = new Transform[TableNode]:
    val name           = "table.empty-table-goes"
    val nodeType       = TableNode
    override val phase = TransformPhase.Late

    def transform(node: TableNode, scope: TransformScope): Unit =
      if node.children.isEmpty then scope.remove(node.id): Unit

  /** The rectangle and the column facts, asked from the table itself. */
  def tableIsRectangular(generator: NodeIdGenerator): Transform[TableNode] =
    new Transform[TableNode]:
      val name           = "table.rectangular"
      val nodeType       = TableNode
      override val phase = TransformPhase.Late

      def transform(node: TableNode, scope: TransformScope): Unit =
        repair(node.id, scope, generator)

  /** The same, asked from a row: moving cells between rows changes the rows, not the table (§3.4).
    */
  def rowKeepsTableRectangular(generator: NodeIdGenerator): Transform[TableRowNode] =
    new Transform[TableRowNode]:
      val name           = "table.row-keeps-rectangular"
      val nodeType       = TableRowNode
      override val phase = TransformPhase.Late

      def transform(node: TableRowNode, scope: TransformScope): Unit =
        scope.document
          .parentOf(node.id)
          .filter(id => scope.document.node(id).exists(_.isInstanceOf[TableNode]))
          .foreach(repair(_, scope, generator))

  private def repair(table: NodeId, scope: TransformScope, generator: NodeIdGenerator): Unit =
    Tables.grid(scope.document, table).foreach { grid =>
      val columns = grid.columns
      if columns > 0 then
        // Short rows first, so that the flags below see every cell. A row with no cells at all is
        // not short but gone -- `emptyRowGoes` removes it, and padding it would bring it back.
        grid.rows.zip(grid.cells).filter(_._2.nonEmpty).foreach { (row, cells) =>
          (cells.length until columns).foreach { column =>
            val cellId = generator.nextFor(scope.document)
            scope.insert(
              row.id,
              scope.document.childrenOf(row.id).length,
              TableCellNode(cellId, Vector.empty)
            ): Unit
          }
        }

        val wanted = Vector.tabulate(columns)(grid.table.alignmentOf)
        scope.document.node(table).collect { case value: TableNode => value }.foreach { current =>
          if current.alignments != wanted then
            scope.replace(table, current.copy(alignments = wanted)): Unit
        }

        Tables.grid(scope.document, table).foreach { fresh =>
          fresh.cells.zipWithIndex.foreach { (cells, rowIndex) =>
            cells.zipWithIndex.foreach { (cell, column) =>
              val header    = fresh.table.header && rowIndex == 0
              val alignment = fresh.table.alignmentOf(column)
              if cell.header != header || cell.alignment != alignment then
                scope.replace(cell.id, cell.copy(header = header, alignment = alignment)): Unit
            }
          }
        }
    }

  def all(generator: NodeIdGenerator): Vector[Transform[?]] =
    Vector(
      tableChildrenAreRows(generator),
      rowChildrenAreCells(generator),
      cellHoldsBlocks(generator),
      tableIsRectangular(generator),
      rowKeepsTableRectangular(generator),
      emptyCellGetsParagraph(generator),
      emptyRowGoes,
      emptyTableGoes
    )
