# ember-clipboard

P25: validierte Dokumentfragmente, Copy/Cut/Paste und strukturierter Drag/Drop.
Das Modul hängt auf core, rich-text, json, html und browser. Es kennt weder die
konkreten Standardadapter noch forms. Extraktion, Codec und Commands verwenden
keine DOM-Globals und laufen in den Node.js-Tests.

## Einbindung

`ClipboardExtension(generator)` zusammen mit `RichText(generator)` registrieren.
Einen `ClipboardCodec(profile, schema, jsonSupport, htmlSupport, htmlImportSupport)`
mit den Adaptern der Anwendung erstellen. `ClipboardService(session, codec)`
stellt die headless Operationen bereit. `BrowserClipboardController(session,
selectionPort, inputController, codec, report, files)` bindet die Browserereignisse;
vor den zugrunde liegenden Controllern mit `dispose()` freigeben.

`report` erhält Erfolg samt Importdiagnosen oder einen `ClipboardError`. Der
injizierte `files`-Callback erhält `ClipboardFileIntent` mit Dateien, Zielbookmark
und Ereignisquelle. Er ist die Anschlussstelle für P26; dieses Modul lädt keine
Dateien hoch und bettet keine Dateidaten ins Dokument ein. Ohne Callback werden
Dateien mit einer Diagnose abgewiesen.

## Austauschvertrag

- Reihenfolge: `application/x-ember-editor+json`, `text/html`, `text/plain`.
  Jeder Zweig wird unabhängig validiert; ein ungültiger Zweig kann auf einen
  gültigen niedrigeren zurückfallen. Die Diagnose nennt verworfene Formate.
- Internes Envelope: Format `ember-fragment`, Version `1`, Anwendungsprofil,
  `openStart`, `openEnd` und das versionierte `DocumentJson`-Dokument.
  Schema, Codecs und URL-Policies kommen vom Empfänger.
- Pro Quellformat standardmäßig höchstens 1.048.576 UTF-16-Codeeinheiten;
  JSON und HTML höchstens 20.000 Nodes und Tiefe 64. Klartext wird vor dem
  Aufbau ebenfalls auf 20.000 Nodes begrenzt. CRLF und CR werden zu LF;
  jede Zeile einschließlich einer letzten leeren wird ein Absatz.
- Copy bewahrt Markierungen, Inline-Wrapper und Teiltext. Offene Grenzen zählen
  Container unter der synthetischen Wurzel. Paste teilt den Zielpfad und führt
  passende offene Container anhand ihrer Deskriptoren zusammen. Unterschiedliche
  Metadaten, etwa Linkziele, werden nicht zusammengeführt.
  Ein einzelner offener Absatz aus Text/Atomen wird direkt in den vorhandenen
  Textkontext eingefügt und behält dessen Überschrift-, Listen- und Link-Container.
- Kopien erhalten neue IDs; interne Moves ganzer Nodes verwenden `Operation.Move`
  und behalten deren IDs. Teiltext wird ausgeschnitten, das Ziel durch primitive
  Mappings verfolgt und das Fragment in derselben Transaktion eingefügt. Ungültige
  Zielstrukturen brechen die gesamte Transaktion ab.
- Semantisches HTML wird aus `HtmlSupport` ohne Editorattribute exportiert.
  Nicht verfügbare JSON-/HTML-Adapter lassen das jeweilige Format entfallen.
  Atom-Klartext ist standardmäßig U+FFFC; die Anwendung kann `atomText` injizieren,
  beispielsweise für Bild-Alttext. Es wird kein Dokument-DOM ausgelesen.

## Cut, Ereignisse und Drag

`PendingCut` ist einmal verwendbar. Ein abgewiesener Write löscht nichts. Nach
Write-Erfolg muss die Dokumentrevision unverändert sein, auch nach Edit + Undo;
reine Auswahländerungen dürfen über die ursprünglichen Bookmarks aufgelöst werden.
`AsyncClipboardPort` ist eine injizierbare Schnittstelle mit getesteten
Future-/Exception-/Konfliktpfaden, kein eingebauter `navigator.clipboard`-Adapter.

Der Eventadapter bestätigt, dass `DataTransfer` alle angebotenen Formate zurückliest.
Das ist eine Bestätigung des Event-Stores, keine unabhängige Bestätigung des
Betriebssystem-Clipboards. Die unten genannte WebKit-Grenze betrifft genau diesen
Unterschied und sperrt dort die native Copy/Cut-Abnahme.

Cut, Paste und Move setzen jeweils `HistoryPolicy.Push`. Clipboard-Ereignisse
beanspruchen ihren korrespondierenden `beforeinput`-Pfad nur im selben Dispatch-Turn;
der Claim wird per Microtask entfernt. Eine spätere Paste wird nie durch einen
alten Claim unterdrückt. Unbestätigtes `deleteByCut` wird abgewiesen;
`deleteByDrag` wird verhindert, da interne Moves die Quelle bereits verändern
und fremde Editoren eine Kopie erhalten. Nicht abbrechbare Input-Ereignisse bleiben
beim bestehenden NativeInput-/Recovery-Protokoll.

Der lokale Drag-Token wird im Fenster des Hosts erzeugt und nur in dessen aktivem
Controller mit der Quellauswahl und Dokumentrevision verknüpft. Ein fremder Editor
bekommt stets eine Kopie. Ctrl-/Alt-Drag kopiert ebenfalls. Das Drop-Ziel stammt aus
der nativen Koordinatenabfrage und der bestehenden `DomPositionMap`. Native Controls
innerhalb von Atomen behalten ihre Ereignisse. Readonly und Composition sperren
schreibende Clipboard-/Drop-Operationen.

## Nachweise und offene Browsergrenze

Die Standardprofil-Tests liegen in `ember-standard/.../ClipboardSpec.scala` und
`AsyncClipboardSpec.scala`; die test-only Abhängigkeit vermeidet eine Rückkante
von clipboard zu standard. `TransactionSpec` prüft Draft-Bookmarks und atomare
Abweisung. Der Browser-Harness enthält `clipboard.spec.mjs` und `drop.spec.mjs`.

Der echte Tastaturtest besteht unter Chromium und Firefox. Im Windows-WebKit des
aktuellen Playwright-Harness gehen eventgeschriebene Clipboard-Daten verloren.
Ein separater Test mit einer nativen Textarea ohne Editoradapter reproduziert das
bereits mit `text/plain`. Beide Fälle sind ausschließlich für Windows-WebKit als
**erwartet fehlgeschlagen** markiert; eine Reparatur erzeugt einen unerwarteten
Erfolg und fordert die Neubewertung. Native Copy/Cut/Paste-Unterstützung ist auf
dieser Kombination nicht abgenommen. Die übrigen Browserfälle prüfen explizit
das Ereignisprotokoll mit synthetischen Clipboard-/DragEvents; physische Drags,
mobile Clipboard-Menüs und reale IME sind damit nicht abgenommen.
