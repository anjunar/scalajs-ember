# ember-toolbar

Optionale Toolbar und Link-/Bilddialoge (P27). Das Modul konsumiert typisierte
Core-Commands; Dokumentänderungen laufen ausschließlich über Transaktionen.
`ember-core`, `ember-ui`, `ember-browser` und `ember-forms` benötigen dieses Modul
nicht. Es gibt keine Rückkante und keine zusätzliche UI-Runtime.

Die Komponenten verwenden das veröffentlichte `scalajs-ui-core:1.0.0`. Ein natives
`<dialog>` übernimmt Modalität und Hintergrund-Inertheit. Die Komponente führt Tab
an den beiden Dialoggrenzen weiter und behandelt Escape; die übrigen Tasten bleiben
nativ. Das vorhandene `ui-viewport.Window` ist kein modaler Dialog. Deshalb wurden
`ui-controls` und `ui-viewport` für P27 nicht als ungenutzte Abhängigkeiten ergänzt.

## Zusammensetzen

`ToolbarAction.command[A]` bindet einen typisierten Command samt Payload und einer
Abfrage für `CommandState`. `ToolbarAction` erlaubt daneben Aktionen wie das Öffnen
eines Dialogs. Der Aufrufer bestimmt Reihenfolge, Namen und angebotene Features.
Die enthaltenen Commands werden nicht automatisch registriert: Die entsprechende
RichText-/History-/Link-/Image-Extension muss in der Sitzung installiert sein.
Für das Ersetzen einer Textauswahl durch ein Bild wird `ClipboardExtension` benötigt;
fehlt sie, schlägt die gesamte Transaktion ohne Textverlust fehl.

```scala
val bold = ToolbarAction.command(
  "bold", "Fett", session, RichText.ToggleMark, StandardMarks.Strong,
  () => ToolbarState.mark(session.state, StandardMarks.Strong, editable())
)
val toolbar = new EditorToolbar(session, selectionPort, Vector(bold))
Runtime.mount(toolbar, DomCursor.root(toolbarContainer))

val service = new EditorDialogService(session, generator, () => editable())
val dialogs = new EditorDialogHost(service, selectionPort, dialogContainer, toolbar.announce)
val links = new LinkDialog(service, dialogs)
val images = new ImageDialog(service, dialogs, pickFile = applicationFilePicker)
```

`editable()` muss die tatsächliche Anwendungsfreigabe abbilden: Editable-Modus,
kein Source-Modus und keine aktive Composition/Recovery. Nach Änderungen dieser
außerhalb der Sitzung gehaltenen Zustände ruft die Anwendung `toolbar.refresh()`
auf. Session-Commits aktualisieren den Zustand automatisch. Der Dialog-Service prüft
die Freigabe auch beim Übernehmen; die Browser-/Core-Regeln bleiben zusätzlich aktiv.
Undo/Redo-Zustände kommen aus der zur Sitzung gehörenden `History`-Instanz.

`ToolbarState.mark` liest bei einem Caret die wirksamen `TypingMarks`, bei einem
Bereich die betroffenen Textläufe. `aria-pressed` unterscheidet `true`, `false` und
`mixed`. Native Buttons tragen Namen und `disabled`; die Leiste hat einen Tabstopp.
Pfeil links/rechts, Home und End wechseln zwischen aktivierten Buttons. Tab verlässt
die Leiste. Nur ein primärer Mousedown aus dem fokussierten Editor verhindert den
Fokuswechsel; Tastaturaktivierung bleibt auf dem Button. Hintergrund-Commits setzen
weder Fokus noch native Selection.

Das [Stylesheet](src/main/resources/ember-toolbar.css) wird als Modulressource
mitgeführt und von der Anwendung eingebunden. Es verwendet sichtbare Rahmen,
Unterstreichung und Systemfarben für Forced Colors; Reduced Motion deaktiviert
Bewegung. Die Bibliothek installiert keine globalen Styles und liest beim Import
weder `window` noch `document`. Toolbar und Dialoghost werden erst im Browser mit
dem zur Editierfläche gehörenden Container und `SelectionPort` erzeugt.

## Dialogvertrag

