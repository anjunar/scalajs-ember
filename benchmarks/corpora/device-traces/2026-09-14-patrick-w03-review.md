# W03: japanische IME abbrechen und weiter schreiben

Status: **W03 bestanden für die dokumentierte Windows-/Codex-Browser-Kombination.**
Patricks positiver Gerätebericht und der gesicherte Wiederholungslauf belegen
Abbruch und anschließende normale Eingabe. Keine allgemeine IME-Freigabe.

Patrick führte am 14. September 2026 den angeleiteten Abbruchtest durch:
Cursor zwischen `Hello ` und `world`, im Hiragana-Modus `nihon` eingeben,
vor Bestätigung/Umwandlung Escape drücken, auf Deutsch wechseln und `a` tippen.
Seine anschließende Rückmeldung: **„Jupp funktioniert einwandfrei“**.
Codex hat auf der Testseite den erwarteten Endtext `Hello aworld` abgelesen.

Umgebung laut Formular: Patrick, PC mit Windows 11, Codex In-app Browser
(UA Chrome/152.0.0.0), japanische Eingabe unter Windows und deutsches Layout
für das Folgetippen. Die genaue IME-Version ist nicht bestätigt.

## Erster Lauf und Exportproblem

Die erste Aufzeichnung wurde gestoppt. Die Testseite meldet **44 Ereignisse**;
Ergebnis und Beobachtung wurden in Patricks Auftrag eingetragen.
Der Export liefert über den aktuellen Browserzugriff bislang keine Download-Datei.
Auch ein gezieltes Warten auf das Download-Ereignis blieb ohne Ergebnis.
Der Testserver ist erreichbar; die Ursache des Exportproblems ist noch ungeklärt.
Patrick wurde gebeten, die Download-Schaltfläche einmal selbst zu betätigen.
Er bestätigt anschließend: **„Trace herunterladen funktioniert bei mir auch nicht.“**
Der Tab mit der Aufzeichnung bleibt erhalten und wurde nicht neu geladen.

Der [Exporter wurde inzwischen verbessert und separat geprüft](../../p28-trace-export.md):
Neue Seiten bieten neben dem Download auch einen dauerhaften Dateilink und ein
kopierbares JSON-Feld. Der alte Tab verwendet weiterhin den bisherigen Code;
seine 44 Ereignisse sind dadurch noch nicht als Datei gesichert.

## Gesicherter Wiederholungslauf

Nach der Exportkorrektur hat Patrick W03 auf einer frischen Seite wiederholt und
meldet **„trace ist heruntergeladen“**. Die [Originaldatei](2026-09-14-patrick-w03-japanese.json)
wurde unverändert aus dem Downloadordner übernommen. SHA-256:
`63d33e29c2c62107847449d1e021bca878f48c250bded63531ff9f55c2d60d57`.
Die Kopie stimmt bytegenau mit dem Download überein.

| Merkmal | Nachweis |
| --- | --- |
| Zeitraum | 14.09.2026, 19:12:26.727–19:15:10.332 MESZ |
| Ereignisse | 77, nicht abgeschnitten |
| Build-Revision | `d048dddce17a4369d57cd600f2b7a5e561e15b84`, dirty=true |
| Integration-SHA | `0973505cfd8f5239a6e60299887b9d2c02327b983716f273ef90f1dc047e85f4` |
| UI-Core | `com.anjunar:scalajs-ui-core_sjs1_3:1.0.1` |
| Browser-UA | Chrome/152.0.0.0, Windows NT 10.0, Win64/x64 |
| Originalformular | `outcome=open`, Beobachtung leer; unverändert erhalten |

Das Original bleibt einschließlich `reviewStatus=requires-human-review` erhalten.
Die Bewertung steht in diesem Bericht. Patricks ausdrückliche positive Rückmeldung
bezieht sich auf den ersten Lauf; der neue Trace belegt die wiederholte Sequenz,
ohne den ersten Trace nachträglich zu rekonstruieren.

## Auswertung

- 19 Tastendrücke, 19 `beforeinput`, sechs `input`, 21 Selectionchanges,
  ein Composition-Start, sechs Updates, ein Ende und je zwei Focusin/-out.
- Vor der IME-Eingabe wurden normale Testbuchstaben (`h`, `nihon`) eingegeben und
  mit Backspace vollständig entfernt. Erst danach beginnt die echte Composition
  am Offset 6 zwischen `Hello ` und `world`.
- Zwischenstände `ｎ`, `に`, `にｈ`, `にほ`, `にほｎ`; anschließend leeres Update
  und leeres Composition-Ende. DOM und Modell stehen wieder auf `Hello world`,
  Cursor bei Offset 6. Der folgende normale `insertText` für `a` ergibt genau
  `Hello aworld`, Cursor bei Offset 7. Nachbartext bleibt erhalten.
- Alle 77 Snapshots nach der Ereignisverarbeitung stimmen zwischen DOM und Modell
  überein. Bei sechs nativen `input`-Ereignissen ist im Capture-Snapshot bereits
  der DOM geändert; der Editor übernimmt ihn bis zum Nachher-Snapshot vollständig.
- 76 Ereignisse sind trusted. Das `compositionend` ist wie im W02-Lauf untrusted;
  seine Herkunft ist nicht aus dem Trace erklärbar. Die Abbruchtaste erscheint
  als `Process`, nicht als wörtliches `Escape`. Die Bedienung mit Escape stammt
  aus dem angeleiteten, positiv bestätigten Gerätebericht; der Trace belegt den
  Abbruch und das Folgetippen. Kein synthetischer IME-Lauf durch Codex.
- Die Textfelder enthalten ausschließlich Fixture-Text und diese Testeingaben.

Der alte 44-Ereignis-Lauf bleibt ungesichert im alten Tab. Der unabhängige neue
77-Ereignis-Lauf löst die fehlende Datei für den W03-Nachweis ab.

W04 (Fokuswechsel während der Composition), W05 (Auswahlersetzung) und die übrigen
offenen Geräteprüfungen erhalten dadurch keine Freigabe. P29 bleibt offen.
