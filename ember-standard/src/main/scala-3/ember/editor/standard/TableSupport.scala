package ember.editor.standard

import ember.editor.core.*
import ember.editor.html.*
import ember.editor.json.*
import ember.editor.markdown.*
import ember.editor.richtext.*
import ember.editor.table.*
import ember.editor.ui.*

/** Tables in every format (X01).
  *
  * ==Separately chosen, like everything else==
  *
  * None of the bundles an application already uses -- [[ImageSupport.everything]],
  * `StandardJsonSupport.everything`, `MarkdownSupports.everything`, `StandardHtmlImport.everything`
  * -- includes tables. X01's acceptance says why: "normale Editoren ziehen das Modul nicht herein."
  * An application that wants tables adds the values below to what it has, and one that does not
  * links none of `ember-table`.
  */
object TableSupport:

  // -----------------------------------------------------------------------------------------
  // HTML
  // -----------------------------------------------------------------------------------------

  /** `table` with an inner `tbody`.
    *
    * No `thead`: a semantic description sees one node, and splitting the rows between two wrappers
    * would need the table to render its children. The first row's cells are `th`, which is what
    * gives a screen reader the column headers -- `thead` adds nothing it needs.
    */
  val table: HtmlSemantics[TableNode] = new HtmlSemantics[TableNode]:
    val nodeType: NodeType[TableNode] = TableNode

    def shapeOf(node: TableNode, profile: RenderProfile): HtmlShape =
      HtmlShape.Element("table", Identity.of(node.id, profile), Vector("tbody"))

  val row: HtmlSemantics[TableRowNode] = new HtmlSemantics[TableRowNode]:
    val nodeType: NodeType[TableRowNode] = TableRowNode

    def shapeOf(node: TableRowNode, profile: RenderProfile): HtmlShape =
      HtmlShape.Element("tr", Identity.of(node.id, profile))

  /** `th` or `td`, with `align` when the column has one. */
  val cell: HtmlSemantics[TableCellNode] = new HtmlSemantics[TableCellNode]:
    val nodeType: NodeType[TableCellNode] = TableCellNode

    def shapeOf(node: TableCellNode, profile: RenderProfile): HtmlShape =
      val align = node.alignment match
        case ColumnAlignment.Default => Vector.empty
        case other                   => Vector(HtmlAttribute("align", other.toString.toLowerCase))
      HtmlShape.Element(if node.header then "th" else "td", Identity.of(node.id, profile) ++ align)

  val semantics: HtmlSupport = HtmlSupport.of(table, row, cell)

  /** Every standard adapter, tables included. */
  val everything: HtmlSupport = ImageSupport.everything ++ semantics

  val views: ViewSupport = ViewSupport.semantic(everything)

  // -----------------------------------------------------------------------------------------
  // JSON
  // -----------------------------------------------------------------------------------------

  private def alignmentName(alignment: ColumnAlignment): String = alignment.toString.toLowerCase

  private def alignmentOf(raw: String, at: DiagnosticPath): Either[DecodeError, ColumnAlignment] =
    ColumnAlignment.values
      .find(_.toString.equalsIgnoreCase(raw))
      .toRight(DecodeError.InvalidValue(s"`$raw` ist keine Spaltenausrichtung.", at))

  private def flag(
      payload: JsonValue.Obj,
      name: String,
      at: DiagnosticPath
  ): Either[DecodeError, Boolean] =
    payload.get(name) match
      case None | Some(JsonValue.Null) => Right(false)
      case Some(JsonValue.Bool(value)) => Right(value)
      case Some(other)                 =>
        Left(DecodeError.TypeMismatch("boolean", other.getClass.getSimpleName, at.field(name)))

  val tableCodec: NodeJsonCodec[TableNode] = new NodeJsonCodec[TableNode]:
    val nodeType: NodeType[TableNode] = TableNode

    def encode(
        node: TableNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] =
      Right(
        Vector(
          "header"     -> JsonValue.Bool(node.header),
          "alignments" -> JsonValue.Arr(
            node.alignments.map(value => JsonValue.Str(alignmentName(value)))
          )
        )
      )

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, TableNode] =
      for
        header <- flag(payload, "header", context.path)
        raw    <- payload.get("alignments") match
          case None | Some(JsonValue.Null) => Right(Vector.empty)
          case Some(_)                     => payload.array("alignments", context.path)
        alignments <- raw.zipWithIndex.foldLeft[Either[DecodeError, Vector[ColumnAlignment]]](
          Right(Vector.empty)
        ) { case (result, (value, index)) =>
          val at = context.path.field("alignments").field(index.toString)
          result.flatMap { collected =>
            value match
              case JsonValue.Str(name) => alignmentOf(name, at).map(collected :+ _)
              case other               =>
                Left(DecodeError.TypeMismatch("string", other.getClass.getSimpleName, at))
          }
        }
      yield TableNode(id, Vector.empty, header, alignments)

  val rowCodec: NodeJsonCodec[TableRowNode] = new NodeJsonCodec[TableRowNode]:
    val nodeType: NodeType[TableRowNode] = TableRowNode

    def encode(
        node: TableRowNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] = Right(Vector.empty)

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, TableRowNode] = Right(TableRowNode(id, Vector.empty))

  val cellCodec: NodeJsonCodec[TableCellNode] = new NodeJsonCodec[TableCellNode]:
    val nodeType: NodeType[TableCellNode] = TableCellNode

    def encode(
        node: TableCellNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] =
      Right(
        Option.when(node.header)("header" -> JsonValue.Bool(true)).toVector ++
          Option
            .when(node.alignment != ColumnAlignment.Default)(
              "alignment" -> JsonValue.Str(alignmentName(node.alignment))
            )
            .toVector
      )

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, TableCellNode] =
      for
        header    <- flag(payload, "header", context.path)
        raw       <- payload.optionalString("alignment", context.path)
        alignment <- raw.fold(Right(ColumnAlignment.Default))(
          alignmentOf(_, context.path.field("alignment"))
        )
      yield TableCellNode(id, Vector.empty, header, alignment)

  val json: JsonSupport = JsonSupport.of(tableCodec, rowCodec, cellCodec)

  // -----------------------------------------------------------------------------------------
  // Markdown
  // -----------------------------------------------------------------------------------------

  private def toSyntax(alignment: ColumnAlignment): TableAlignment = alignment match
    case ColumnAlignment.Default => TableAlignment.Unspecified
    case ColumnAlignment.Left    => TableAlignment.Left
    case ColumnAlignment.Center  => TableAlignment.Center
    case ColumnAlignment.Right   => TableAlignment.Right

  private def fromSyntax(alignment: TableAlignment): ColumnAlignment = alignment match
    case TableAlignment.Unspecified => ColumnAlignment.Default
    case TableAlignment.Left        => ColumnAlignment.Left
    case TableAlignment.Center      => ColumnAlignment.Center
    case TableAlignment.Right       => ColumnAlignment.Right

  /** GFM pipe tables. Read only under a profile with tables (`commonMarkSafeWithTables`).
    *
    * ==What Markdown cannot write==
    *
    * A GFM cell is one line of inline content, and a table always has a header row. A cell with two
    * paragraphs, a list or a code block, and a table without a header, have no spelling -- they are
    * written as closely as possible and reported as a loss, so that `Strict` refuses the export and
    * `AllowLossy` says what went (§18.2).
    */
  val markdown: MarkdownBlockRule = new MarkdownBlockRule:
    val id = "ember.markdown.table"

    def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId] =
      block match
        case MarkdownBlock.Table(_, span, alignments, rows) =>
          val header = rows.headOption.collect { case row: MarkdownBlock.TableRow => row.header }
          Some(
            sink.add(span)(
              TableNode(_, children, header.getOrElse(true), alignments.map(fromSyntax))
            )
          )
        case MarkdownBlock.TableRow(_, span, _, _) =>
          Some(sink.add(span)(TableRowNode(_, children)))
        case MarkdownBlock.TableCell(_, span, header, alignment, _) =>
          val paragraph = sink.add(span)(ParagraphNode(_, children))
          Some(sink.add(span)(TableCellNode(_, Vector(paragraph), header, fromSyntax(alignment))))
        case _ => None

    def handles(node: EditorNode): Boolean = node match
      case _: TableNode | _: TableRowNode | _: TableCellNode => true
      case _                                                 => false

    def encode(
        node: EditorNode,
        children: MarkdownChildren,
        sink: SyntaxSink
    ): Option[MarkdownBlock] = node match
      case value: TableNode =>
        if !value.header then
          sink.lost(
            s"Tabelle `${value.id.value}` ohne Kopfzeile: GFM schreibt die erste Zeile als Kopf."
          )
        val rows = children.blocks.zipWithIndex.map {
          case (row: MarkdownBlock.TableRow, index) => row.copy(header = index == 0)
          case (other, _)                           => other
        }
        Some(MarkdownBlock.Table(sink.fresh(), sink.nowhere, value.alignments.map(toSyntax), rows))

      case _: TableRowNode =>
        Some(MarkdownBlock.TableRow(sink.fresh(), sink.nowhere, false, children.blocks))

      case value: TableCellNode =>
        val inlines = children.blocks match
          case Vector()                                       => Vector.empty
          case Vector(MarkdownBlock.Paragraph(_, _, content)) => content
          case blocks                                         =>
            sink.lost(
              s"Zelle `${value.id.value}` enthaelt mehr als eine Zeile; GFM-Zellen sind einzeilig."
            )
            blocks.flatMap {
              case MarkdownBlock.Paragraph(_, _, content)     => content
              case MarkdownBlock.Heading(_, _, _, _, content) => content
              case other                                      => Vector.empty
            }
        Some(
          MarkdownBlock.TableCell(
            sink.fresh(),
            sink.nowhere,
            value.header,
            toSyntax(value.alignment),
            inlines
          )
        )

      case _ => None

  val markdownRules: MarkdownSupport = MarkdownSupport.of(markdown)

  // -----------------------------------------------------------------------------------------
  // HTML import
  // -----------------------------------------------------------------------------------------

  private def alignmentAttribute(element: HtmlFragment.Element): ColumnAlignment =
    element.attributes
      .find(_.name == "align")
      .flatMap(attribute =>
        ColumnAlignment.values.find(_.toString.equalsIgnoreCase(attribute.value))
      )
      .getOrElse(ColumnAlignment.Default)

  /** Whether the first row of a pasted table holds header cells. */
  private def firstRowIsHeader(element: HtmlFragment.Element): Boolean =
    def rowsOf(fragment: HtmlFragment.Element): Vector[HtmlFragment.Element] =
      fragment.children.flatMap {
        case child: HtmlFragment.Element if child.tag == "tr" => Vector(child)
        case child: HtmlFragment.Element if Set("thead", "tbody", "tfoot").contains(child.tag) =>
          rowsOf(child)
        case _ => Vector.empty
      }
    rowsOf(element).headOption.exists(_.children.exists {
      case cell: HtmlFragment.Element => cell.tag == "th"
      case _                          => false
    })

  val tableImport: HtmlImportRule = new HtmlImportRule:
    val name = "html.table"

    def handles(element: HtmlFragment.Element): Boolean = element.tag == "table"

    def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
      val header = firstRowIsHeader(element)
      HtmlImportDecision.Container(
        children => TableNode(scope.nextId(), children, header),
        NodeLevel.Block,
        ChildMode.Blocks
      )

  val rowImport: HtmlImportRule =
    HtmlImportRule.container("html.table-row", Set("tr"), ChildMode.Blocks) {
      (_, children, scope) =>
        TableRowNode(scope.nextId(), children)
    }

  val cellImport: HtmlImportRule =
    HtmlImportRule.container("html.table-cell", Set("td", "th"), ChildMode.Blocks) {
      (element, children, scope) =>
        TableCellNode(scope.nextId(), children, element.tag == "th", alignmentAttribute(element))
    }

  /** A caption has no place in the table model; its text goes with a diagnosis. */
  val captionImport: HtmlImportRule = new HtmlImportRule:
    val name = "html.table-caption"

    def handles(element: HtmlFragment.Element): Boolean = element.tag == "caption"

    def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
      HtmlImportDecision.Discard("Tabellenbeschriftung hat im Dokument keinen Platz")

  val htmlImport: Vector[HtmlImportRule] = Vector(tableImport, rowImport, cellImport, captionImport)