`EditorDialogService.capture()` erstellt ein sitzungsgebundenes, einmal verwendbares
`DialogTarget`. Anchor und Focus werden getrennt über `mappingSince` aufgelöst;
Einfügen an einer Ersatzgrenze ist ausgeschlossen. Gelöschte Ziele, abgelaufene
Mapping-Historie, Undo/Redo und Dokumentersetzung machen das Ziel ungültig. Ein
Datensatzwechsel mit identischem Dokumentinhalt benötigt `service.invalidate()`.
Fehler bleiben als Text in der Alert-Region sichtbar; Eingaben bleiben korrigierbar.
Erst ein erfolgreicher Commit verbraucht das Ziel. Abbrechen verändert keinen Inhalt.

Linkdialoge setzen, ändern und entfernen Links. Ein leerer Caret außerhalb eines
Links kann keinen neuen Link ohne Beschriftung anlegen; die Anwendung deaktiviert
die entsprechende Aktion. Der Bilddialog ersetzt Textauswahlen atomar oder bearbeitet
ein ausgewähltes Bild. Einzelne NodeSelections und Bereiche über genau einem Bild
werden erkannt. Alt darf ausdrücklich leer sein. Beim Ändern des Alts bleiben
Knoten-ID und Medienmetadaten erhalten; ein Quellenwechsel ersetzt die Medienreferenz.
Jede erfolgreiche Dialogänderung ist ein eigener Undo-Schritt.

Nach Schließen erhält bei Mausaktivierung der zuvor fokussierte Editor seine
gemappte Auswahl zurück. Bei Tastaturaktivierung erhält der auslösende Button den
Fokus. Rückgabe erfolgt nur, solange der Dialog den Fokus noch besitzt. Ein
abgelaufenes Ziel wird gemeldet; eine neue Einfügeposition wird nicht erfunden.

## Datei-Callback und Lifecycle

`ImageDialog` erhält optional
`(DialogTarget, String /* alt */) => Either[EditorError, Unit]`. Dieser Callback
läuft in der Benutzeraktivierung des Dateibuttons und muss **vor seiner Rückkehr**
das Ziel mit `service.resolve(target)` auflösen und an den anwendungseigenen Picker
bzw. P26-`MediaCoordinator.capture(range)` übergeben. Dann darf er den nativen Picker
öffnen. Bei `Right(())` schließt der Dialog; das DialogTarget selbst ist danach
ungültig. Der Upload besitzt sein eigenes MediaTarget und seinen eigenen Lifecycle.
Ein abgebrochener Dateidialog erzeugt keinen Upload. Upload-Erfolg und -Fehler meldet
die Anwendung über `toolbar.announce(...)` in der sichtbaren Statusregion.
Der Datei-Callback wird beim Bearbeiten eines vorhandenen Bildes nicht angeboten.

Beim Entfernen des Editors:

1. `dialogs.dispose()` schließt den Dialog und entfernt seine Listener.
2. `service.dispose()` invalidiert Targets und entfernt die Commit-Subscription.
3. Anwendungseigene Picker und MediaCoordinator entsorgen.
4. `Runtime.unmount(toolbar)`, dann Input/Selection/View/Session entsorgen.

Die ausführbare Verdrahtung steht in
[`ToolbarFixtures.scala`](../ember-integration/src/main/scala-3/ember/editor/integration/ToolbarFixtures.scala).
Eine eigenständige Demoansicht ist nach dem Integration-Full-Link und Start von
`node ember-integration/browser/server.mjs` unter `http://127.0.0.1:4188/toolbar`
erreichbar. Ihr Upload verwendet den ausdrücklich begrenzten P26-Testserver.

Die automatisierten Tests prüfen Commands, Targets, Maus-/Tastaturführung,
Fokus-Rückgabe, echte Dateiauswahl/HTTP, Readonly, Forced Colors, Reduced Motion
und schmale Fenster. Der Composition-Fall prüft das Controller-Protokoll.
Eine physische mobile IME-/Touch- oder Screenreader-Abnahme folgt daraus nicht;
diese Geräteprüfungen bleiben Teil von P28.

Die [P28-Supportmatrix](../ember-integration/browser/support-matrix.md) hält den
aktuellen Status fest. Die [Gerätecheckliste](../ember-integration/browser/accessibility-checklist.md)
erläutert die manuelle Abnahme und das opt-in Trace-Werkzeug unter `/toolbar?trace=1`.
