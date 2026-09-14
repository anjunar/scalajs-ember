# Tatsächliche Gerätebelege

Tastaturlauf und erste japanische IME-Teilprüfung übernommen; noch keine vollständige Geräteabnahme vorhanden.
Hier liegen von einer Testperson durchgeführte Geräte-Traces mit gesonderter Auswertung.
Automatische Browserläufe und Musterdateien werden nicht als Gerätebelege abgelegt.

Patrick hat anschließend alle weiteren IME-Prüfungen mit positiver Gesamtrückmeldung
(„Das funktioniert alles“) überspringen lassen. Vorhandene Belege bleiben erhalten;
weitere IME-Abnahmen werden ohne neuen Auftrag nicht verfolgt.

- [Patrick, W01 vom 14.09.2026](2026-09-14-patrick-w01-review.md): 213 Ereignisse,
  Textübernahme konsistent, Trace noch vor dem Fix. Sichtbare Cursorbewegung nach
  Leertaste/Enter im Gespräch positiv bestätigt; vollständiger W01 weiterhin offen.
- [Patrick, W02 vom 14.09.2026](2026-09-14-patrick-w02-review.md): echte japanische
  Eingabe und Bestätigung positiv; 133 Ereignisse, konsistente Textübernahme.
  Undo/Redo anschließend separat automatisiert am selben Dokument geprüft.
- [Patrick, W03 vom 14.09.2026](2026-09-14-patrick-w03-review.md): Abbruch und
  Folgetippen bestanden; Wiederholung mit 77 Ereignissen unverändert gesichert
  und ausgewertet, Endtext `Hello aworld`. Erster 44-Ereignis-Trace separat ungesichert.
- [Patrick, W04 vom 14.09.2026](2026-09-14-patrick-w04-review.md): Originaltrace
  mit 108 Ereignissen unverändert gesichert und ausgewertet; Text übernommen und normales `a`
  danach möglich. Fokuswechsel während Composition nicht belegt;
  W04 auf Patricks Wunsch übersprungen, keine Freigabe.

Der [erste Windows-Lauf](../../../ember-integration/browser/windows-keyboard-ime.md)
führt durch Tastatur, Unicode und IME ohne Screenreader. Der Opt-in-Exporter
unter `/toolbar?trace=1` liefert ein manuelles Ergebnisprotokoll im selben JSON
wie die Ereignisse, ergänzt um Versionen und den Hash des ausgeführten Builds.

Vor Übernahme: Testperson/Umgebung/Eingabemethode, Prüfschritt, tatsächliche
Beobachtung und Ergebnis prüfen; nur Testinhalt aufbewahren. Ein fehlender,
abgeschnittener oder ungeprüfter Trace bleibt offen. Das Exportfeld
`reviewStatus` ist keine automatische Freigabe.
