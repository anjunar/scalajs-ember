# Native Editor-Integration in Scala JS UI und Simplicity Blog

Stand: 14. September 2026. Die Anwendungseinbindung wurde auf ausdrücklichen Nutzerauftrag vorgezogen. Link- und Bilddialoge verwenden direkt `ui.viewport.Viewport.WindowConf`; die frühere Lexical-Dialogbrücke ist entfernt.

## Was jetzt verwendet wird

- `ui.editor.Editor` montiert eine native Ember-Session, DocumentView, SelectionPort und BrowserInputController in der gemeinsamen UI-Runtime.
- Die Ribbon hat beschriftete Gruppen, kompakte Aktionen, aktive Format-/Absatzzustände, einen gemeinsamen Tab-Stopp und Pfeil-/Home-/End-Navigation. Nicht anwendbare Listen- und Zitataktionen sind deaktiviert.
- Link- und Bildformulare bestehen aus UI-Komponenten und werden direkt im vorhandenen Viewport geöffnet. Die Textposition wird über Ember-Bookmarks gesichert. Bestätigen, Abbrechen, Fokus-Rückkehr und veraltete Dialogziele sind getestet.
- Bildadresse, Alternativtext, Titel und Pixelbreite sind im Viewport bearbeitbar. Upload, Datei-Paste und Drop verwenden den vorhandenen Anwendungsservice; Abbruch, Reihenfolge und Dokumentwechsel bleiben Session-Aufgaben.
- `editor(name, options)` und der Markdown-Formwert bleiben für `simplicity-blog` erhalten. EditorState-JSON des Prototyps ist keine Import-API.
- Die alten Scala-Adapter, Lexical-Pluginmodule, Fremd-DOM-Dialogdienste, Ribbon-CSS und produktiven npm/Maven-Abhängigkeiten sind aus der UI-Integration entfernt. Die Quell- und Lockfile-Prüfung findet dort keine Lexical-Laufzeitabhängigkeit mehr.

Die Implementierung liegt hauptsächlich in `../scalajs-ui/scala/scalajs-ui-editor/src/main/scala-3/ui/editor/`, die Styles in `../scalajs-ui/npm/scalajs-ui/form/NativeEditor.css`. Der Editor verwendet `uiViewport` als direkte Projektabhängigkeit. Ember bleibt für seine eigenständigen Beispiele weiterhin ohne Viewport-Zwang nutzbar.

## Bewusste Unterschiede zum Prototyp

Die native Ansicht unterstützt Absätze, Überschriften, Zitate, Listen, Fett/Kursiv, Inline-Code, Codeblöcke, Links, Bilder und Trennlinien. Der Anwendungs-Codec erhält Bildtitel und die bestehende `{width=N}`-Schreibweise, einschließlich wiederholter URLs mit unterschiedlichen Breiten, Referenzbildern und Codebeispielen. Medien-IDs werden über die Anwendungs-URL aufgelöst; sie sind kein zusätzlicher Markdown-Inhalt.

Tabellen, Raw HTML und zusätzliche Markierungen (`++…++`, `~~…~~`, `==…==`) öffnen in der Markdown-Ansicht. Sie werden nicht als gewöhnliche Absätze importiert und beim nächsten Edit umgeschrieben. `table` bietet deshalb den ausdrücklichen Zugang zum Quelltext; es behauptet keine native Tabellenbearbeitung. Auch nicht darstellbare oder abgewiesene Imports behalten den ursprünglichen Formwert in der sichtbaren Fallback-Ansicht. `Markdown` / `Visuell` wechselt die Ansicht, ohne das Feld auszutauschen.

Die native Bildbreite ist auf 1–100000 Pixel begrenzt. Größere vorhandene Angaben bleiben im Quelltext. Bildgröße wird über den Dialog bearbeitet; der alte DOM-Resize-Griff gehört nicht zur neuen Oberfläche. `menu` und `floating` bieten derzeit eine kompakte Leiste, kein Lexical-Menüsystem oder frei positioniertes Auswahl-Popup. Ohne Plugin-Konfiguration erscheint die Standard-Ribbon. Die alten Scala-Hooks mit Lexical-Objekten und `DialogService.showAsync` entfallen.

## Lokaler Entwicklungsstand und Reproduktion

`simplicity-blog/application/src/main/typescript/package.json` und dessen npm-Lockfile verweisen auf dieselben lokalen UI-Pakete im Nachbar-Checkout. Der bisherige separate Lexical-Import und der `@lexical/code`-Alias wurden entfernt. Es wurde keine neue öffentliche Maven-/npm-Version veröffentlicht.

Aus `scalajs-ember`:

```powershell
sbt --server publishLocal
sbt --server "Test/testOnly *"
```

Danach aus `scalajs-ui`:

```powershell
npm install
sbt --server "scalajs-ui-bridge/fullLinkJS" "Test/testOnly *"
npm run verify --workspace npm/scalajs-ui-editor
npm run verify --workspace npm/scalajs-ui-demo
```

Aus `simplicity-blog/application/src/main/typescript`:

```powershell
npm install
npm run verify
```

Die UI verwendet lokal veröffentlichte Ember-Artefakte `0.1.0-SNAPSHOT`. Für einen frischen Checkout müssen diese zuerst gebaut werden. Ein öffentliches Release benötigt eine abgestimmte Versionierung beider Bibliotheken.

## Nachweise

| Prüfung | Ergebnis |
|---|---|
| Vollständiges Ember-Scala-Gate | 1269 Tests erfolgreich |
| Vollständiges UI-Scala-Gate | 458 Tests erfolgreich |
| npm Editor `verify` | Typecheck, 47 Integrationstests und 3 Tarball-Consumer-Tests erfolgreich |
| npm UI-Demo `verify` | Client/SSR-Build, Eine-Runtime-Nachweis und Seitenprüfungen erfolgreich |
| Simplicity Blog `verify` | Typecheck, Modelle/Stabilisierung, Client/Graal-Build und 32 SSR-Render-Prüfungen erfolgreich |
| Browserprüfung der UI-Demo | Gruppierte Ribbon sichtbar; Linkdialog ist ein Viewport-Fenster; Bestätigen verändert den markierten Text und stellt den Editorfokus wieder her |

Bei der Einbindung wurde ein Ember-Fehler korrigiert: Formatierung konnte ausschließlich Text-Endpunkte verarbeiten. Native Absatz-/Select-all-Auswahlen verwenden auch Container-Endpunkte. `RangeFormatting` verarbeitet nun beide Varianten einschließlich rückwärts gerichteter und gemischter Bereiche. Die neuen Scala-Regressionen und der UI-Medientest decken diesen Pfad ab.

Die lokalen Logs liegen unter `target/native-ember-all-scala.log`, `target/native-ui-final-scala.log`, `target/native-ui-final-npm.log`, `target/native-ui-demo-verify.log` und `target/native-blog-final-verify.log`. Der Demo-Server läuft lokal unter `http://localhost:5174/editor/basics`.

## Noch getrennt offen

Diese Umsetzung ersetzt den Prototyp in der Anwendungseinbindung. Sie erledigt nicht automatisch die weitergehende P29-API mit öffentlichen Session-/Command-/Extension-Handles. Die noch offenen Geräte-/Screenreader-Freigaben aus P28 bleiben ebenfalls offen; weitere manuelle IME-Prüfungen wurden vom Nutzer beendet. Der Blog-Backendserver war bei dieser Prüfung nicht gestartet: ein authentifizierter Speichern/Publizieren-/Medien-Backend-Durchlauf wurde nicht behauptet. Die Backend-Transportverträge wurden nicht geändert.
