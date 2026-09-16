package ember.editor.table

import ember.editor.core.*
import ember.editor.richtext.*

/** Tables as an extension (X01).
  *
  * ==No core special case==
  *
  * X01's acceptance: "Kein Core-Spezialfall fuer TableSelection". Everything a table needs goes in
  * through the doors §13 already has -- node types, a selection mapper, transforms and commands.
  * The one thing the rich-text profile had to learn is a marker, not a type: a cell is an
  * [[IsolatingElementNode]], and range deletion and Backspace respect that without knowing tables
  * exist.
  *
  * ==Where it takes precedence==
  *
  * Over the rich-text editing commands, and only for a [[TableSelection]]. Backspace, Delete,
  * typing and Enter over a cell rectangle clear the cells; with any other selection these handlers
  * return `Pass` and the ordinary handler runs (§12).
  */
final class TableExtension private (generator: NodeIdGenerator) extends Extension:

  val id: ExtensionId = ExtensionId("ember.table")

  override val dependsOn: Vector[ExtensionId] = Vector(ExtensionId("ember.rich-text"))

  override def contribute: ExtensionContributions =
    ExtensionContributions(
      nodeTypes = Vector(TableNode, TableRowNode, TableCellNode),
      selectionMappers = Vector(TableSelectionMapper),
      transforms = TableNormalization.all(generator),
      commands = Vector(
        CommandRegistration(TableCommands.InsertTable) { (scope, size) =>
          TableEditing.insertTable(scope, generator, size)
        },
        CommandRegistration(TableCommands.InsertRow) { (scope, position) =>
          TableEditing.insertRow(scope, generator, position)
        },
        CommandRegistration(TableCommands.InsertColumn) { (scope, position) =>
          TableEditing.insertColumn(scope, generator, position)
        },
        CommandRegistration(TableCommands.DeleteRow) { (scope, _) =>
          TableEditing.deleteRow(scope, generator)
        },
        CommandRegistration(TableCommands.DeleteColumn) { (scope, _) =>
          TableEditing.deleteColumn(scope, generator)
        },
        CommandRegistration(TableCommands.DeleteTable) { (scope, _) =>
          TableEditing.deleteTable(scope, generator)
        },
        CommandRegistration(TableCommands.ToggleHeaderRow) { (scope, _) =>
          TableEditing.toggleHeaderRow(scope)
        },
        CommandRegistration(TableCommands.SetColumnAlignment) { (scope, alignment) =>
          TableEditing.setColumnAlignment(scope, alignment)
        },
        CommandRegistration(TableCommands.MoveToCell) { (scope, direction) =>
          TableEditing.moveToCell(scope, generator, direction)
        },
        CommandRegistration(TableCommands.SelectCells) { (scope, corners) =>
          TableEditing.selectCells(scope, corners._1, corners._2)
        },
        CommandRegistration(TableCommands.CollapseCellSelection) { (scope, _) =>
          TableEditing.collapseCellSelection(scope)
        },
        CommandRegistration(
          RichText.DeleteBackward,
          CommandPriority.High,
          (scope, _) => TableEditing.deleteOverCells(scope, generator)
        ),
        CommandRegistration(
          RichText.DeleteForward,
          CommandPriority.High,
          (scope, _) => TableEditing.deleteOverCells(scope, generator)
        ),
        CommandRegistration(
          RichText.InsertParagraph,
          CommandPriority.High,
          (scope, _) => TableEditing.deleteOverCells(scope, generator)
        ),
        CommandRegistration(
          RichText.InsertText,
          CommandPriority.High,
          (scope, text) => TableEditing.typeOverCells(scope, generator, text)
        )
      )
    )

object TableExtension:

  def apply(generator: NodeIdGenerator): TableExtension = new TableExtension(generator)
