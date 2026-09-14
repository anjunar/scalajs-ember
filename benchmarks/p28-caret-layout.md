# P28: Cursor nach Leerzeichen und Zeilenwechsel

Am 14. September 2026 meldete Patrick beim ersten Windows-Tastaturlauf:
Leertaste und Enter lassen den sichtbaren Cursor stehen; beim nächsten Buchstaben
springt er an die richtige Stelle. Zur ersten Meldung lag zunächst kein Geräte-Trace
vor. Ein Trace und die spätere positive Rückmeldung sind inzwischen im manuellen
Nachtrag unten verknüpft. W01 insgesamt ist noch nicht abgenommen.

## Ursache und Korrektur

Die native Selection zeigte nach einem Leerzeichen bereits den richtigen Offset.
Durch `white-space: normal` blieb seine sichtbare Position jedoch unverändert.
Ein mit Enter erzeugter leerer Absatz hatte eine Höhe von null. Auch Shift+Enter
am Absatzende lieferte keine sichtbare neue Cursorzeile.

`HtmlShape.Element.textBlock` kennzeichnet jetzt Absätze, Überschriften und Codeblöcke.
Im Editorprofil erhält deren UI-Container `white-space: pre-wrap`. Bei leerem Inhalt
oder einem abschließenden Umbruch ergänzt die Projektion ein über die UI-Runtime
verwaltetes `br[data-ember-caret]`. Die Renderhilfe liegt hinter der Kindergruppe,
bei Code im inneren `code`-Host. Textänderungen und Snapshot-Restores aktualisieren
sie über die betroffenen Knoten und ihre Vorfahren; unveränderte Geschwister bleiben
unberührt. Stabile Textknoten bleiben erhalten.

Die Hilfe ist kein Dokumentknoten, keine Undo-Aktion und kein Bestandteil von
Content-HTML oder Clipboard-Export. Hydration berücksichtigt die Renderhilfe und
behält ihre SSR-Hostidentität. Es war keine weitere Änderung an UI-Core 1.0.1 nötig.

## Prüfung

Die drei ursprünglichen Layoutfälle schlugen vor der Korrektur in Chromium,
Firefox und WebKit fehl (9 Fehler). Die Tests messen die native Range-Geometrie;
bei leeren Textknoten die zugehörige Inline-Zeilenbox. Ein Browserbild mit sichtbarem
nativem Cursor bestätigte außerdem die neue leere Absatzzeile.

Regressionen: wiederholte/trailing Leerzeichen, wiederholtes Enter, Shift+Enter,
führendes Leerzeichen, Löschen des letzten Buchstabens, Undo/Redo, leere Codezeilen,
SSR-Hydration leerer Überschriften sowie Content-Export und Komponentenidentität.
Die Prüfung mit echten Betriebssystem-IME-Kandidaten wird dadurch nicht ersetzt.

Finaler lokaler Stand:

- **1267 Scala-Tests bestanden**, alle vier Full-Link-Anwendungen gebaut,
  `editorMetadata` und `scalafmtCheckAll` erfolgreich.
- **829 Browserfälle bestanden**, zusätzlich die zwei bekannten erwarteten
  Windows-WebKit-Clipboard-Fehler (Playwright meldet insgesamt 831 bestanden).
  Keine unerwarteten Fehler, keine Wiederholungsversuche oder übersprungenen Fälle
  im abschließenden Gesamtlauf. Serverimport ohne Browserglobals bestanden.
- **3 große Move-Fälle bestanden**: 100001 Modellknoten, 50000 Absatzgeschwister,
  jeweils ein DOM-Move bei erhaltener Identität.

```powershell
sbt --server scalafmtAll "Test/testOnly *" "scalajs-ember-integration/fullLinkJS" "scalajs-ember-profile-text/fullLinkJS" "scalajs-ember-profile-markdown/fullLinkJS" "scalajs-ember-profile-standard/fullLinkJS" editorMetadata scalafmtCheckAll
Set-Location ember-integration/browser
$env:EMBER_FIREFOX_CHANNEL='moz-firefox'
npm run verify
npm run test:bench:stress
```

Lokale Logs: `target/p28-caret-scala.log`, `target/p28-caret-browser-final.log`,
`target/p28-caret-stress.log`. Firefox lief über den dokumentierten installierten
BiDi-Kanal. Ein vorangegangener Gesamtlauf hatte zusätzlich zu noch anzupassenden
Whitespace-/Geometrie-Erwartungen einen Chromium-Ladefehler `ERR_NO_BUFFER_SPACE`;
die gezielte Wiederholung und der abschließende Gesamtlauf bestanden.

**Manueller Nachtrag:** Patrick bestätigt am 14. September 2026 auf Nachfrage zum
Nachtest von Leertaste und Enter: „Cursor folgt jetzt korrekt“.
Der [übernommene Trace mit Auswertung](corpora/device-traces/2026-09-14-patrick-w01-review.md)
stammt noch vor dem Fix; diese positive Beobachtung ist separat dokumentiert und
keinem neuen Build-Trace zugeordnet. W01 insgesamt sowie IME-, Screenreader- und
weitere Gerätefreigaben bleiben offen.
