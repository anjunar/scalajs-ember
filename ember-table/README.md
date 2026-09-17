# scalajs-ember-table

Tables with a dedicated rectangular cell-selection contract. Headless and optional.

| | |
| --- | --- |
| sbt ID / artifact | `scalajs-ember-table` |
| Scala package | `ember.editor.table` |
| Production dependencies | `scalajs-ember-core`, `scalajs-ember-rich-text` (`scalajs-ember-history` for tests) |

## Overview

`ember-table` adds `TableNode`/`TableRowNode`/`TableCellNode`, a `TableSelection` with a
registered selection mapper and validator, eight normalization rules, eleven commands and the
`TableExtension`. Formats (HTML, JSON, GFM Markdown, HTML import) live in
[`ember-standard`](../ember-standard/README.md) (`TableSupport`); keyboard and browser cell
selection live in [`ember-browser-support`](../ember-browser-support/README.md)
(`TableBindings`, `TableSelectionView`). No standard bundle pulls tables in automatically — an
application opts in explicitly.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ember-table" % "1.0.1"
```

## Quick start

```scala
val resolved = ExtensionResolver
  .resolve(Vector(RichText(generator), TableExtension(generator), history))
  .getOrElse(...)

session.dispatch(TableCommands.InsertTable, TableSize(rows = 3, columns = 3))
session.dispatch(TableCommands.InsertRow, RowPosition.Below)
session.dispatch(TableCommands.InsertColumn, ColumnPosition.After)
session.dispatch(TableCommands.DeleteRow)
session.dispatch(TableCommands.DeleteColumn)
session.dispatch(TableCommands.DeleteTable)
session.dispatch(TableCommands.ToggleHeaderRow)
session.dispatch(TableCommands.SetColumnAlignment, ColumnAlignment.Center)
session.dispatch(TableCommands.MoveToCell, CellDirection.Next)      // Tab
session.dispatch(TableCommands.SelectCells, anchorCell -> focusCell)
session.dispatch(TableCommands.CollapseCellSelection)               // Escape
```

Wiring tables into an application, in addition to what it already has (no bundle includes this
automatically):

```scala
TableSupport.views                                              // alongside ImageSupport.views etc.
StandardJsonSupport.everything(...) ++ TableSupport.json
MarkdownSupports.everything(...) ++ TableSupport.markdownRules   // read with commonMarkSafeWithTables
StandardHtmlImport.everything(...).withRules(TableSupport.htmlImport*)
TableBindings.tabNavigation ++ EditorBindings.everythingKeyboard ++ TableBindings.keyboard
TableSelectionView.attach(session, view, selectionPort)
```

## The model

```text
TableNode(header, alignments)
  TableRowNode
    TableCellNode(header, alignment)
      ParagraphNode ...
```

A cell holds **blocks**, not inline content — Enter in a cell makes a second paragraph, and HTML
allows lists in cells; Markdown can only manage one line per cell, and the adapter reports more
than one as a loss instead of restricting the document to what a format can write. Whether the
first row is a header, and how each column aligns, are facts of the **table**; cells carry a copy
because a cell renders as `th`/`td` without knowing its row. Normalization keeps the copies in
sync; commands only write the table.

## Invariants, repaired rather than rejected

| Rule | Effect |
| --- | --- |
| `table.children-are-rows` | A non-row child of the table gets a row and a cell |
| `table.row-children-are-cells` | A non-cell child of a row gets a cell |
| `table.cell-holds-blocks` | Inline content dropped directly into a cell goes into a paragraph |
| `table.rectangular` / `table.row-keeps-rectangular` | Short rows are padded; header/alignment copies follow the table |
| `table.empty-cell-gets-paragraph` | An empty cell gets a paragraph with an empty run |
| `table.empty-row-goes` / `table.empty-table-goes` | Empty rows and tables disappear |

Validation is deliberately lenient — pasted HTML is rarely rectangular, and an import builds its
document without transforms. As soon as a table enters a session, it is repaired.

## No kernel special case

Node types, a `SelectionMapper`, transforms and commands all come through the doors the kernel
already has. The only thing the rich-text profile had to learn is a **marker**, not a type:
`IsolatingElementNode` (the cell — Backspace at the start and Delete at the end never pull text
from a neighboring cell; a range from one cell into another is never merged) and
`StructuralElementNode` (table and row — a range that only touches them clears the cells inside
rather than removing rows or columns; a table fully contained in a range goes as a whole). A
session without `TableExtension` has no mapper for `TableSelection` and rejects such a selection —
the kernel never guesses.

## Cell selection

`TableSelection(table, anchor, focus)` is the rectangle spanned by its two corners **at the time
of the question** — an inserted row inside the rectangle belongs to it afterward, and if a corner
is lost the selection falls back to a caret in whichever one remains. Backspace, Delete, typing
and Enter over a cell selection clear the cells (high-priority handlers ahead of the rich-text
commands, only for this selection kind). In the browser, dragging across cell boundaries builds
one via `TableSelectionView`, painted through a stylesheet in `head` and addressed by node IDs —
nothing is written into the editor DOM. The browser sees it via `SelectionPort.represent` as a
range from the anchor to the focus cell, and the port recognizes that as its own echo.

## Keyboard

Tab jumps to the end of the next cell, wrapping to a new row from the last cell; Shift+Tab goes
back. This is the explicitly opted-in behavior of `TabPolicy.IndentsUntilEscape` — Escape then Tab
still leaves the editor from inside a table. Outside a table the command reports `Pass`, and lists
and code keep the key.

## Limitations

- No merged cells (colspan/rowspan) — that would need its own set of invariants.
- Copying or cutting a **cell selection** writes nothing to the clipboard; a text selection inside
  tables behaves as elsewhere.
- GFM has no table without a header row and no multi-line cells; both are reported losses on
  Markdown export. An HTML `caption` is dropped with a diagnostic on import.

## Tests

```bash
sbt --server "scalajs-ember-table/Test/testOnly *"
```

| Suite | Covers |
| --- | --- |
| `TableStructureSpec` | Rectangularity, header row, alignment, repairs, lenient import |
| `TableEditingSpec` | Insert, typing, Enter, Backspace/Delete at cell boundaries, ranges across cells and tables, cell selection, rows/columns, Tab, undo |
| `TableSelectionSpec` | The rectangle, mapping through edits, fallback, the validator, rejection without the module |

Plus `ember-markdown`'s `MarkdownTableSpec` (GFM read/write), `ember-standard`'s
`TableFormatSpec` (HTML, JSON, Markdown, HTML import), and the browser harness's
`table-editing.spec.mjs` (typing, Enter, Backspace, Tab, Escape, drag-selection, undo — in
Chromium, Firefox and WebKit).

## Related modules

- [`ember-rich-text`](../ember-rich-text/README.md) — the profile this module extends.
- [`ember-standard`](../ember-standard/README.md) — HTML/JSON/Markdown adapters for tables.
- [`ember-browser-support`](../ember-browser-support/README.md) — Tab navigation and drag-to-select.
</content>
