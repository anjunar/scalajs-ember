# W04: Fokuswechsel während japanischer Eingabe

Status: **auf Patricks Wunsch übersprungen; keine W04-Freigabe.**
Patrick weist anschließend an: „Überspringe nun mal W04“.
Normale Folgeeingabe ist belegt, Fokuswechsel während Composition nicht belegt.

Patrick berichtet: **„Es wurde weder verworfen noch bestätigt. aber das a ist zu sehen“**.
Codex liest danach den Endtext `Hello worldにほｎ　a` ab. Der Screenshot zeigt den
Cursor hinter dem normalen `a`, ohne sichtbares Kandidatenfenster. Die Aufzeichnung
wurde gestoppt und die Rückmeldung unverändert im Formular festgehalten.

## Gesicherter Trace und Auswertung

Der neue Exporter stellt das vollständige JSON im Textfeld bereit. Daraus wurde
die Ereignisfolge gelesen; ein Download ist zunächst nicht im Downloadordner
angekommen, auch nicht nach Betätigen des bleibenden Dateilinks durch Codex.
Patrick wurde um Betätigung des Dateilinks gebeten. Die Seite bleibt erhalten.

Anschließend meldet Patrick die Datei als gespeichert. Der
[Originaltrace](2026-09-14-patrick-w04-japanese.json) wurde unverändert übernommen
und mit dem Download per SHA-256 abgeglichen:
`e6a2bc42f91659f95406d2d582309757d5988274665029e154b031d959372a40`.
Die Datei bestätigt die zuvor im sichtbaren JSON gelesene Auswertung.
`outcome=open` und `reviewStatus=requires-human-review` bleiben unverändert.
Die Textfelder enthalten nur Fixture-Text, japanische Testzwischenstände,
Leerzeichen und das normale `a`.

- 108 Ereignisse, nicht abgeschnitten; 14.09.2026,
  19:19:08.827–19:21:32.626 MESZ.
- Patrick, Windows 11 laut Formular, Codex In-app Browser, UA Chrome/152.0.0.0;
  japanische Hiragana-Eingabe und danach deutsches Layout laut Testvorgabe/Formular.
- Build `d048dddce17a4369d57cd600f2b7a5e561e15b84`, dirty=true;
  Integration-SHA `0973505cfd8f5239a6e60299887b9d2c02327b983716f273ef90f1dc047e85f4`;
  UI-Core 1.0.1.
- Vier Composition-Starts und -Ends, 15 Updates, 21 `beforeinput`, 15 `input`,
  19 `keydown`, 24 Selectionchanges, je drei Focusin/-out.
- Alle 108 Nachher-Snapshots zeigen gleichen DOM- und Modelltext. Bei 13 nativen
  Input-Ereignissen weichen die Capture-Snapshots vor der Editorverarbeitung ab;
  danach sind die Änderungen vollständig übernommen.

Es gibt zunächst einen korrigierten Wortversuch und volle Leerzeichen U+3000.
Die letzte Composition erreicht `にほｎ` und endet mit genau diesem Text
(`compositionend`, nullbasierter Ereignisindex 100). Das `a` folgt erst danach
als normaler `insertText` mit `isComposing=false` (Index 103).
Die Eingabe wurde somit technisch abgeschlossen und unverändert übernommen,
ohne Umwandlung in Kanji. Keine doppelte Übernahme oder blockierte Folgeeingabe
im Trace festgestellt. Das `compositionend` ist wie in früheren Läufen als
untrusted markiert; seine Herkunft ist nicht aus dem Trace erklärbar.

**Zwischen dem Beginn und Ende der japanischen Eingabe fehlt jedes Focusout.**
Das erste Focusout im Lauf liegt vor den Compositions, das nächste erst nach
dem normalen `a` (Index 105). Daher ist die Wirkung eines Klicks in das äußere
Eingabefeld während offener Composition hier nicht prüfbar. Aus dem erhaltenen
Romaji-Zwischenstand wird kein Editorfehler abgeleitet.

Der Editor beendet laut `BrowserInputController.scala` eine offene Composition
bei `focusout` und übernimmt den noch vorhandenen Text. Dieser Pfad wird durch
den vorliegenden Geräte-Trace nicht nachgewiesen. Ein gezielter W04-Lauf muss
das kleine Eingabefeld rechts neben „Notizen außerhalb des Editors“ tatsächlich
fokussieren (dort sichtbarer Cursor), bevor es in den Editor zurückgeht.

Originaldatei und SHA-Abgleich liegen vor; die Exportblockade ist für diesen Lauf aufgehoben.
W04 wird auf Patricks Wunsch nicht weiter geprüft; die fehlende Abnahme bleibt
dokumentiert. Anschließend lässt Patrick auch alle weiteren IME-Prüfungen
überspringen und meldet „Das funktioniert alles“; kein weiterer W04-Lauf vorgesehen.
