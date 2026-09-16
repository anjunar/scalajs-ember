# scalajs-ember-table

Tabellen mit eigenem Auswahlvertrag (X01). Headless und optional.

Verbindlicher Entwurf: [UI_EDITOR_IMPLEMENTATION.md](../UI_EDITOR_IMPLEMENTATION.md) X01,
[UI_EDITOR_ARCHITECTURE.md](../UI_EDITOR_ARCHITECTURE.md) §§8, 11, 12, 13.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-table` |
| Scala-Paket | `ember.editor.table` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `scalajs-ember-rich-text` |

## Stand

X01 umgesetzt: `TableNode`/`TableRowNode`/`TableCellNode`, `TableSelection` mit registriertem
Mapper und Validator, acht Normalisierungsregeln, elf Befehle und die `TableExtension`. Formate
(HTML, JSON, GFM-Markdown, HTML-Import) liegen in `ember-standard` (`TableSupport`), Tastatur und
Zellauswahl im Browser in `ember-browser-support` (`TableBindings`, `TableSelectionView`).

## Verwendung

```scala
val resolved = ExtensionResolver
  .resolve(Vector(RichText(generator), TableExtension(generator), history))
  .getOrElse(…)

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

In der Anwendung, zusätzlich zu dem, was sie schon hat — keines der Standardbündel zieht
Tabellen herein:

```scala
TableSupport.views                                  // statt ImageSupport.views
StandardJsonSupport.everything(…) ++ TableSupport.json
MarkdownSupports.everything(…) ++ TableSupport.markdownRules  // Lesen mit commonMarkSafeWithTables
StandardHtmlImport.everything(…).withRules(TableSupport.htmlImport*)
TableBindings.tabNavigation ++ EditorBindings.tabIndentation ++ … ++ TableBindings.keyboard
TableSelectionView.attach(session, view, selectionPort)
```

## Das Modell

```text
TableNode(header, alignments)
  TableRowNode
    TableCellNode(header, alignment)
      ParagraphNode …
```

Eine Zelle hält **Blöcke**, keinen Inline-Inhalt. Enter macht in einer Zelle einen zweiten
Absatz, und HTML erlaubt Listen in Zellen. Markdown kann nur eine Zeile pro Zelle — der Adapter
meldet mehr als eine als Verlust, statt das Dokument auf das zu beschränken, was ein Format
schreiben kann.

Ob die erste Zeile ein Kopf ist und wie jede Spalte ausgerichtet ist, sind Tatsachen der
**Tabelle**. Die Zellen tragen eine Kopie, weil eine Zelle als `th` oder `td` gerendert wird, ohne
ihre Zeile zu kennen (§15.1). Die Normalisierung hält die Kopien nach; Befehle schreiben nur die
Tabelle.

## Invarianten, repariert statt abgewiesen

| Regel | Wirkung |
| --- | --- |
| `table.children-are-rows` | Ein Nicht-Zeilen-Kind der Tabelle bekommt Zeile und Zelle |
| `table.row-children-are-cells` | Ein Nicht-Zellen-Kind einer Zeile bekommt eine Zelle |
| `table.cell-holds-blocks` | Inline-Inhalt direkt in einer Zelle kommt in einen Absatz |
| `table.rectangular` / `table.row-keeps-rectangular` | Kurze Zeilen werden aufgefüllt; Kopf- und Ausrichtungskopien folgen der Tabelle |
| `table.empty-cell-gets-paragraph` | Eine leere Zelle bekommt einen Absatz mit leerem Lauf |
| `table.empty-row-goes` / `table.empty-table-goes` | Leere Zeilen und Tabellen verschwinden |

Die Validierung ist bewusst nachsichtig: eingefügtes HTML ist selten rechteckig, und ein Import
baut sein Dokument ohne Transforms. Sobald die Tabelle in eine Sitzung kommt, wird sie repariert.

## Kein Core-Spezialfall

X01 verlangt es, und so ist es gebaut: Knotenarten, ein `SelectionMapper`, Transforms und Commands
kommen über die Türen, die §13 ohnehin hat. Das Einzige, was das Rich-Text-Profil dazulernen
musste, ist ein **Marker**, kein Typ:

