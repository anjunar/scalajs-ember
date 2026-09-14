# P28: Performance, Packaging und offene Freigaben

Stand: 14. September 2026. **P28 ist teilweise umgesetzt.** Automatisierte
Korrektheitsprüfungen, Korpora, Modulgrenzen und reproduzierbare Messungen sind
vorhanden. Physische IME-/Geräte-/Screenreader-Abnahmen fehlen. Dieser Bericht
dokumentiert die Baseline mit UI-Core 1.0.0 einschließlich des großen Move-Timeouts.
Der anschließende [Runtime-Fix](runtime-reorder.md) besteht den 50000-Absatz-Move
in allen drei Engines mit 95–132 ms. Die anschließend veröffentlichte Version
1.0.1 ist inzwischen die Standardabhängigkeit; siehe [Release-Nachweis](ui-core-1.0.1-release.md).

Alle Zahlen, Browser-Versionen, Heap- und Bundlewerte stehen in den
[Messtabellen](results/measurements.md), die Rohwerte in
[acceptance.json](results/acceptance.json). Dies ist eine Arbeitsbaum-Messung auf
Basis der dort genannten Git-Revision, keine Messung eines veröffentlichten Releases.

Bei 100001 Knoten liegt Core+History im Node-Lauf bei p50/p95 **0,079/0,215 ms**,
der synchrone Formstring-Pfad bei **535/612 ms**. Chromium projiziert lokale Edits
mit p95 **0,100 ms**, ohne zusätzliche Mounts/Unmounts oder Strukturmutationen;
nach Dispose und GC fällt der gemessene Heap dort von etwa **255 auf 8 MiB**.
Diese Werte beschreiben genau die unten genannten Messpfade und diese Umgebung.

## Durch die Messungen gefundene Fehler

- Die History-Budgetschätzung traversierte bei jeder lokalen Änderung die ganze
  NodeMap; Merge und explizite Gruppen taten dies ebenfalls. Die Schätzung verwendet
  jetzt die tatsächlich geänderten IDs und deren Vereinigung. Der Regressionstest
  zählt echte Map-Iterator-Aufrufe bei 100001 Knoten: vor der Korrektur 20/38/2
  Durchläufe für zehn Push-/Merge-/Gruppen-Edits, danach jeweils null. Ein nichtlokaler
  Undo-plus-Edit-Pfad bleibt beim vollständigen Vergleich, weil dort ein Netto-Delta
  zum Zustand vor Undo den restaurierten Zwischenzustand nicht beschreibt.
  Ignorierte Zwischenänderungen werden als Kandidaten für einen späteren Merge
  mitgeführt, ohne den Undo-Zustand der ignorierten Änderung zu verändern. Auch
  dieser Fall wird gegen die vollständige Schätzung und auf null Map-Durchläufe geprüft.
- History-Dispose hielt Snapshots über die Extension weiter fest. Die Installation
  gibt bei Session-Dispose nun Stapel, Gruppe und Session-Referenz frei.
- Die Dirty-Queue berechnete Knotentiefe über einen Pfad einschließlich linearer
  Geschwistersuche. Für die Tiefe werden nur die Vorfahren benötigt.
- Der Zusatzkorpus fand HTML-Tabellen, deren Struktur ohne Verlustmeldung entfernt
  wurde. Nicht registrierte Elemente erhalten jetzt eine Unwrap-Diagnose;
  transparente Text-`span` bleiben verlustfrei.
- Reale Eingabe im Minimalprofil zeigte eine ausstehende `selectionchange`-Meldung:
  Der sichtbare Caret war bereits weiter, die kontrollierte Eingabe verwendete
  noch die alte Modellposition. Vor übernommenen Eingaben/Shortcuts wird jetzt
  synchron über den SelectionPort importiert. Dessen Echo-Unterdrückung bleibt
  erhalten, damit die DOM-Repräsentation einer NodeSelection sie nicht durch eine
  generische Range ersetzt. Eine deterministische Race-Probe ergänzt echte Eingabe.

## Methodik

Build: sbt 2.0.8, Scala 3.3.8, Scala.js 1.22.0; lokal Java 25.0.4.1.
Die Linux-CI konfiguriert Java 21 und Node 22; deren Ergebnisse sind separat zu bewerten.

