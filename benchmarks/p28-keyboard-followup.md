# P28: Tastatur-Folgelauf im Codex-Browser

Am 14. September 2026 wurde eine frische Testseite auf dem aktuellen Build durch
Codex bedient. **Browserautomatisierung, keine physische Geräteabnahme.**
Patricks vorheriger Tab und sein Originaltrace blieben erhalten.

## Build und Beleg

- Revision: `0381575e6c759875a7ee2aa2d80b8a7d13a8a270`, dirty=false beim Laden.
- Integration-SHA-256: `0973505cfd8f5239a6e60299887b9d2c02327b983716f273ef90f1dc047e85f4`.
- UI-Core 1.0.1; Codex In-app Browser, UA `Chrome/152.0.0.0`, Sprache `de`.
- [Unveränderter Export](results/p28-codex-keyboard-2026-09-14.json), SHA-256
  `b601bed4b2432cbd0994c808a082e8e82f19810da777be67910876564f1329a5`.
- Aufzeichnung: 17:39:42.728–17:42:50.797 MESZ; 121 Ereignisse, nicht abgeschnitten.
  Alle Vorher-/Nachher-Textsnapshots zeigen identischen DOM- und Modelltext.

Der Exporter verwendet weiterhin das allgemeine Format `operator-device-trace`.
Dieser konkrete Beleg ist anhand von `operator.tester`, `operator.inputMethod`
und den Notizen ausdrücklich **automatisiert** und liegt deshalb unter `results/`,
nicht im Korpus manueller Gerätebelege. `outcome=passed` bezeichnet nur die unten
ausgeführten Prüfungen. `trusted=true` macht daraus keinen menschlichen IME-Lauf.

## Beobachtete Ergebnisse

| Ablauf | Ergebnis im Codex-Browser |
| --- | --- |
| Unicode-Testtext einsetzen | `Grüße, é, 👩🏽‍💻, 中文, 한글, العربية` unverändert dargestellt |
| Backspace hinter `👩🏽‍💻` | Gesamtes Graphem einschließlich Hautfarbe und ZWJ entfernt; Nachbartext erhalten |
| Strg+Z und Strg+Y | Emoji vollständig wiederhergestellt bzw. erneut entfernt |
| Backspace hinter `é` | Basiszeichen und kombinierter Akzent gemeinsam entfernt; Undo stellt beide wieder her |
| Home, fünfmal Shift+Rechts | Sichtbar genau `Grüße` markiert; Ersetzen mit `Hallo` lässt den restlichen Text unverändert |
| End, Enter, Shift+Enter | Neuer Absatz und anschließende zusätzliche Umbruchzeile; nativer Cursor im Browserbild sichtbar |
| Undo über die Strukturänderungen | Ursprünglicher Unicode-Text vollständig wiederhergestellt |
| Toolbar mit Tab, End, Home und Pfeilen | Ein Tabstopp; End erreicht Bild, Home den Anfang, Pfeile die folgenden Aktionen |
| Linkdialog per Enter, Escape | Adressfeld fokussiert; Abbruch gibt Fokus an die Linkaktion zurück |
| Linkdialog per Tastatur übernehmen | Nur `Grüße` erhält `https://example.com/`; Fokus wieder auf der Linkaktion |
| Tab aus der Toolbar, Shift+Tab zurück | Toolbar wird verlassen; Rückkehr zum Editor möglich |
| Undo der Linksetzung | Link entfernt, Unicode-Text unverändert |

Die Tabellenbeobachtungen stammen aus den sichtbaren Browserzuständen und
Browserbildern. Der Trace enthält nur Editorereignisse; Toolbar-/Dialogfokus und
Pixelpositionen werden durch das Exportformat nicht vollständig abgebildet.
Es wurden keine Editor-Kommandos über versteckte Test-APIs aufgerufen.

## Undo-Grenze bei einer Bereichsersetzung

Das erste Zeichen `H` ersetzt die Auswahl `Grüße` in einer eigenen Undo-Stufe.
Das anschließende Tippen von `allo` bildet eine weitere Gruppe. Einmal Undo
entfernt daher zunächst `allo`; ein weiteres Undo stellt `Grüße` wieder her.
Dieses Verhalten entspricht der vorhandenen Bereichsersetzungsgrenze in
`HistoryGrouping.scala` und `HistorySpec` ("A range replacement ... be a boundary").
Es wurde geprüft und dokumentiert, nicht als neuer Datenverlust eingestuft.

## Nächste physische Prüfung

Patrick meldete zunächst keine installierte CJK-IME. Anschließend bestätigt er am
14. September 2026: **Japanisch in Windows installiert.** Die frische W02-Seite
steht nun auf „Offen“ und ist für Microsoft IME/Hiragana vorbereitet. Die
Aufzeichnung war zu diesem Zeitpunkt noch nicht gestartet.

Nachtrag: Patrick hat die [erste echte japanische IME-Teilprüfung](corpora/device-traces/2026-09-14-patrick-w02-review.md)
anschließend positiv bestätigt. 133 Ereignisse belegen die Eingabe/Bestätigung;
Undo/Redo am selben Dokument ist separat automatisiert bestanden. Vollständiger
manueller W02 und W03–W05 bleiben offen.

Nach Installation genaue IME/Sprache eintragen, Trace starten und mitten in
`Hello world` Kandidaten wählen/bestätigen. Anschließend Strg+Z und Strg+Y prüfen.
Danach folgen getrennte Läufe für Abbruch, Fokuswechsel und Auswahlersetzung.
Die menschlichen W01-Restprüfungen und W06 bleiben ebenfalls offen; dieser
automatisierte Folgelauf erteilt keine zusätzliche manuelle Freigabe.
