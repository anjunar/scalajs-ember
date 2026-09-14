# Belegte Supportgrenze — P28

Stand: 14. September 2026. P28 ist **teilweise umgesetzt, nicht vollständig
abgenommen**. Die automatisierten Belege und ihre Umgebung stehen im
[Messbericht](../../benchmarks/report.md). Eine grüne Protokollprüfung ist keine
Freigabe für physische IMEs oder Screenreader.

| Bereich | Tatsächlicher Nachweis | Grenze |
| --- | --- | --- |
| Core, History, Formate und Forms ohne Browser | Vollständiges Scala-Gate; echte Scala.js-Ausführung | Kein DOM nötig; Vollstring-Felder serialisieren synchron |
| Chromium, Firefox, WebKit auf Windows | Vollständiger Playwright-Lauf einschließlich Selection, Eingabe, Undo, Hydration, Recovery, Clipboard, Toolbar und Dialogen | Firefox lokal über `moz-firefox`/BiDi; WebKit ist kein Safari-Gerät |
| Native Clipboard unter Windows-WebKit | Zwei bekannte erwartete Fehler aus P25 | OS-Clipboard-Lesen sowie der betreffende Copy/Paste-Nachweis bleiben offen |
| SSR und No-JS | Node-Import ohne Browserglobals; Browser mit JavaScript deaktiviert: Textarea, POST, Validierung und Multipart | Keine Behauptung über unbekannte Serveradapter |
| Text-, Markdown-, Standard-Profil | Drei unabhängige Full-Link-Anwendungen; Tastatureingabe und Dispose in allen drei Engines | Messanwendungen; keine npm-Fassade oder gemeinsame P29-Bridge |
| Lokale Textänderungen | 1000 Änderungen nach 50 Warmups; 1001/10001/100001 Nodes, langer Leaf, tiefe Liste; kein Geschwister-Remount | Synchronous Commit/Projektion, keine Paint- oder End-to-End-Eingabelatenz |
| Strukturänderungen | [UI-Core-Fix](../../benchmarks/runtime-reorder.md): 50000-Absatz-Move in drei Engines mit einem DOM-Move und erhaltener Identität; [1.0.1 veröffentlicht und übernommen](../../benchmarks/ui-core-1.0.1-release.md) | Lokale Einzelmessungen; alter 1.0.0-Stand mit Chromium-Timeout nach 240 s bleibt als Baseline dokumentiert |
| Import/Roundtrip | CommonMark-Suite, JSON-/HTML-/Markdown-Limits und versionierter Zusatzkorpus | Tabellen werden aufgelöst und als Verlust diagnostiziert; kein Tabellenmodell |
| NVDA, VoiceOver | Abnahmeanleitung und opt-in Trace-Werkzeug vorhanden | **Offen: keine manuelle Screenreader-Abnahme** |
| Physische Desktop-CJK-IME, Akzente, Dead Keys | Automatisierte Composition-Zustandsmaschinen und Recovery | **Offen: reale Betriebssystemeingabe** |
| Android/Gboard, iOS/Safari, Autokorrektur, Diktat, Touch | [Manuelle Schritte](accessibility-checklist.md) vorbereitet | **Offen: kein physischer Gerätenachweis** |
| Linux-CI | Workflow für Scala, Serverimport, drei Browserengines, Korpora und Messungen eingerichtet | Lokaler Windows-Lauf belegt keinen ausgeführten Linux-CI-Run |
| npm-Consumer und Ablösung | P29/P30 spezifiziert | Noch nicht umgesetzt; P28 erteilt keine Ablösefreigabe |

Aktueller lokaler P28-Folgelauf: **808 bestandene Browserfälle plus zwei bekannte
erwartete Fehler**, 15 Messfälle und drei große Move-Fälle; acht Node-Tests prüfen
die Ablehnung fehlerhafter Stressbelege. Die [Belege mit UI-Core 1.0.1](../../benchmarks/results/p28-ci-1.0.1.json)
halten diese Zahlen getrennt von den historischen Release-Läufen fest.
Als erste manuelle Kombination ist [Windows-Tastatur und IME ohne Screenreader](windows-keyboard-ime.md)
begonnen. Ein Cursorfehler bei Leertaste/Enter wurde gemeldet und
[korrigiert](../../benchmarks/p28-caret-layout.md); die manuelle Bestätigung und
ein vollständiger Operatorbericht stehen weiterhin aus.

## Konfigurierte Standardgrenzen

Zeichenangaben sind UTF-16-Einheiten; Modellgröße und DOM-Größe sind verschiedene
Grenzen. Ein konfigurierbares Importlimit ist kein Performanceversprechen.

| Pfad | Standard |
| --- | --- |
| JSON | 8 Mi Zeichen Quelle; JSON-Tiefe 64; 200000 Arraywerte; 256 Objektfelder; 100000 Nodes; 20000 Kinder je Container; 1000000 Zeichen je Text; Dokumenttiefe 100 |
| Markdown | 4 Mi Zeichen; 200000 Zeilen; Tiefe 100; 100000 Blöcke; 20000000 Arbeitsschritte |
| Markdown-Paste | 256 Ki Zeichen; 10000 Zeilen; Tiefe 24; 5000 Blöcke; 1000000 Arbeitsschritte |
| HTML | 1 Mi Zeichen; 20000 Nodes; Tiefe 100 |
| History | 200 Einträge, geschätzte 8 MiB, Mergefenster 500 ms; die neueste Stufe bleibt auch allein oberhalb des Budgets erhalten |
| Positionsmapping | 64 Revisionen; ältere Bookmarks laufen ab |
| Media | Standardmäßig 10 MiB pro Datei, 8 Dateien je Intent, 8 laufende Operationen, 32 aufbewahrte Resultate; siehe [Medienvertrag](../../ember-forms/MEDIA_SERVICE.md) |

Die 100001-Node-Messung baut ein kontrolliertes Modell direkt und überschreitet
die JSON-Defaults. Sehr große synchrone Formstrings und flache Umordnungen sind
keine freigegebenen interaktiven Einsatzfälle. Der [Messbericht](../../benchmarks/report.md)
enthält die offenen Performancebefunde und ausführbare Reproduktionen.

Vor einer Gerätefreigabe die [Checkliste](accessibility-checklist.md) mit Versions-,
Geräte- und tatsächlichen Eingabenachweisen ausfüllen. Erst diese Ergebnisse können
die offenen Zeilen ändern.
