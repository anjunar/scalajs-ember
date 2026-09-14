# P28: Export ohne stillen Datenverlust

Patrick bestätigt am 14.09.2026, dass der W03-Download auch bei eigener Bedienung
keine Datei liefert. Der alte Tab zeigt weiterhin 44 aufgezeichnete Ereignisse
und den korrekten Endtext `Hello aworld`; er wurde nicht neu geladen.
Die genaue Ursache dieses Ausfalls ist nicht abschließend isoliert.

Der bisherige Export wartete unbegrenzt auf Build-Metadaten, verwendete einen
nicht eingebundenen Download-Link und widerrief dessen Blob-URL bereits im
nächsten Timer. Ein vom Browser unterdrückter Download hatte keinen sichtbaren
Ersatzweg. Diese Schwachstellen sind behoben:

- Build-Anfrage nach fünf Sekunden abbrechen; fehlenden Nachweis ausdrücklich
  im Export und in der Anzeige ausweisen, keine Gerätefreigabe daraus ableiten.
- Vollständiges JSON in einem beschrifteten, schreibgeschützten Textfeld anbieten.
- Sichtbaren Dateilink behalten; Blob-URL erst beim nächsten Export/Lauf ersetzen.
- Während der Vorbereitung keinen neuen Lauf starten. Formular, Dateiname und
  Ereignisse beziehen sich auf denselben vor dem Warten aufgenommenen Snapshot.
- Downloadprobleme sichtbar melden; kopierbares JSON erhalten.
- Beim nächsten Lauf alte Exportanzeige, JSON und Link zurücksetzen.

## Prüfung

12 gezielte Fälle bestanden: Export-/Neustart-Tests und bestehender Operator-Trace
in Chromium, Firefox (`moz-firefox`) und WebKit. Zusätzlich geprüft sind ein
zweiter Download über den erhaltenen Link sowie ein unterdrückter automatischer
Download bei einer nie beantworteten Build-Anfrage. Dabei bleiben Ereignisse
kopierbar, der fehlende Build wird kenntlich gemacht und Änderungen am Formular
verfälschen den bereits aufgenommenen Snapshot nicht.
Log: `target/p28-export-tests.log`. Zwei erste Testläufe scheiterten an
mehrdeutigen bzw. nach Entfernen des href nicht mehr passenden Test-Locators;
nach deren Korrektur ist der gezielte Lauf vollständig grün.

Im Codex In-app Browser zusätzlich separat geprüft: `Hello worlda`, acht
automatisierte Ereignisse, JSON sichtbar und Download
`ember-device-keyboard-2026-09-14T17-10-25.221Z.json` tatsächlich im Downloadordner.
Das ist eine Exportprüfung, keine Geräteabnahme. Der laufende Server und der
kompilierte Editor wurden nicht ersetzt; nur neue Testseiten laden den neuen
Exporter. Der alte W03-Trace bleibt ungesichert in seinem bisherigen Tab.

Nachtrag: Patrick hat den [W03-Wiederholungslauf](corpora/device-traces/2026-09-14-patrick-w03-review.md)
mit dem neuen Exporter erfolgreich heruntergeladen. 77 Ereignisse sind
unverändert gesichert und ausgewertet; W03 ist für diese Kombination bestanden.
