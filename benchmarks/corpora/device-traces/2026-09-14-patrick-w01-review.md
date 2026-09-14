# W01: übernommener Tastaturlauf von Patrick

Status: **Trace geprüft; Cursor-Nachtest für Leertaste/Enter laut Patrick bestanden;
vollständiger W01 offen.**

Der vorhandene Download wurde auf Patricks Bitte unverändert übernommen:
[Originaltrace](2026-09-14-patrick-w01-before-caret-fix.json).
SHA-256: `ccfcd03616858858561388fbd669b42bba6485f0cd1a3307d14f7784c427182b`.
Die Kopie stimmt bytegenau mit dem Download überein. Die ausgefüllte Browserseite
wurde zum Abgleich gelesen, ohne sie neu zu laden oder Testeingaben zu verändern.

## Übernommene Angaben

| Feld | Aufgezeichneter Wert |
| --- | --- |
| Testperson | patrick |
| Umgebung laut Formular | PC, Windows 11, Chrome 153.0.8010.36 |
| Eingabe laut Formular | Keyboard, Deutsch |
| Prüfschritt | W01 – Tastatur, Auswahl und Unicode |
| Ergebnis im ursprünglichen Formular | Offen; Beobachtung leer |
| Zeitraum | 14.09.2026, 16:50:04.962–16:50:43.353 MESZ; 38,391 Sekunden |
| Trace | 213 Ereignisse; nicht abgeschnitten |
| Sprache | de |
| Browser-UA im Export | Chrome/152.0.0.0, Windows NT 10.0, Win64/x64 |
| Beim Auslesen erreichter Tab | Codex In-app Browser, `/toolbar?trace=1` |
| Build-Revision laut Export | `b8b90aa998127bb55524a502a7b121990336d577`, dirty=false |
| Build-SHA-256 | `5aa2b0a0a312c1acf91be0df7f82299d456dd9d97fc63232cc63b7e1a3ad45c9` |
| UI-Core | 1.0.1 |

Die Chrome-Versionsangabe im Formular und der Browser-UA unterscheiden sich.
Beide bleiben als jeweilige Quelle erhalten; der Lauf belegt damit keine
eindeutig bestätigte Abnahme in einem eigenständigen Chrome 153.

## Auswertung des Traces

- 69 Tastendrücke: 23 Leerzeichen, 6 Enter, 40 Buchstaben (`a`, `d`, `s`).
- 69 `beforeinput`-Ereignisse: 63 `insertText`, 6 `insertParagraph`;
  alle wurden durch den Editor übernommen (`defaultPrevented=true`).
- 73 Selectionchanges sowie je ein Focusin und Focusout.
- Alle 213 Ereignisse sind als `trusted=true` aufgezeichnet. Das allein ist
  kein Nachweis einer vollständigen Geräteabnahme.
- In sämtlichen Vorher-/Nachher-Snapshots stimmen DOM- und Modelltext überein.
  Die 852 Textfelder enthalten ausschließlich Fixture-Text, einfache Testbuchstaben
  und Leerzeichen; keine privaten Dokumentinhalte wurden gefunden.
- Keine Composition, keine Graphem-/Unicode-Löschung, keine Undo-/Redo-Eingabe
  und kein Shift+Enter sind in diesem Trace belegt. Textsnapshots enthalten keine
  Absatzstruktur oder Pixelpositionen; sichtbare Cursorbewegung lässt sich daraus
  nicht nachträglich bestätigen.

Dieser Trace stammt **vor dem Fix** `2061c87` (`bug fix: space and enter`).
Der beim Übernehmen gebaute Integrationseintrag hat SHA-256
`0973505cfd8f5239a6e60299887b9d2c02327b983716f273ef90f1dc047e85f4`.
Zusätzlich fehlen im noch offenen Tab die Whitespace-Regel und die Renderhilfen
des Fixes. Der Trace wird deshalb als erster Lauf zum
[gemeldeten Cursorbefund](../../p28-caret-layout.md) aufbewahrt.
Er bestätigt die Textübernahme, aber nicht die Behebung des sichtbaren Fehlers.

## Manueller Nachtrag aus dem Gespräch

Am 14. September 2026 wurde Patrick ausdrücklich gefragt, ob beim Nachtest von
Leertaste und Enter der sichtbare Cursor jetzt sofort folgt oder noch ein Fehler
auftritt. Seine Antwort: **„Cursor folgt jetzt korrekt“**.

Damit ist die sichtbare Cursorbewegung nach **Leertaste und Enter laut Testperson
bestanden**. Diese Bestätigung wird separat dokumentiert und nicht rückwirkend dem
älteren Trace zugeschrieben. Der konkrete Tab und Build dieser positiven Beobachtung
sind durch keinen neuen Trace zugeordnet. Das Original-JSON mit `outcome=open`
und `reviewStatus=requires-human-review` bleibt unverändert.

Der gesamte W01 ist weiter offen: Unicode/Graphemlöschung, Auswahl/Navigation,
Undo/Redo und Shift+Enter wurden in diesem Nachtrag nicht einzeln bestätigt.
IME-, Screenreader- und weitere Gerätefreigaben werden daraus nicht abgeleitet.
