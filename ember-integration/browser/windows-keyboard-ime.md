# P28: erster Windows-Lauf ohne Screenreader

Status: **begonnen; Cursor nach Leertaste/Enter laut Patrick korrekt; W01 insgesamt offen**.
Dieser Lauf betrifft Windows-Tastatur und die tatsächlich verwendete IME.
NVDA, VoiceOver, Mobilgeräte und Diktat erhalten dadurch keine Freigabe.

Aktueller Stand vom 14. September 2026: **Japanische Eingabe und Bestätigung laut
Patrick bestanden.** Der [W02-Trace samt Auswertung](../../benchmarks/corpora/device-traces/2026-09-14-patrick-w02-review.md)
belegt die Eingabe von `日本語`; Undo/Redo am selben Dokument ist ergänzend
automatisiert bestanden. Die vollständige manuelle W02-Vorgabe und W04–W05 bleiben offen.
Für [W03](../../benchmarks/corpora/device-traces/2026-09-14-patrick-w03-review.md)
sind Abbruch und Folgetippen bestanden (`Hello aworld`): Patricks positiver Bericht
und der gesicherte Wiederholungstrace mit 77 Ereignissen sind ausgewertet.
Der [erste W04-Lauf](../../benchmarks/corpora/device-traces/2026-09-14-patrick-w04-review.md)
zeigt übernommene japanische Eingabe und normales Folgetippen, jedoch kein Focusout
während Composition. **Patrick lässt W04 überspringen; keine Freigabe.**
Anschließend beendet Patrick die weiteren IME-Prüfungen ausdrücklich:
**„Überspringe mal komplett IME. Das funktioniert alles“**.
Diese positive Gesamtrückmeldung ist festgehalten. Die verbliebenen IME-Prüfungen
(W02-Restumfang, W04, W05) werden übersprungen; der vorbereitete W05-Trace ist
gestoppt. Vorhandene Einzelfallbelege bleiben erhalten; keine weiteren IME-Läufe
oder IME-Rückfragen ohne neuen Auftrag.
Ein [automatisierter Folgelauf im aktuellen Codex-Browser](../../benchmarks/p28-keyboard-followup.md)
prüft Unicode, Auswahl, Undo/Redo und Toolbar erfolgreich; er ersetzt die
menschlichen Restprüfungen nicht.

## Vorbereitung

