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

Links die Editierfläche, rechts derselbe Stand in vier Ansichten — Dokumentbaum, JSON,
ausgeliefertes HTML und Editor-HTML. Alle vier hängen an **einem** Dokument; die Panels sind über
`EditorProperties.document(session)` gebunden und werden bei jedem Commit nachgeführt (§10), nicht
von einem Timer.

Damit ist der Stand nach P11 an einem Stück sichtbar:

| | |
| --- | --- |
| P02–P05 | unveränderliches Dokumentmodell, primitive Operationen, atomare Transaktionen |
| P06 | Absätze, Editing-Commands, Normalisierung |
| P09 | die keyed Projektion und dieselbe Semantik für SSR und Browser |
| P10 | das versionierte JSON |
| P11 | Undo und Redo mit den Gruppierungsregeln aus §14 |

Undo und Redo gibt es als Knöpfe und über Strg+Z beziehungsweise Strg+Shift+Z; die Statuszeile
zeigt die Tiefe beider Stapel. Zusammenhängendes Tippen wird dabei zu einer Stufe zusammengefasst
— ein Undo nimmt das Wort zurück, nicht den Buchstaben.

## Was ausdrücklich noch fehlt

**Kein `contenteditable`, keine DOM-Selection, keine native Eingabe.** Das sind P20 bis P23. Die
Fläche ist fokussierbar und fängt Tastendrücke ab, die sie in Commands übersetzt — dieselbe Kette,
die eine echte Eingabe später nimmt, nur ohne den nativen Teil davor. Composition, IME und
Mutation-Recovery fehlen entsprechend ganz.

**Der Caret ist ein Modellwert.** Er steht in der Statuszeile als `knoten:offset`, und der Textlauf,
in dem er sitzt, wird eingefärbt. Die Einfärbung läuft über `DocumentView.componentFor` — den Index,
den §15.1 als „eine Zuordnung, keine zweite Ownership-Liste" führt. Eine echte Browserauswahl setzt
sie nicht; das ist der `SelectionPort` aus P21.

**Keine Hydration.** Die Seite rendert clientseitig in `#root`; der Server liefert eine leere Hülle.
Hydration ist P20.

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
| `DemoSession.scala` | Sitzung, Schema, Codecs und die vier Ansichten. Die einzige Stelle, an der alle Module vorkommen. |
| `DemoApp.scala` | Die Seite als JFX-Komponentenbaum. |
| `dev/` | HTML-Hülle, Stylesheet, Server. |

Die Editierfläche hängt als **Kind** der Flächenkomponente im Baum, nicht als zweite Wurzel
(`DocumentView.mount(..., parent = Some(host))`). Damit räumt ein `Runtime.unmount` der Seite auch
die Ansicht ab, und `DocumentView.dispose()` bleibt trotzdem gefahrlos.