`EditorBench.scala` baut gültige Modelle mit 1001/10001/100001 Knoten, einem
1-Million-Zeichen-Leaf und 32 verschachtelten Listenebenen. Details und Importgrenzen
stehen im [Korpusvertrag](corpora/README.md). Je Fall werden 50 Änderungen aufgewärmt
und anschließend 1000 Änderungen gemessen. Formstring-Fälle verwenden 30 Messungen,
da jeder Commit das gesamte Feld validiert und serialisiert. Der Textumfang bleibt
konstant; nur das erste Zeichen eines mittleren Textlaufs wechselt zwischen x und y.

Die drei Node-Modi trennen Core, Core+History und Core+Formfeld. History pusht jede
Änderung und trimmt auf höchstens 32 Einträge; beim sehr langen Leaf greift zusätzlich
das Bytebudget. Eine separate Vollserialisierung wird fünfmal gemessen. Deren p95
ist bei fünf Samples das Maximum; sie ist keine belastbare Tail-Latenz-Schätzung.
Das geschätzte History-Budget ist **kein** gemessener Heapwert.

Die Browser messen mit einem Worker dieselben lokalen Fälle mit History und echten
UI-Core-NodeViews. Gezählt werden geänderte Nodes, Lifecycle-Aufrufe und
MutationObserver-Einträge. Commitzeit enthält die synchrone Projektion;
`commitToProjected` läuft vom selben Start bis zum Projection-Callback. Die Werte
dürfen nicht addiert oder als reine Paint-/Layoutzeit bezeichnet werden. Mount,
Block-Move und Dispose sind Einzelmessungen; p50/p95 beziehen sich auf lokale Edits.
Ein angezeigtes `0.000 ms`, insbesondere in Firefox, bedeutet eine Messung unterhalb
der verfügbaren Uhr-Auflösung und keine kostenlose Operation.

Heapdaten stammen aus Node `--expose-gc` beziehungsweise Chromium-CDP. Vorher,
montiert und nach Dispose wird GC angefordert. Firefox/WebKit haben hier keine
vergleichbare API; ihre Heapwerte bleiben null. JVM-/Browser-/JIT-Caches und
unterschiedliche GC-Implementierungen begrenzen den Vergleich. Ein leerer Host und
freigegebene History-Referenzen sind zusätzliche Assertions, kein allgemeiner
Leakfreiheitsbeweis. Die abschließenden Messläufe laufen ohne parallelen Build
oder funktionalen Testlauf auf diesem Arbeitsplatz.

## Baseline-Befund mit UI-Core 1.0.0: 50000 Geschwister umordnen

Bereits bei **5000 Geschwistern** dauert der Move in den drei Engines etwa
**1,16 bis 14,06 Sekunden**. Der bestandene Identitätstest für diese Größe ist
deshalb ausdrücklich keine Freigabe für flüssige interaktive Umordnungen.

Das erste Wurzelkind ans Ende einer Liste von 50000 Absätzen zu bewegen, überschritt
in Chromium **240 Sekunden**. Mount und lokale Edits waren zuvor beendet. Der
[archivierte Diagnosebeleg](results/large-move-timeout.json) enthält diese Phasen und
den tatsächlichen Fehler. Für die regulären Timings gelten die später separat
gemessenen Werte, nicht der Diagnose-Durchlauf mit überlappendem Build.

Im ursprünglichen UI-Core-Quellstand ruft `KeyedChildren.reconcile` für jedes bestehende
Kind `Runtime.move` auf. `Runtime.move` bildet und durchsucht wieder die gesamte
Geschwisterliste. Dieses quadratische Muster erklärt die Größenabhängigkeit; die
Runtime besitzt die Umordnung, nicht der Editor. Der Editor verwendet
bei dieser Baseline das veröffentlichte **ui-core 1.0.0**. Es wurde weder eine zweite Ownership-Liste
noch ein direkter DOM-Reorder als Ersatz eingebaut.

Die große Move-Operation liegt als separater Reproduktionstest
in `large-move.spec.mjs`. Der reguläre Messlauf erfasst lokale Edits und Freigabe
auch bei 100001 Knoten, Moves bei 500/5000 Geschwistern. Der separate große Stressfall
ist **kein bestandener CI-Fall mit 1.0.0**. Die gemeinsame Keyed-Reconciliation
ist inzwischen korrigiert und mit einer lokalen Snapshot-Version erneut gemessen;
[Kandidatenbericht und Freigabegrenzen](runtime-reorder.md) halten dies getrennt fest.