1. Die [Testseite](http://127.0.0.1:4188/toolbar?trace=1) im normalen Windows-Browser
   öffnen. Der Testserver muss laufen. Ein Neuladen setzt das Dokument auf
   den Fixture-Text `Hello world` zurück.
2. Unter **Manuelles Ergebnisprotokoll** Testkürzel, Gerät/Windows-/Browser-Version
   und die genaue Eingabemethode samt Sprache eintragen. Auch „deutsches Layout,
   keine CJK-IME installiert“ ist eine gültige Angabe; CJK-Fälle bleiben dann offen.
3. Pro Prüfschritt einen eigenen Trace aufnehmen. **Trace starten** setzt das
   Ergebnis auf „Offen“ und fokussiert den Editor. Nach der Eingabe **Trace stoppen**,
   Ergebnis und Beobachtung eintragen und **Trace herunterladen** drücken.
4. Vor dem nächsten Fall die Seite neu laden. Die Beobachtung benennt die
   tatsächlich ausgeführten Eingaben und das Ergebnis, nicht nur „geht“.

Falls kein Download erscheint: Nach „Trace herunterladen“ den sichtbaren Link
„Trace-Datei speichern“ oder das Feld „Trace-JSON zum Kopieren“ verwenden.
Erst nach Sicherung des JSON neu laden. Fehlende Build-Angaben werden ausdrücklich
gemeldet; der Trace kann trotzdem gesichert werden, die Geräteabnahme bleibt offen.
Diese [Exportkorrektur](../../benchmarks/p28-trace-export.md) gilt für neu geladene Seiten;
den alten W03-Tab mit ungesicherter Aufzeichnung deshalb vorerst erhalten.

Erlaubter Testinhalt: der Fixture-Text `Hello world` sowie
`Grüße, é, 👩🏽‍💻, 中文, 한글, العربية`. Für Linktests `https://example.com/`
verwenden. Keine eigenen Dokumente, Kennwörter oder privaten Clipboard-Inhalte einsetzen.

Der Export enthält Build-Hash, Git-Revision und Dirty-Status, UI-Core-Abhängigkeit,
Browser-UA, Zeitangaben, das manuelle Protokoll und höchstens 2000 Ereignisse.
`reviewStatus: requires-human-review` bleibt auch bei einem eingetragenen
„Bestanden“ erhalten. Ein Prüfer bewertet den Bericht; `isTrusted` ersetzt diese
Bewertung nicht. Bei `truncated: true` den Schritt in kürzeren Läufen wiederholen.

## Prüfschritte

| Schritt | Bedienung | Erwartete Beobachtung |
| --- | --- | --- |
| W01 – Tastatur, Auswahl und Unicode | Am Ende von `Hello world` dreimal Leertaste, dann dreimal Enter drücken; die Cursorposition jeweils **vor** dem nächsten Buchstaben beobachten. Auch Shift+Enter und eine Leertaste in der leeren Zeile prüfen. Danach vollständig durch den Testtext ersetzen. Mit Pfeilen, Home/End und Shift navigieren/auswählen. Das Emoji und das kombinierte `é` jeweils mit Backspace löschen; Undo/Redo ausführen. | Jede Leertaste rückt den Cursor sichtbar weiter; Enter/Shift+Enter zeigen sofort eine neue Cursorzeile. Keine halben Grapheme, verschwundenen Nachbarzeichen oder springende Auswahl. Undo/Redo stellt Text und Auswahl nachvollziehbar wieder her. |
| W02 – IME bestätigen und Undo | Mit der tatsächlich installierten CJK-IME mitten in `Hello world` Testzeichen eingeben, Kandidaten wechseln und bestätigen. Danach einmal Ctrl+Z, dann Ctrl+Y. | Zwischenstände werden nicht doppelt übernommen. Ein Undo entfernt die gesamte bestätigte Composition; Redo stellt sie einmal wieder her. |
| W03 – IME abbrechen | Mitten im Fixture-Text eine Composition beginnen und vor der Bestätigung Escape drücken. Danach ein normales Zeichen eingeben. | Der Abbruch hinterlässt keine halbfertigen Zeichen; normale Eingabe funktioniert anschließend. |
| W04 – Fokuswechsel während IME | Während einer offenen Composition das Feld „Notizen außerhalb des Editors“ anklicken. Danach in den Editor zurückkehren und weiter schreiben. | Keine verlorenen oder doppelten Zeichen, kein dauerhaft blockierter Editor. Den tatsächlichen Bestätigungs-/Abbruchentscheid der IME notieren. |
| W05 – Auswahl ersetzen | `world` auswählen und mit der IME ersetzen. Danach mit einer Auswahl über einen fett markierten Teil wiederholen. | Nur der gewählte Inhalt wird ersetzt. Formatgrenzen und Auswahl bleiben konsistent; Undo stellt die Ersetzung vollständig zurück. |
| W06 – Toolbar und Linkdialog | Ein Wort auswählen, mit Tab die Toolbar erreichen, Pfeile/Home/End verwenden. Linkdialog öffnen, `https://example.com/` eintragen; einmal abbrechen, einmal übernehmen. | Toolbar hat einen Tabstopp, Tab verlässt sie. Dialogfokus bleibt erreichbar; Escape/Übernehmen bringt Fokus und passende Auswahl zurück. |

Für W01 darf der Unicode-Testtext kopiert werden; das prüft keine CJK-IME.
W02–W05 benötigen die echte Windows-Eingabemethode. Fehlt sie, diese Fälle mit
„Nicht durchführbar“ und dem Grund dokumentieren, nicht durch JavaScript ersetzen.
Dead Keys separat mit dem tatsächlich vorhandenen Tastaturlayout wiederholen.

Für den ersten japanischen W02-Lauf: Trace starten, im Editor mit Win+Leertaste
Japanisch / Microsoft IME wählen und den Hiragana-Modus (`あ`) aktivieren.
Mit Romaji-Eingabe `nihon` tippen (`にほん`), mit Leertaste umwandeln, gegebenenfalls
erneut Leertaste für die Kandidatenliste drücken und `日本` mit Enter bestätigen.
Danach einmal Strg+Z, dann Strg+Y: Die bestätigte Composition soll als Einheit
verschwinden und wiederkehren. Anschließend Trace stoppen und herunterladen.
`にほん` und `日本` sind dafür ebenfalls erlaubter Testinhalt.
Die Bedienung der Kandidatenliste folgt der
[Microsoft-IME-Dokumentation](https://support.microsoft.com/en-us/windows/hardware/input-devices/microsoft-japanese-ime).

Nach jedem Fall den Trace zur Auswertung bereitstellen. Der DOM-/Modelltext nach
bestätigten Eingaben muss übereinstimmen; Unterschiede während einer laufenden
Composition werden anhand des Ereignisverlaufs bewertet. Screenreader-Ansagen
gehören ausdrücklich nicht zum Ergebnis dieses ersten Laufs.

## Ergebnisablage

Erste Rückmeldung vom 14. September 2026: Leertaste und Enter ließen den sichtbaren
Cursor stehen; erst ein Buchstabe ließ ihn nachspringen. Der Fehler wurde in allen
drei Browser-Engines reproduziert. Die Korrektur und ihre automatisierte Prüfung
stehen im [Cursorbefund](../../benchmarks/p28-caret-layout.md). Der
[erste Trace samt Auswertung](../../benchmarks/corpora/device-traces/2026-09-14-patrick-w01-review.md)
ist übernommen: 213 Ereignisse, noch vor dem Fix; Browserangaben widersprüchlich.
Patrick bestätigt im Gespräch für den Nachtest von Leertaste/Enter:
„Cursor folgt jetzt korrekt“. Diese positive Teilprüfung ist separat festgehalten;
ein neuer Trace zum Nachtest und die vollständige W01-Abnahme fehlen weiterhin.

Geprüfte, auf Testinhalt beschränkte Traces kommen nach
`benchmarks/corpora/device-traces/`. Die [gesamte Checkliste](accessibility-checklist.md)
und die [Supportmatrix](support-matrix.md) werden erst nach dem tatsächlichen
Operatorbericht aktualisiert. W01 und W02 haben positive Teilbefunde;
W03 ist für die dokumentierte Windows-/Codex-Browser-Kombination bestanden.

Serverstart, falls erforderlich:

```powershell
sbt --server "scalajs-ember-integration/fullLinkJS" editorMetadata
node ember-integration/browser/server.mjs
```

Während eines manuellen Laufs den Linkeroutput nicht neu bauen; sonst einen
neuen Lauf mit neu gestarteter Testseite beginnen.
