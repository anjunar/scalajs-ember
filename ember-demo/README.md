# scalajs-ember-demo

Die laufende Demo des Ember-Editors. **Nicht publiziert** — eine Anwendung, keine Bibliothek.

| | |
| --- | --- |
| sbt-ID | `scalajs-ember-demo` |
| Scala-Paket | `ember.editor.demo` |
| Abhängigkeiten | alle Ember-Module plus `jfx-core` |

## Starten

```bash
sbt --server "scalajs-ember-demo/fastLinkJS"
```

```bash
node ember-demo/dev/server.mjs
```

Dann [http://127.0.0.1:4200](http://127.0.0.1:4200). `EMBER_DEMO_PORT` setzt den Port,
`EMBER_DEMO_FULL=1` liest stattdessen die `fullLinkJS`-Ausgabe.

**Kein vite, kein Bundler, keine npm-Abhängigkeit.** Die Demo besteht aus einer HTML-Hülle, einem
handgeschriebenen Stylesheet und der Linkerausgabe. Ein Build-Schritt dafür wäre mehr Werkzeug als
Editor — und ein Werkzeug, das jemand pflegen muss, bevor er den Editor sehen kann. Der kleine
Node-Server ist trotzdem nötig: `main.js` ist ein ES-Modul, und ein Modulimport über `file://`
scheitert an der Origin-Prüfung des Browsers.

## Was zu sehen ist

Links die Editierfläche, rechts derselbe Stand in fünf Ansichten — Dokumentbaum, JSON,
Markdown, ausgeliefertes HTML und Editor-HTML. Alle fünf hängen an **einem** Dokument; die Panels sind über
`EditorProperties.document(session)` gebunden und werden bei jedem Commit nachgeführt (§10), nicht
von einem Timer.

Damit ist der Stand nach P22 an einem Stück sichtbar:

| | |
| --- | --- |
| P02–P05 | unveränderliches Dokumentmodell, primitive Operationen, atomare Transaktionen |
| P06 | Absätze, Editing-Commands, Normalisierung |
| P09 | die keyed Projektion und dieselbe Semantik für SSR und Browser |
| P10 | das versionierte JSON |
| P11 | Undo und Redo mit den Gruppierungsregeln aus §14 |
| P12 | Marks, Überschriften, Zitate, Umbrüche und die Textlauf-Normalisierung |
| P13 | Listen mit Ein- und Ausrücken |
| P14 | Links samt URL-Policy |
| P15 | Codeblöcke mit Sprachangabe |
| P16 | Bilder als Inline-Atome samt Media-Policy |
| P17/P18 | Markdown: Parser, Writer und der Weg ins Dokument |
| P21 | DOM-Selection, Fokus und Bookmarks |
| P22 | Tippen: `beforeinput`, Tastatur, nativer Eingabepfad |

Undo und Redo gibt es als Knöpfe und über Strg+Z beziehungsweise Strg+Shift+Z; die Statuszeile
zeigt die Tiefe beider Stapel. Zusammenhängendes Tippen wird dabei zu einer Stufe zusammengefasst
— ein Undo nimmt das Wort zurück, nicht den Buchstaben.

Seit P12 stehen daneben Fett, Kursiv, Code, H2, Zitat, Umbruch und Trenner; die Statuszeile zeigt
die aktiven Marks. P13 bringt Liste, Nummern, Einrücken und Ausrücken dazu -- Letztere auch auf
Tab und Shift+Tab, was P13 ausdrücklich als Sache der Anwendung führt und nicht des Moduls.

„Link" und „Link weg" arbeiten auf dem Lauf am Caret: `SetLink` braucht eine Auswahl, und ein
Klick auf „Link" kommt typisch bei einem Caret statt bei einem markierten Bereich. Seit P21 wäre
eine DOM-Auswahl zu lesen; die Demo wählt trotzdem den ganzen Lauf -- vorhersagbar und erklärbar.

„Bild", „Alt-Text" und „Bild 96px" gehören zu P16. Das eingefügte Bild ist eine **echte Datei
unter einem relativen Pfad** (`/ember.svg`, vom Dev-Server ausgeliefert) — genau die Quelle, die
`MediaUrlPolicy.default` zulässt. Eine `data:`-URL wäre bequemer und würde von derselben Policy
abgewiesen; sie ist keine dauerhafte MediaReference (§20). Einen Dateidialog gibt es hier nicht
und wird es hier nicht geben: Uploads sind laut §20 ein Anwendungsservice, und der Command nimmt
eine fertige Adresse.

Das Startdokument bringt schon eines mit, mitten in einem Absatz. Im Panel „Dokument" ist zu
sehen, was das heißt: das Bild steht **zwischen** zwei Textläufen, nicht neben dem Absatz. Wer
den Caret in einen Lauf setzt und „Bild" drückt, teilt den Lauf und setzt es zwischen die
Hälften.

„Alt-Text" und „Bild 96px" arbeiten auf dem Bild neben dem Caret, aus demselben Grund wie
„Link" auf dem Lauf am Caret: ein Atom hat keine Textposition, ein Caret kann also nicht *darin*
stehen. Seit P21 ließe es sich auch mit der Maus auswählen; die Knopfvariante bleibt, weil sie
ohne Erklärung reproduzierbar ist. Auswahl, Änderung und der Caret danach laufen in **einer**
Transaktion -- drei wären drei History-Stufen.

Das Panel „Markdown" zeigt den Export (P18). Es wählt **`AllowLossy`**, und das ist der
interessante Teil: eine Ansicht, die statt eines Dokuments einen Fehler zeigte, sobald etwas
keine Markdown-Schreibweise hat, wäre unbrauchbar. §18.2 macht die Wahl ausdrücklich, und was
nicht mitkommt, steht unter dem Quelltext — beim Startdokument etwa die Maße des Bildes. Still
verloren geht nichts.

Und was das Panel **nicht** zeigt: den Quelltext, aus dem das Dokument einmal kam. §18.2 sagt
`encode(decode(source)) == source` ausdrücklich ab. Zugesichert ist die andere Richtung.

„Codeblock" macht aus dem Absatz am Caret einen Codeblock. Tab und Shift+Tab rücken darin die
Zeile ein statt das Listenelement: beide Befehle liegen auf derselben Taste, und beide geben
`Pass` zurück, wenn sie nicht zuständig sind (§12) -- der Controller probiert sie deshalb der
Reihe nach. Wo keiner zuständig ist, verlässt Tab die Fläche.

Wer bei leerem Caret „Fett" drückt, erzeugt keinen Text -- die nächste Eingabe
kommt fett heraus (§11). Und wer die Formatierung wieder wegnimmt, sieht im Panel „Dokument", wie
die drei Läufe zu einem zusammenwachsen.

## Seit P21 und P22: eine echte Editierfläche

Die Fläche ist ein richtiger Editing-Host. `contenteditable`, `role="textbox"` und
`aria-multiline` setzt der `BrowserInputController`; ein `SelectionPort` liest und schreibt die
Browserauswahl, und Tastendrücke werden zu Absichten, Absichten zu Commands.

Nichts davon ist hier nachgebaut. Es ist dieselbe Pipeline, die das Integrations-Gate fährt --
gegen eine Sitzung, die jedes Feature-Modul dieses Repositories trägt. Genau dafür gibt es die
Demo: als Probe, dass sich die Module zu einer Anwendung zusammensetzen lassen.

Bis P22 stand hier eine handgeschriebene `keydown`-Brücke, und ihr eigener Kommentar sagte,
worauf sie wartete: „The real judgement -- when a native action is prevented and when it is not --
belongs to P22." Die Brücke ist weg, und mit ihr die Demo-Commands, die nur sie brauchte.

**Der Caret ist weiterhin auch ein Modellwert.** Er steht in der Statuszeile als
`knoten:offset`, und der Textlauf, in dem er sitzt, wird eingefärbt -- über
`DocumentView.componentFor`, den Index, den §15.1 als „eine Zuordnung, keine zweite
Ownership-Liste" führt. Neu ist, dass daneben eine echte Browserauswahl steht, die mit ihm
übereinstimmt.

**Tab rückt ein und sperrt niemanden ein.** §22 lässt Einrückung auf Tab nur ausdrücklich
aktiviert zu und verlangt einen Ausgang: Escape, dann Tab, und der Fokus geht weiter. Die Demo
schaltet `TabPolicy.IndentsUntilEscape` ein und zeigt beides.

## Was ausdrücklich noch fehlt

**Composition, Observer-Abgleich und Recovery.** Das ist P23. Eine IME funktioniert für einfache
Fälle, und mehr behauptet P22 nicht: während einer Composition beansprucht der Controller nichts
und schreibt nichts, und was sie hinterlässt, wird danach gelesen. Eine native Struktur, die sich
nicht als Dokumentänderung ausdrücken lässt, führt in `Recovering` -- mit gesichertem Text und
einer Meldung in der Statuszeile, aber ohne Reparatur.

**Keine Hydration.** Die Seite rendert clientseitig in `#root`; der Server liefert eine leere
Hülle. P20 gibt es, diese Demo benutzt es nur nicht.

## Die Gruppenanker in der HTML-Ausgabe

Beide HTML-Fassungen tragen die Gruppenanker der JFX-Runtime
(`<!--jfx:KeyedChildren:start-->`). Das ist Absicht und bleibt so: P20 braucht sie zum
Hydrieren, und ein Kommentarknoten ist im ausgelieferten Dokument weder sichtbar noch
semantisch. Sie sind der einzige Ort, an dem die Ausgabe verrät, welche Runtime sie erzeugt
hat.

## Aufbau

| Datei | Inhalt |
| --- | --- |
| `Main.scala` | Einstieg. Kein Initialisierungscode auf oberster Ebene — §15.2. |
| `DemoSession.scala` | Sitzung, Schema, Codecs und die fünf Ansichten. Die einzige Stelle, an der alle Module vorkommen. |
| `DemoApp.scala` | Die Seite als JFX-Komponentenbaum. |
| `dev/` | HTML-Hülle, Stylesheet, Bild, Server. |

Die Editierfläche hängt als **Kind** der Flächenkomponente im Baum, nicht als zweite Wurzel
(`DocumentView.mount(..., parent = Some(host))`). Damit räumt ein `Runtime.unmount` der Seite auch
die Ansicht ab, und `DocumentView.dispose()` bleibt trotzdem gefahrlos.
