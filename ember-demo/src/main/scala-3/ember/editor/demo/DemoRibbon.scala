package ember.editor.demo

import ember.editor.browser.{SelectionPort, WriteIntent}
import ember.editor.core.*
import ember.editor.code.{CodeCommands, CodeInfo, CodeBlockNode}
import ember.editor.history.HistoryCommands
import ember.editor.list.*
import ember.editor.richtext.*
import ember.editor.table.*
import ember.editor.toolbar.*

object DemoRibbon:
  def groups(
      editor: DemoSession,
      available: () => Boolean,
      dialogs: DemoDialogs,
      selection: SelectionPort
  ): Vector[ToolbarGroup] =
    val session = editor.session
    def enabled = CommandState(
      available() && session.selection.exists(_.isInstanceOf[RangeSelection])
    )
    def command[A](
        id: String,
        label: String,
        cmd: EditorCommand[A],
        value: A,
        state: () => CommandState
    ): ToolbarAction =
      val action = ToolbarAction.command(id, label, session, cmd, value, state)
      action.copy(activate =
        () =>
          action.activate().map { _ =>
            selection.scope.focus()
            selection.write(session.selection, WriteIntent.Explicit)
          }
      )

    /** Enabled only where a table command has something to act on. */
    def inTable[A](id: String, label: String, cmd: EditorCommand[A], value: A) =
      command(
        id,
        label,
        cmd,
        value,
        () =>
          CommandState(
            available() && Tables.contextAt(session.document, session.selection).isDefined
          )
      )
    def block[A](id: String, label: String, cmd: EditorCommand[A], value: A) =
      command(id, label, cmd, value, () => blockState(session, id, enabled))
    def mark(id: String, label: String, value: TextMark) =
      command(
        id,
        label,
        RichText.ToggleMark,
        value,
        () => ToolbarState.mark(session.state, value, available())
      )
    val groups = Vector(
      ToolbarGroup(
        "Verlauf",
        Vector(
          command(
            "undo",
            "Rückgängig (Strg+Z)",
            HistoryCommands.Undo,
            (),
            () => CommandState(available() && editor.history.canUndo)
          ),
          command(
            "redo",
            "Wiederholen (Strg+Shift+Z)",
            HistoryCommands.Redo,
            (),
            () => CommandState(available() && editor.history.canRedo)
          )
        )
      ),
      ToolbarGroup(
        "Schrift",
        Vector(
          mark("bold", "Fett (Strg+B)", StandardMarks.Strong),
          mark("italic", "Kursiv (Strg+I)", StandardMarks.Emphasis),
          mark("inline-code", "Inline-Code", StandardMarks.InlineCode)
        )
      ),
      ToolbarGroup(
        "Absatz",
        Vector(
          block("paragraph", "Absatz", RichText.SetHeading, None),
          block("heading-1", "Überschrift 1", RichText.SetHeading, Some(HeadingLevel.H1)),
          block("heading-2", "Überschrift 2", RichText.SetHeading, Some(HeadingLevel.H2)),
          block("heading-3", "Überschrift 3", RichText.SetHeading, Some(HeadingLevel.H3)),
          block("quote", "Zitat", RichText.Quote, ()),
          block("unquote", "Zitat aufheben", RichText.Unquote, ())
        )
      ),
      ToolbarGroup(
        "Listen",
        Vector(
          block("bullet-list", "Aufzählung", ListCommands.ToggleList, ListKind.Unordered),
          block("ordered-list", "Nummerierung", ListCommands.ToggleList, ListKind.Ordered),
          block("indent", "Einrücken", ListCommands.Indent, ()),
          block("outdent", "Ausrücken", ListCommands.Outdent, ())
        )
      ),
      ToolbarGroup(
        "Einfügen",
        Vector(
          ToolbarAction("link", "Link bearbeiten", () => enabled, () => dialogs.link()),
          ToolbarAction(
            "image",
            "Bild bearbeiten",
            () => CommandState(available() && session.selection.nonEmpty),
            () => dialogs.image()
          ),
          // A dialog rather than a toggle: the language decides whether the block is coloured.
          ToolbarAction(
            "code-block",
            "Codeblock und Sprache",
            () => blockState(session, "code-block", enabled),
            () => dialogs.codeBlock()
          ),
          block("rule", "Trennlinie", RichText.InsertThematicBreak, ())
        )
      ),
      ToolbarGroup(
        "Tabelle",
        Vector(
          command(
            "table",
            "Tabelle einfügen",
            TableCommands.InsertTable,
            TableSize(3, 3),
            () => enabled
          ),
          inTable(
            "row-above",
            "Zeile darüber einfügen",
            TableCommands.InsertRow,
            RowPosition.Above
          ),
          inTable(
            "row-below",
            "Zeile darunter einfügen",
            TableCommands.InsertRow,
            RowPosition.Below
          ),
          inTable(
            "column-before",
            "Spalte links einfügen",
            TableCommands.InsertColumn,
            ColumnPosition.Before
          ),
          inTable(
            "column-after",
            "Spalte rechts einfügen",
            TableCommands.InsertColumn,
            ColumnPosition.After
          ),
          inTable("delete-row", "Zeile löschen", TableCommands.DeleteRow, ()),
          inTable("delete-column", "Spalte löschen", TableCommands.DeleteColumn, ()),
          inTable("delete-table", "Tabelle löschen", TableCommands.DeleteTable, ())
        )
      )
    )
    val labels = Map(
      "undo"          -> "↶",
      "redo"          -> "↷",
      "bold"          -> "F",
      "italic"        -> "K",
      "inline-code"   -> "</>",
      "heading-1"     -> "H1",
      "heading-2"     -> "H2",
      "heading-3"     -> "H3",
      "unquote"       -> "Ohne Zitat",
      "bullet-list"   -> "• Liste",
      "ordered-list"  -> "1. Liste",
      "indent"        -> "→ Einzug",
      "outdent"       -> "← Einzug",
      "link"          -> "Link",
      "image"         -> "Bild",
      "code-block"    -> "Code",
      "rule"          -> "Linie",
      "table"         -> "Tabelle",
      "row-above"     -> "↑ Zeile",
      "row-below"     -> "↓ Zeile",
      "column-before" -> "← Spalte",
      "column-after"  -> "→ Spalte",
      "delete-row"    -> "− Zeile",
      "delete-column" -> "− Spalte",
      "delete-table"  -> "− Tabelle"
    )
    groups.map(group =>
      group.copy(actions =
        group.actions.map(action => action.copy(shortLabel = labels.get(action.id)))
      )
    )

  private def blockState(session: EditorSession, id: String, base: CommandState): CommandState =
    def ancestors(start: NodeId): Vector[EditorNode] =
      val result  = Vector.newBuilder[EditorNode]
      var current = Option(start)
      while current.nonEmpty do
        session.document.node(current.get).foreach(result += _)
        current = session.document.parentOf(current.get)
      result.result()
    val paths = session.selection.toVector.collect { case range: RangeSelection =>
      if range.isCollapsed then Vector(ancestors(range.focus.owner))
      else RangeFormatting.runsIn(session.document, range).map(run => ancestors(run.id))
    }.flatten
    def pressed(test: Vector[EditorNode] => Boolean): CommandState =
      val count = paths.count(test)
      base.copy(pressed = Some(if count == 0 then "false"
      else if count == paths.size then "true"
      else "mixed"))
    id match
      case "paragraph" => pressed(_.exists(_.isInstanceOf[ParagraphNode]))
      case "heading-1" | "heading-2" | "heading-3" =>
        pressed(_.exists {
          case heading: HeadingNode => heading.level.level == id.last.asDigit
          case _                    => false
        })
      case "quote"   => pressed(_.exists(_.isInstanceOf[QuoteNode]))
      case "unquote" =>
        base.copy(enabled = base.enabled && paths.exists(_.exists(_.isInstanceOf[QuoteNode])))
      case "code-block"  => pressed(_.exists(_.isInstanceOf[CodeBlockNode]))
      case "bullet-list" =>
        pressed(_.exists {
          case list: ListNode => list.kind == ListKind.Unordered; case _ => false
        })
      case "ordered-list" =>
        pressed(_.exists { case list: ListNode => list.kind == ListKind.Ordered; case _ => false })
      case "indent" =>
        base.copy(enabled = base.enabled && paths.exists(_.collectFirst { case item: ListItemNode =>
          item
        }.exists(item => session.document.indexOfChild(item.id).exists(_ > 0))))
      case "outdent" =>
        base.copy(enabled = base.enabled && paths.exists(_.exists(_.isInstanceOf[ListItemNode])))
      case _ => base
