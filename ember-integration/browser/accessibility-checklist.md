# P28: manuelle Accessibility- und Geräteabnahme

Stand: vorbereitet, **noch keine manuelle Kombination abgenommen**. Automatische
Browsertests sind separat in [support-matrix.md](support-matrix.md) aufgeführt.
Ein fehlendes Gerät oder ein grüner Protokolltest ersetzt kein ausgefülltes Ergebnis.

## Vorbereitung und Beleg

Nach dem Full-Link `node ember-integration/browser/server.mjs` starten und
`http://127.0.0.1:4188/toolbar?trace=1` öffnen. Für echte mobile Geräte muss die
Testseite durch die Testumgebung erreichbar sein; der lokale Loopback-Link allein
ist kein Gerätezugang. Keine Änderung an Firewall oder öffentlichem Hosting ist
Teil dieses Pakets.

Nur den Testtext „Grüße, é, 👩🏽‍💻, 中文, 한글, العربية“ verwenden. Die Trace-Leiste
zeichnet nach **Trace starten** höchstens 2000 native Ereignisse mit Browser-UA,
Eingabetyp, Composition-Status, DOM-/Modelltext und Selection-Offsets auf. Nach
**Trace stoppen** den Trace herunterladen. `trusted: true` allein bestätigt weder
eine Eingabemethode noch einen Screenreader; zusätzlich ist der Operatorbericht nötig.
Traces mit `truncated: true` werden in kürzeren Einzelläufen wiederholt.

Pro Abnahme eine Datei nach `benchmarks/corpora/device-traces/` übernehmen und mit
Gerät, Betriebssystem, Browser-/AT-Version, Eingabemethode, Git-Revision, Datum,
Testperson und bestanden/fehlgeschlagen/offen referenzieren. Vor dem Einchecken
prüfen, dass ausschließlich Testtext enthalten ist. Es werden keine erfundenen
oder aus synthetischen Tests kopierten Geräte-Traces eingecheckt.

## Aufgaben

- [ ] NVDA mit Firefox: Editorname, Textnavigation, Zeichen/Wörter/Absätze,
  Auswahl, markierte Bereiche und leere/dekorative Bilder werden verständlich angesagt.
- [ ] NVDA mit Chromium: dieselben Aufgaben; Browse-/Fokusmodus wechseln ohne Falle.
- [ ] VoiceOver mit Safari/macOS: Rotor, Textnavigation, Auswahl und Bearbeitung;
  Links/Bilder und Status-/Fehlermeldungen sind erreichbar.
- [ ] Toolbar per Tab erreichen, Pfeile/Home/End bedienen, deaktivierte Aktionen
  erkennen; Tab verlässt die Leiste. `mixed`/`pressed` sind sinnvoll angesagt.
- [ ] Link-/Bilddialog öffnen, alle Labels erreichen, Fehler korrigieren,
  Escape/Abbrechen/Übernehmen; Auswahl und Fokus kehren passend zurück.
- [ ] 200 % und 400 % Zoom, Forced Colors/Windows-Kontrastdesign und Reduced Motion:
  kein verdeckter Inhalt, sichtbarer Fokus, Aktionen ohne reine Farbcodierung.
- [ ] Desktop-CJK-IME: Kandidatenwahl, Zwischenstände, Bestätigung und Abbruch;
  ein Undo nimmt die bestätigte Composition als Einheit zurück.
- [ ] Dead Keys/Akzente: deutsche/französische Layouts sowie Unicode-Kombinationen
  inmitten einer Auswahl; Backspace zerstört kein halbes Graphem.
- [ ] Android/Gboard: Auswahlgriffe, CJK und lateinische Eingabe, Vorschläge,
  Autokorrektur, Enter/Backspace, Undo nach bestätigter Eingabe.
- [ ] iOS/Safari: Autokorrektur, koreanische 10-Tasten-Eingabe, Diktat,
  Auswahlgriffe, Löschen einer Auswahl und Wechsel zur Toolbar.
- [ ] Windows-Spracherkennung bzw. macOS/iOS-Diktat: Ersatz vorhandener Wörter,
  Interpunktion, Korrektur und Undo, jeweils DOM gegen Modelltext prüfen.
- [ ] Physisches Copy/Cut/Paste zwischen Editor und fremder Anwendung sowie
  Datei-/Text-Drag, einschließlich Touch falls das Gerät diesen Pfad unterstützt.

Die detaillierten Composition-Schritte stehen weiterhin in [manual-ime.md](manual-ime.md).
No-JS-POST/Reset/Fehler/Multi­part sind automatisiert; die Verständlichkeit mit
Screenreader gehört trotzdem zum jeweiligen Gerätebericht.

## Ergebnisvorlage

| Feld | Eintrag |
| --- | --- |
| Gerät, OS, Browser, AT und Versionen | offen |
| Eingabemethode / Sprache | offen |
| Git-Revision und Datum | offen |
| Testperson | offen |
| Aufgaben / Ergebnis | offen |
| Trace-Datei / Beobachtungen | offen |

Die Produktfreigabe für die jeweilige Kombination bleibt gesperrt, bis dieser
Bericht ausgefüllt und relevante Fehler behoben sind. P29/P30 erhalten hierdurch
keine vorgezogene Geräte- oder Ablösungsfreigabe.
