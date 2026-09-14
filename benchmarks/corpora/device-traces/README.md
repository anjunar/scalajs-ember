# Tatsächliche Gerätebelege

Erster Tastaturlauf übernommen; noch keine vollständige manuelle Abnahme vorhanden.
Hier liegen von einer Testperson durchgeführte Geräte-Traces mit gesonderter Auswertung.
Automatische Browserläufe und Musterdateien werden nicht als Gerätebelege abgelegt.

- [Patrick, W01 vom 14.09.2026](2026-09-14-patrick-w01-review.md): 213 Ereignisse,
  Textübernahme konsistent, Trace noch vor dem Fix. Sichtbare Cursorbewegung nach
  Leertaste/Enter im Gespräch positiv bestätigt; vollständiger W01 weiterhin offen.

Der [erste Windows-Lauf](../../../ember-integration/browser/windows-keyboard-ime.md)
führt durch Tastatur, Unicode und IME ohne Screenreader. Der Opt-in-Exporter
unter `/toolbar?trace=1` liefert ein manuelles Ergebnisprotokoll im selben JSON
wie die Ereignisse, ergänzt um Versionen und den Hash des ausgeführten Builds.

Vor Übernahme: Testperson/Umgebung/Eingabemethode, Prüfschritt, tatsächliche
Beobachtung und Ergebnis prüfen; nur Testinhalt aufbewahren. Ein fehlender,
abgeschnittener oder ungeprüfter Trace bleibt offen. Das Exportfeld
`reviewStatus` ist keine automatische Freigabe.