- `IsolatingElementNode` — die Zelle. Backspace am Anfang und Entf am Ende ziehen nie Text aus
  einer Nachbarzelle; ein Bereich von einer Zelle in eine andere wird nicht zusammengeführt.
- `StructuralElementNode` — Tabelle und Zeile. Ein Bereich, der sie nur **berührt**, leert die
  Zellen darin statt Zeilen oder Spalten zu entfernen. Liegt eine Tabelle **vollständig** in einem
  Bereich, geht sie als Ganzes.

Eine Sitzung ohne `TableExtension` hat keinen Mapper für `TableSelection` und weist eine solche
Auswahl ab — der Kern rät nicht.

## Die Zellauswahl

`TableSelection(table, anchor, focus)` ist das Rechteck, das die zwei Ecken **zum Zeitpunkt der
Frage** aufspannen. Eine eingefügte Zeile im Rechteck gehört danach dazu. Geht eine Ecke verloren,
fällt die Auswahl auf einen Caret in der verbliebenen zurück.

Backspace, Entf, Tippen und Enter über einer Zellauswahl leeren die Zellen; das übernehmen
Handler mit hoher Priorität vor den Rich-Text-Befehlen (§12), und nur für diese Auswahlart.

Im Browser entsteht sie beim Ziehen über Zellgrenzen: `TableSelectionView` hebt einen importierten
Bereich zwischen zwei Zellen derselben Tabelle zu einem Rechteck an. Angezeigt wird sie über ein
Stylesheet im `head`, adressiert über die Knoten-IDs und auf den Editor-Host beschränkt — im
Editor-DOM wird nichts geschrieben. Dem Browser wird sie über `SelectionPort.represent` als Bereich
von der Anker- zur Fokuszelle gezeigt; der Port erkennt das als eigenes Echo.

## Tastatur

Tab springt ans Ende der nächsten Zelle, aus der letzten in eine neue Zeile; Shift+Tab zurück. Das
ist §22s ausdrücklich aktiviertes Verhalten: `TableBindings.tabNavigation` gehört zu
`TabPolicy.IndentsUntilEscape`, Escape und dann Tab verlassen den Editor auch aus einer Tabelle.
Außerhalb einer Tabelle meldet der Befehl `Pass`, und Listen und Code behalten die Taste.

## Grenzen

- Keine verbundenen Zellen (Colspan/Rowspan) — X01 verlangt dafür eigens festgelegte Invarianten.
- Kopieren und Ausschneiden einer **Zellauswahl** schreibt nichts in die Zwischenablage; eine
  Textauswahl in Tabellen verhält sich wie überall.
- GFM kennt keine Tabelle ohne Kopfzeile und keine mehrzeiligen Zellen; beides ist beim
  Markdown-Export ein gemeldeter Verlust. Eine HTML-`caption` wird beim Import mit Diagnose
  verworfen.

## Tests

```bash
sbt --server "scalajs-ember-table/Test/testOnly *"
```

| Suite | Prüft |
| --- | --- |
| `TableStructureSpec` | Rechteckigkeit, Kopfzeile, Ausrichtung, Reparaturen, nachsichtiger Import |
| `TableEditingSpec` | Einfügen, Tippen, Enter, Backspace/Entf an Zellgrenzen, Bereiche über Zellen und Tabellen, Zellauswahl, Zeilen/Spalten, Tab, Undo |
| `TableSelectionSpec` | Rechteck, Mapping durch Änderungen, Rückfall, Validator, Abweisung ohne Modul |

Dazu `ember-markdown/…/MarkdownTableSpec` (GFM lesen/schreiben), `ember-standard/…/TableFormatSpec`
(HTML, JSON, Markdown, HTML-Import) und im Browser
`ember-integration/browser/test/table-editing.spec.mjs` (Tippen, Enter, Backspace, Tab und
Escape, Zellauswahl durch Ziehen, Undo) in Chromium, Firefox und WebKit.
