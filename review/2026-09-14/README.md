# Repro-Fälle zum Review vom 14. September 2026

Reproduktionsfälle zum internen Review vom 14. September 2026, ursprüngliche Review-Basis
`8b3092f`. Alle 14 Befunde sind inzwischen korrigiert. Die Dateien unter `scala/`
archivieren die ursprünglichen Reproduktionen; die weiterentwickelten Regressionstests
liegen in den regulären Testquellen der jeweiligen Module.

## Scala

Vom Repository-Root in PowerShell:

```powershell
sbt --server "Test/testOnly *ReviewProbe *ReviewFormatProbeSpec *ReviewFormatDepthProbeSpec"
```

Das aktuelle `probes.sbt` enthält denselben Testaufruf für eine interaktive sbt-Sitzung;
es bindet keine zusätzlichen Quellverzeichnisse mehr ein. Der obige direkte Aufruf
erhält auch bei einem Testfehler einen eindeutigen Exitcode. Die ursprünglichen 16
Repro-Assertions schlugen auf `8b3092f` fehl. Die regulären Fassungen prüfen jetzt
die Korrekturen und zusätzliche Randfälle.

| Suite | Befunde |
| --- | --- |
| `MediaReviewProbe` | R01: externe Quelle trotz Host-Allowlist / internalOnly |
| `LinkReviewProbe` | R04: Textumordnung; R08: Enter im Link |
| `ListReviewProbe` | R07: Auswahl über mehrere ListItems |
| `HistoryReviewProbe` | R09: Undo-Stapel nach abgebrochener Transaktion |
| `BookmarkReviewProbe` | R10: Bookmark nach Edit und Undo |
| `ReviewFormatProbeSpec` | R05: drei Markdown-Roundtrips; R06: kleines Tiefenlimit; R11: vier HTML-Fälle |
| `ReviewFormatDepthProbeSpec` | R06: 18.001 Zeichen verursachen einen Stackoverflow; erwartet wird ein typisierter Fehler |

Die archivierte Markdown-Whitespace-Probe verglich zusätzlich die Anzahl der Textläufe.
Der reguläre Test prüft Text und Marks unabhängig von einer erlaubten Aufteilung an
Entity-Grenzen; so wird der semantische Erhalt geprüft.

## Browser

Voraussetzungen: Node, installierte Harness-Abhängigkeiten und Playwright-Chromium
gemäß [Harness-README](../../ember-integration/browser/README.md). Vom Repository-Root:

```powershell
sbt --server "scalajs-ember-integration/fullLinkJS"
node review/2026-09-14/browser-probes.mjs
```

Das Skript startet den vorhandenen lokalen Fixture-Server auf Port 4188, führt die vier
Chromium-Fälle aus und beendet den eigenen Server wieder. Der Port muss frei sein;
nicht gleichzeitig mit der regulären Playwright-Suite starten. Auf der ursprünglichen Basis
lautete das Ergebnis `4/4 review regressions reproduced`, Exitcode 1. Nach Korrektur
werden 0/4 und Exitcode 0 erwartet. Es gibt pro Fall
Soll und Ist als JSON aus:

- `word-delete`: R12, echter Tastendruck Ctrl+Backspace.
- `stale-input-claim`: R02, echte erste Eingabe, anschließend synthetischer Native-Input-Pfad.
- `foreign-composition`: R13, synthetisches Composition-Event eines nativen Atom-Felds.
- `replaced-text-host`: R03, Text-Host-Klon und anschließende echte Tastatureingabe.

Das Skript ist kein Nachweis realer IME-/Geräteunterstützung. Für den vollständigen
vorhandenen Browser-Testlauf gelten weiterhin die Befehle im Harness-README.
Die acht regulären Fälle in `ember-integration/browser/test/review-regressions.spec.mjs`
laufen in allen drei Engines. Die Wortlöschungsprüfung berücksichtigt das NBSP,
mit dem native `contenteditable`-Eingabe den verbleibenden nachlaufenden Leerraum erhält.
