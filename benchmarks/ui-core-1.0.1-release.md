# UI 1.0.1: Maven-Veröffentlichung und Editor-Übernahme

Stand: 14. September 2026. **Alle neun Maven-Module sind als 1.0.1 veröffentlicht.**
Sonatype bestätigt Deployment `dc3942f5-2142-48a5-a69d-83d4eadbce34` mit
`PUBLISHED` und ohne Fehler. Alle neun JARs wurden anschließend direkt von
Maven Central heruntergeladen und über SHA-256 mit dem geprüften Staging verglichen;
auch die öffentlich ausgelieferten POMs nennen 1.0.1.

Der Editor verwendet jetzt standardmäßig **UI-Core 1.0.1**. Der optionale
`ember.uiCore.version`-Override bleibt für zukünftige Kandidaten erhalten.
Die [maschinenlesbaren Release-Belege](results/ui-core-1.0.1-release.json)
enthalten Deployment-Antwort, Artefakt-URLs und Hashes, Quellhashes,
aufgelöste Consumer-Version und die abschließenden Prüfergebnisse.

Öffentlicher Einstieg: [UI-Core 1.0.1 auf Maven Central](https://repo.maven.apache.org/maven2/com/anjunar/scalajs-ui-core_sjs1_3/1.0.1/scalajs-ui-core_sjs1_3-1.0.1.pom).

## Enthaltener Core-Fix

Die [Korrektur großer Block-Moves](runtime-reorder.md) ist in den Source-JARs
enthalten. Vor dem Upload wurden `Runtime.scala` und `KeyedChildren.scala`
bytegenau gegen den geprüften Arbeitsbaum verglichen. Die generische Runtime
behält Ownership und ordnet Hosts mittels LIS mit minimaler Insert-Anzahl.
Sieben Regressionen sichern große Listen, Identität, Guards, virtuelle Grenzen
und Retry nach einem Backendfehler ab.

## Release-Prüfungen

- Version 1.0.1 über `scripts/set-version.mjs` gesetzt und mit `--check` geprüft.
- 454 Scala-Tests, globaler Formatcheck und beide Production-Links bestanden.
- Alle npm-Workspace-Verifies bestanden, einschließlich 333 Vitest-Fällen,
  57 Browserfällen, Tarball-Consumern, Demo-/Landing- und SSR-Prüfungen.
- Neun Maven-Module signiert; POM-Versionen und interne UI-Abhängigkeiten sind
  durchgehend 1.0.1. Core-Source-JAR enthält die geprüften Änderungen.
- Upload über `scripts/publish-central.ps1 -Version 1.0.1 -SkipPublishSigned`
  nach separatem `sbt --server publishSigned` und Prüfung des Staging-Inhalts.

Veröffentlicht werden Core, Router, Viewport, JSON, Controls, Forms, Editor,
WebAuthn und Bridge unter `com.anjunar`, jeweils als `_sjs1_3`-Artefakt.
Der Versionshelfer setzt auch npm-Manifeste konsistent; npm-Pakete wurden in
diesem Auftrag nicht veröffentlicht.

Die vorhandenen Release-Skripte wurden korrigiert: Der Versionshelfer findet
Modul-READMEs unter `scala/` und behandelt den fehlenden generierten Starter
als optional. Das PowerShell-Publish-Skript wartet bei automatischer
Veröffentlichung auch nach `VALIDATED` weiter auf `PUBLISHED`. Die README
nennt jetzt das tatsächlich vorhandene Maven-Skript.

Die historische Snapshot-Messung bleibt unverändert erhalten. Physische
Geräte-/IME-/Screenreader-Prüfungen bleiben unabhängig vom Release offen.

## Editor-Abnahme mit dem veröffentlichten Artefakt

Der normale Build ohne Versionsoverride löst
`com.anjunar:scalajs-ui-core_sjs1_3:1.0.1` auf. 1266 Scala-Tests, alle vier
Production-Links, Formatierung, Serverimport, 11 Korpusfälle und die Grenzen
der 23 Projekte bestehen. Im vollständigen Browserlauf bestehen 802 Fälle;
die zwei bekannten erwarteten Windows-WebKit-Clipboard-Fehler bleiben separat.
Es gibt keine übersprungenen, instabilen oder unerwartet fehlgeschlagenen Fälle.

Alle drei Stressfälle mit 50000 Absätzen / 100001 Modellknoten bestehen:

| Engine | Move-Zeit | DOM-Änderung | Mounts / Unmounts |
| --- | ---: | --- | --- |
| Chromium 153.0.8010.12 | 92,5 ms | ein Host entfernt und wieder eingefügt | 0 / 0 |
| Firefox 155.0.1 | 112 ms | ein Host entfernt und wieder eingefügt | 0 / 0 |
| WebKit 26.6 | 130 ms | ein Host entfernt und wieder eingefügt | 0 / 0 |

Die Identität bleibt erhalten; das bisher erste Element steht anschließend am
Ende. Es sind erneut lokale Einzelmessungen nach 50 Warmups und 1000 Edits,
keine p50/p95- oder Paint-Zeiten. Die ursprünglichen Snapshot-Zahlen bleiben im
separaten Kandidatenbericht erhalten.

Reproduktion im Editor-Root (PowerShell, installierte Harness-Abhängigkeiten):

```powershell
sbt --server "Test/testOnly *" "scalajs-ember-integration/fullLinkJS" "scalajs-ember-profile-text/fullLinkJS" "scalajs-ember-profile-markdown/fullLinkJS" "scalajs-ember-profile-standard/fullLinkJS" editorMetadata scalafmtCheckAll
node tools/verify-editor-boundaries.mjs
node tools/verify-editor-corpora.mjs
$env:EMBER_FIREFOX_CHANNEL = 'moz-firefox'
Push-Location ember-integration/browser
npm run verify
npm run test:bench:stress
Pop-Location
```

`tools/record-ui-release.mjs` verdichtet die lokalen Release-Logs und JSON-Reports
aus `target/ui-release-1.0.1-*` sowie den Stressreport zu den versionierten Belegen.
Es prüft `PUBLISHED`, übereinstimmende Staging-/Central-Hashes, die unveränderten
Core-Quellhashes, aufgelöste Version 1.0.1 und die Testresultate.

## Sonatype-Hinweis zu künftigen Releases

Die erfolgreiche Antwort enthält drei Kontingentwarnungen für `anjunar`:
über 75 % des monatlichen Größenkontingents sowie überschrittene Monatsgrenzen
für Datei- und Releaseanzahl. Laut Antwort beginnt die Durchsetzung am
**1. Oktober 2026**. Dieser Release ist erfolgreich; für weitere Veröffentlichungen
ist der [Sonatype-Nutzungsstand](https://central.sonatype.com/publishing/usage?org=anjunar)
zu berücksichtigen.
