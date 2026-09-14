# P28: große Block-Moves im UI-Core korrigiert

Stand: 14. September 2026. Der Fix liegt im Arbeitsbaum von `../scalajs-ui` und
wurde als **1.0.1-p28-SNAPSHOT ausschließlich lokal** veröffentlicht und im Editor
geprüft. Anschließend wurde der Fix als **1.0.1 auf Maven Central veröffentlicht**
und in den normalen Editor-Build übernommen; siehe [Release-Nachweis](ui-core-1.0.1-release.md).
Die [ursprüngliche Messung](report.md) bleibt als Vergleich erhalten; die
[neuen Rohwerte](results/runtime-reorder.json) enthalten Versionen, Quellhashes,
Linkeroutput-Hash, Arbeitsbaum-Basisrevisionen und Prüfergebnisse.

## Verhalten und Messung

Gemessen wird das Verschieben des ersten Absatzes ans Ende derselben flachen
Liste. 5000 Absätze ergeben mit Textkindern und Wurzel 10001 Modellknoten;
50000 Absätze ergeben 100001 Knoten.

| Engine | 5000 Absätze, 1.0.0 | 5000 Absätze, Kandidat | 50000 Absätze, Kandidat |
| --- | ---: | ---: | ---: |
| Chromium 153.0.8010.12 | 14062,4 ms | 14 ms | 95,1 ms |
| Firefox 155.0.1 | 2585 ms | 13 ms | 118 ms |
| WebKit 26.6 | 1159 ms | 19 ms | 132 ms |

Der ursprüngliche Chromium-Lauf mit 50000 Absätzen überschritt das
[240-Sekunden-Zeitlimit](results/large-move-timeout.json). Für Firefox und WebKit
gibt es bei dieser Größe keinen ursprünglichen Zeitwert. Alle drei neuen
Stressläufe beweisen: genau ein entferntes und wieder eingefügtes DOM-Element,
dieselbe Host-Identität, null Mounts/Unmounts und das ehemalige erste Element am Ende.

Umgebung: Windows 11 (10.0.26200), Intel Core i7-14700KF, Node 26.4.0;
Production-Full-Link mit Scala 3.3.8 / Scala.js 1.22.0. Die Move-Zeiten sind
**Einzelmessungen**, keine p50/p95 oder Paint-Latenzen. Vor dem Move laufen
50 Warmups und 1000 lokale Edits. Die eigenen Builds und Tests liefen vor den
Messungen; eine vollständig unbelastete Maschine wird nicht behauptet.

## Ursache und Korrektur

`KeyedChildren` rief bisher für jedes bestehende Kind `Runtime.move` auf, das
wiederholt die gesamte Geschwisterliste erzeugte und durchsuchte. Jetzt werden
Werte aktualisiert und neue Hosts angehängt; anschließend ordnet Runtime die
vollständige Kindpermutation in einem Durchgang an.

Runtime bestimmt eine längste steigende Teilfolge der bisherigen Positionen
(LIS) und lässt diese Hosts stehen. Die Planung kostet O(n log n), benötigt O(n)
temporären Speicher und führt genau n−LIS Host-Inserts aus. Die Rotation braucht
dadurch einen Insert. Die Kosten eines einzelnen Backend-Inserts kommen hinzu;
insbesondere wird für beliebige SSR-Permutationen keine O(n log n)-Gesamtlaufzeit
versprochen. `Runtime.move` erkennt außerdem unveränderte Positionen vor seiner
Listenallokation.

Runtime bleibt alleiniger Besitzer der Kinderliste. Guards und Renderkontexte
werden vor den Inserts geprüft. Temporäre Indexverknüpfungen halten bereits
erfolgreiche Inserts fest: Lehnt ein Backend einen späteren Insert vor dessen
Mutation ab, entspricht die logische Reihenfolge weiterhin dem physischen Baum.
Ein erneuter Aufruf kann die Umordnung abschließen. Update-/Factory-Callbacks
bleiben wie bisher nicht transaktional. Es gibt keinen Editor-eigenen DOM-Reorder.

## Tatsächliche Prüfung

| Gate | Ergebnis |
| --- | --- |
| UI: vollständiges Scala-Gate | 452 bestanden, einschließlich 7 neuer Reorder-Tests |
| Editor: vollständiges Scala-Gate | 1266 bestanden |
| UI: drei Browserengines | 57 bestanden |
| Editor: drei Browserengines | 802 bestanden; 2 bekannte erwartete Windows-WebKit-Clipboard-Fehler |
| Reguläre Browser-Messfälle / großer Move | 15 / 3 bestanden |
| npm UI-Core | Typecheck, 114 Tests und 8 Tarball-Consumer-Tests bestanden |
| npm UI-Demo | Typecheck, Client-/SSR-/Pages-Build und Eine-Runtime-Nachweis bestanden |
| Editor-Pakete | 4 Full-Links, Serverimport, 11 Korpusfälle und Grenzen der 23 Projekte bestanden |
| Formatierung | Editor und UI-Core Compile/Test bestanden; globales UI-Gate scheitert an parallel geänderter `TableView.scala` |