Große synchrone Formstrings verursachen ebenfalls deutlich höhere Kosten als ein
lokaler Core-Edit. Die Tabellen weisen diese vollständig aus. Daraus folgt keine
pauschale Empfehlung, Dokumente dieser Größe an einen pro Tastendruck vollständig
serialisierten Formularstring zu binden.

## Packaging und Build

Die Profile text/markdown registrieren nur `ember.rich-text`; standard registriert
zusätzlich List, Link, Image, Code und History. Die Größen stammen aus tatsächlich
gelinkten und separat ausgeführten Anwendungen. Der Textfall ist ein minimales
RichText-Profil mit Text-Codec, keine Zusage eines Strict-Plaintext-Produkts.
Eine Registrierungsliste beweist nicht die Abwesenheit jeder optionalen Klasse
auf dem Compile-Classpath. Erreichbarkeit wird hier durch die realen Linkeroutputs
belegt, nicht durch Quellsuche in generiertem JavaScript.

Die drei Apps werden einzeln geladen. Sie sind **keine** P29-npm-Bridge und dürfen
nicht zu einer Anwendung mit mehreren Scala.js-Runtimes zusammengefügt werden.
Bytezählung, SHA-256 und Kompression schließen Sourcemaps aus; npm-Consumer- und
Tarball-Messungen folgen erst mit P29.

`editorMetadata` exportiert sbt-Projektkanten, aufgelöste Compile-Artefakte, Quellpfade,
Publishstatus und die bestehenden Grenzregeln. Der Prüfer verwendet diese Fakten
für Zyklen, verbotene Imports/Module und private Publish-Abhängigkeiten.
Die CI führt die regulären Scala-/Browser-/No-JS-Gates, Korpora, Bundle- und
Performance-Messungen aus und archiviert `target/p28`.

Vier Full-Link-Anwendungen nach allen Tests überstiegen den bisherigen 2-GB-Buildheap.
Der Build erhält 4 GB und verwendet für Full-Link den Batchmodus, der den jeweiligen
Optimizer anschließend freigibt. Die vorhandenen Full-Link-Optimierungen bleiben
erhalten; Fast-Link bleibt inkrementell.

## Reproduktion

Im Repo-Root; Node und die installierten Harness-/Playwright-Abhängigkeiten sind
Voraussetzung. Firefox unter diesem Windows-Stand verwendet den dokumentierten
Kanal `moz-firefox`; in Linux-CI bleibt der gebündelte Firefox voreingestellt.

```powershell
sbt --server scalafmtCheckAll "Test/testOnly *"
sbt --server "scalajs-ember-integration/fullLinkJS" "scalajs-ember-profile-text/fullLinkJS" "scalajs-ember-profile-markdown/fullLinkJS" "scalajs-ember-profile-standard/fullLinkJS" editorMetadata
node tools/verify-editor-boundaries.mjs
node tools/verify-editor-corpora.mjs
node tools/measure-editor-bundles.mjs
```

Funktionales Browsergate, anschließend die Messungen **nacheinander**:

```powershell
$env:EMBER_FIREFOX_CHANNEL = 'moz-firefox'
$env:PLAYWRIGHT_JSON_OUTPUT_NAME = '../../target/p28/full-browser-report.json'
Push-Location ember-integration/browser
npm run test:server
npm run test:browser -- --reporter=line,json
Pop-Location
node --expose-gc benchmarks/run.mjs
Push-Location ember-integration/browser
npm run test:bench
Pop-Location
node tools/summarize-editor-evidence.mjs
```

Mit `--record` übernimmt der letzte Befehl tatsächlich gemessene Belege nach
`benchmarks/results/`. Ohne diese Option schreibt er ausschließlich nach `target/p28`.
Großer Move, separat; mit der alten Version 1.0.0 mit erwartetem
**nicht erfolgreichen Exitcode**, mit dem Kandidaten und der neuen Standardversion 1.0.1 bestanden:

```powershell
Push-Location ember-integration/browser
npm run test:bench:stress -- --project=chromium
Pop-Location
```

Für physische Geräte und Screenreader gelten die
[Supportmatrix](../ember-integration/browser/support-matrix.md) und
[Abnahmecheckliste](../ember-integration/browser/accessibility-checklist.md).
Ohne deren Ergebnisse wird weder P28 vollständig abgeschlossen noch P29/P30 freigegeben.
