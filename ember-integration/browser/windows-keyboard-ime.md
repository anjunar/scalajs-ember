# P28: erster Windows-Lauf ohne Screenreader

Status: **vorbereitet, noch nicht durch eine Testperson abgenommen**.
Dieser Lauf betrifft Windows-Tastatur und die tatsächlich verwendete IME.
NVDA, VoiceOver, Mobilgeräte und Diktat erhalten dadurch keine Freigabe.

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
| W01 – Tastatur, Auswahl und Unicode | `Hello world` im Editor vollständig markieren und durch den Testtext ersetzen. Mit Pfeilen, Home/End und Shift navigieren/auswählen. Das Emoji und das kombinierte `é` jeweils mit Backspace löschen; Undo/Redo ausführen. | Keine halben Grapheme, verschwundenen Nachbarzeichen oder springende Auswahl. Undo/Redo stellt Text und Auswahl nachvollziehbar wieder her. |
| W02 – IME bestätigen und Undo | Mit der tatsächlich installierten CJK-IME mitten in `Hello world` Testzeichen eingeben, Kandidaten wechseln und bestätigen. Danach einmal Ctrl+Z, dann Ctrl+Y. | Zwischenstände werden nicht doppelt übernommen. Ein Undo entfernt die gesamte bestätigte Composition; Redo stellt sie einmal wieder her. |
| W03 – IME abbrechen | Mitten im Fixture-Text eine Composition beginnen und vor der Bestätigung Escape drücken. Danach ein normales Zeichen eingeben. | Der Abbruch hinterlässt keine halbfertigen Zeichen; normale Eingabe funktioniert anschließend. |
| W04 – Fokuswechsel während IME | Während einer offenen Composition das Feld „Notizen außerhalb des Editors“ anklicken. Danach in den Editor zurückkehren und weiter schreiben. | Keine verlorenen oder doppelten Zeichen, kein dauerhaft blockierter Editor. Den tatsächlichen Bestätigungs-/Abbruchentscheid der IME notieren. |
| W05 – Auswahl ersetzen | `world` auswählen und mit der IME ersetzen. Danach mit einer Auswahl über einen fett markierten Teil wiederholen. | Nur der gewählte Inhalt wird ersetzt. Formatgrenzen und Auswahl bleiben konsistent; Undo stellt die Ersetzung vollständig zurück. |
| W06 – Toolbar und Linkdialog | Ein Wort auswählen, mit Tab die Toolbar erreichen, Pfeile/Home/End verwenden. Linkdialog öffnen, `https://example.com/` eintragen; einmal abbrechen, einmal übernehmen. | Toolbar hat einen Tabstopp, Tab verlässt sie. Dialogfokus bleibt erreichbar; Escape/Übernehmen bringt Fokus und passende Auswahl zurück. |

Für W01 darf der Unicode-Testtext kopiert werden; das prüft keine CJK-IME.
W02–W05 benötigen die echte Windows-Eingabemethode. Fehlt sie, diese Fälle mit
„Nicht durchführbar“ und dem Grund dokumentieren, nicht durch JavaScript ersetzen.
Dead Keys separat mit dem tatsächlich vorhandenen Tastaturlayout wiederholen.

Nach jedem Fall den Trace zur Auswertung bereitstellen. Der DOM-/Modelltext nach
bestätigten Eingaben muss übereinstimmen; Unterschiede während einer laufenden
Composition werden anhand des Ereignisverlaufs bewertet. Screenreader-Ansagen
gehören ausdrücklich nicht zum Ergebnis dieses ersten Laufs.

## Ergebnisablage

Geprüfte, auf Testinhalt beschränkte Traces kommen nach
`benchmarks/corpora/device-traces/`. Die [gesamte Checkliste](accessibility-checklist.md)
und die [Supportmatrix](support-matrix.md) werden erst nach dem tatsächlichen
Operatorbericht aktualisiert. Bisher ist keiner der obigen Fälle manuell bestanden.

Serverstart, falls erforderlich:

```powershell
sbt --server "scalajs-ember-integration/fullLinkJS" editorMetadata
node ember-integration/browser/server.mjs
```

Während eines manuellen Laufs den Linkeroutput nicht neu bauen; sonst einen
neuen Lauf mit neu gestarteter Testseite beginnen.