Die generischen Regressionen prüfen 500/50000 Geschwister, 100 deterministische
Permutationen gegen eine unabhängige Minimal-Insert-Referenz, Einfügen/Löschen,
virtuelle Endanker, Guard-Ablehnung, Backendfehler mit Retry sowie ungültige
Permutationen. Bestehende Transfer-, Ownership- und Browserverträge laufen im
vollständigen Gate mit. Globale UI-Formatierung ist vor einem gemeinsamen Commit
erneut zu prüfen; die parallelen Tabellenänderungen gehören nicht zu diesem Fix.

## Reproduktion des Kandidaten

PowerShell, zunächst im Editor-Root. Installierte npm-/Playwright-Abhängigkeiten
werden vorausgesetzt. Der Versionsoverride gilt nur für den jeweiligen sbt-Aufruf;
ein späterer Build ohne Property verwendet inzwischen das veröffentlichte 1.0.1.

```powershell
Push-Location ../scalajs-ui
sbt --server 'set uiCore / version := \"1.0.1-p28-SNAPSHOT\"' "scalajs-ui-core/publishLocal"
sbt --server "Test/testOnly *" *> ../scalajs-ember/target/p28-runtime-scala.log
sbt --server "scalajs-ui-bridge/fullLinkJS" "scalajs-ui-core-browser-tests/fullLinkJS"
sbt --server "scalajs-ui-core/Compile/scalafmtCheck" "scalajs-ui-core/Test/scalafmtCheck" *> ../scalajs-ember/target/p28-runtime-core-format.log
sbt --server scalafmtCheckAll *> ../scalajs-ember/target/p28-runtime-format.log
npm run verify --workspace npm/scalajs-ui-core *> ../scalajs-ember/target/p28-runtime-npm-core.log
npm run verify --workspace npm/scalajs-ui-demo *> ../scalajs-ember/target/p28-runtime-npm-demo.log
$env:EMBER_FIREFOX_CHANNEL = 'moz-firefox'
$env:PLAYWRIGHT_JSON_OUTPUT_NAME = Join-Path (Get-Location) '../scalajs-ember/target/p28-runtime-browser.json'
npm run test --workspace npm/scalajs-ui-core-browser-tests -- --reporter=line,json *> ../scalajs-ember/target/p28-runtime-browser.log
Pop-Location

sbt --server "-Dember.uiCore.version=1.0.1-p28-SNAPSHOT" "Test/testOnly *" "scalajs-ember-integration/fullLinkJS" "scalajs-ember-profile-text/fullLinkJS" "scalajs-ember-profile-markdown/fullLinkJS" "scalajs-ember-profile-standard/fullLinkJS" editorMetadata scalafmtCheckAll *> target/p28-runtime-editor-scala.log
node tools/verify-editor-boundaries.mjs
node tools/verify-editor-corpora.mjs
Push-Location ember-integration/browser
npm run test:server
$env:PLAYWRIGHT_JSON_OUTPUT_NAME = '../../target/p28-runtime-editor-browser.json'
npm run test:browser -- --reporter=line,json *> ../../target/p28-runtime-editor-browser.log
Remove-Item Env:PLAYWRIGHT_JSON_OUTPUT_NAME
npm run test:bench
npm run test:bench:stress
Pop-Location
node tools/summarize-runtime-reorder.mjs
```

Der Zusammenfasser benötigt die oben benannten Logs und Reports und
prüft die tatsächlich aufgelöste Kandidatenversion. Er überschreibt ausschließlich
`results/runtime-reorder.json`, nicht die versionierte 1.0.0-Baseline.

## Noch offene Freigabe

Die Veröffentlichung als UI-Core 1.0.1 und die normale Dependency-Umstellung
sind inzwischen erfolgt und im [Release-Nachweis](ui-core-1.0.1-release.md)
festgehalten. Die physischen
IME-/Geräte-/Screenreader-Nachweise aus der [Supportmatrix](../ember-integration/browser/support-matrix.md)
bleiben ebenfalls offen. P28 ist weiterhin teilweise umgesetzt; P29/P30 wurden
mit dieser Korrektur nicht begonnen.
