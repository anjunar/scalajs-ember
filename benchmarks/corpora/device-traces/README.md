# Tatsächliche Gerätebelege

Noch keine manuelle Abnahme vorhanden. Hier liegen ausschließlich von einer
Testperson durchgeführte und anschließend geprüfte Geräte-Traces.
Automatische Browserläufe und Musterdateien werden nicht als Gerätebelege abgelegt.

Der [erste Windows-Lauf](../../../ember-integration/browser/windows-keyboard-ime.md)
führt durch Tastatur, Unicode und IME ohne Screenreader. Der Opt-in-Exporter
unter `/toolbar?trace=1` liefert ein manuelles Ergebnisprotokoll im selben JSON
wie die Ereignisse, ergänzt um Versionen und den Hash des ausgeführten Builds.

Vor Übernahme: Testperson/Umgebung/Eingabemethode, Prüfschritt, tatsächliche
Beobachtung und Ergebnis prüfen; nur Testinhalt aufbewahren. Ein fehlender,
abgeschnittener oder ungeprüfter Trace bleibt offen. Das Exportfeld
`reviewStatus` ist keine automatische Freigabe.
