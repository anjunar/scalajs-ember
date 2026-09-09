# scalajs-ember-core

Headless Kern des Ember-Editors. Dokumentmodell, Selection, Transaktionen, Commands und
Extensions — ohne DOM, ohne JFX-Runtime, ohne Formular, ohne UI.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §§5–13.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-core` |
| Scala-Paket | `ember.editor.core` |
| Produktionsabhängigkeiten | keine außer der Scala-/Scala.js-Standardbibliothek |

## Stand

P01 und P02 abgeschlossen. Vorhanden: Fehlerkonvention, Abhängigkeitsgrenze, unveränderliches
Dokumentmodell mit vollständiger Strukturvalidierung, offene Node- und Mark-Verträge, Schema
und ID-Generator. `Selection`, `Transaction` und `Command` folgen ab P03/P04. Leere
Platzhaltertypen werden bewusst nicht vorweggenommen.

## Dokumentmodell

| Typ | Rolle |
| --- | --- |
| `EditorNode` / `ElementNode` / `AtomNode` | Offener Node-Vertrag, geschlossene Strukturkategorien |
| `RootNode`, `TextNode` | Die zwei Arten, die der Kern selbst mitbringt |
| `NodeType[N]` / `ElementNodeType[N]` | Deskriptor mit Typzeuge (`project`) und Rekonstruktion (`rekey`, `withChildren`) |
| `TextMark` / `MarkSet` | Offener Mark-Vertrag, normalisierte Menge mit ordnungsunabhängiger Gleichheit |
| `Schema` | Registry der Node-Arten, ohne öffentliche `Map[String, Any]` |
| `Document` / `DocumentRead` | Gültiger unveränderlicher Baum, privat konstruiert |
| `DocumentValidator` / `Violation` | Fünfstufige Prüfung, geschlossenes Ergebnis-ADT |
| `NodeIdGenerator` | Injizierte ID-Quelle, deterministisch testbar |

Ein `Document` ist nur über `Document.build` zu bekommen und erfüllt danach die Invarianten aus
§8.2 immer schon: genau eine Wurzel, eindeutige IDs, erreichbare Knoten, keine Zyklen, je Knoten
genau ein Elternteil, schemakonforme Inhalte. Wer einen Wert dieses Typs hält, muss nichts mehr
prüfen.

Der Elternindex ist **abgeleitet** — eine Projektion der Kindlisten, keine zweite Wahrheit.
Kinder sind ausschließlich referenzierte IDs, nie eingebettete Objekte; eine Textänderung tief
im Baum kostet deshalb den betroffenen String und wenige Indexpfade, nicht eine rekursive Kopie
aller Vorfahren.

Alle Traversierungen sind iterativ. `DocumentSpec` baut dafür einen 25 000 Ebenen tiefen Baum —
Node.js schafft rund 11 000 Rekursionsebenen, eine rekursive Implementierung würde dort
zuverlässig mit einem Stacküberlauf scheitern statt mit einer Diagnose.

### Validierung in fünf Stufen

Identität → Referenzen → Zyklen → Erreichbarkeit → Schema. Die Prüfung bricht nach der ersten
fehlerhaften Stufe ab. Das ist Diagnosequalität, kein vorzeitiges Aufgeben: aus einer einzigen
ins Leere zeigenden Kindreferenz folgen sonst zwangsläufig Unerreichbarkeits- und Schemafehler,
die nur die Ursache zudecken.

Jede `Violation` nennt Pfad, ID und Grund:

```
<root>#root.children[0]: `root` verweist an Position 0 auf den unbekannten Knoten `ghost`.
```

### Fremde Node-Arten

Feature-Nodes erfordern keine Core-Änderung. Der Nachweis steht in
[ember/editor/foreign/ForeignNodes.scala](src/test/scala-3/ember/editor/foreign/ForeignNodes.scala):
ein Container mit Zusatzfeldern, ein Atom und eine eigene Mark, alle in einem fremden Paket und
ausschließlich über die öffentliche API gebaut. `rekey` und `withChildren` sind der Grund, warum
das geht — der Kern kann die `copy`-Signatur einer fremden Case Class nicht erraten und darf es
auch nicht versuchen.

## Abhängigkeitsgrenze

Architektur §7: der Kern hat kein `org.scalajs.dom`, keine JFX-Property und keinen
Forms-/Viewport-Import. Das ist keine Absichtserklärung, sondern ein Build-Gate.

`boundaryCheck` in [build.sbt](../build.sbt) prüft bei jedem Compile drei Dinge:

1. **Projektabhängigkeiten** gegen eine Allowlist — für diesen Kern ist sie leer.
2. **Aufgelöste Artefakte** gegen eine Blocklist (`scalajs-dom`, `scalajs-jfx`, `scalajs-lexical`).
   Das ist die eigentliche Garantie: was nicht auf dem Classpath liegt, lässt sich auch voll
   qualifiziert nicht verwenden.
3. **Imports** gegen verbotene Paketpräfixe — inklusive der Module, die später auf dem Kern
   aufbauen (`ember.editor.jfx`, `.browser`, `.forms`, `.ui`, `.html`, `.markdown`, `.json`).
   Diese Pakete existieren noch nicht; die Regel steht trotzdem schon.

Der Check hängt an `Compile / sources`, nicht an `Compile / compile`: sbt 2 cached
Taskergebnisse, und `compile` neu zuzuweisen hätte den Compile-Schritt selbst aus dem
Action-Cache genommen. Details stehen als Kommentar in `build.sbt`.

Verletzungen brechen den Build ab, bevor kompiliert wird:

```
Abhaengigkeitsgrenze von scalajs-ember-core verletzt (JFX_EDITOR_ARCHITECTURE.md, Abschnitt 7):
Nicht erlaubte Projektabhaengigkeiten:
  probe-dummy
Verbotene Artefakte auf dem Classpath:
  org.scala-js:scalajs-dom_sjs1_3
Verbotene Imports:
  …\ember\editor\core\Probe.scala:3  import org.scalajs.dom  (verboten: org.scalajs.dom)
```

## Fehlerkonvention

Definiert in [package.scala](src/main/scala-3/ember/editor/core/package.scala). Zwei Arten von
Fehlschlägen, an der Signatur unterscheidbar:

- **Erwartete Fehler sind Werte.** `Either[E, A]` mit `E <: EditorError`. Ein ungültiges
  Dokument, eine abgewiesene Transaktion, ein nicht dekodierbares Wire-Format — vorgesehene
  Ergebnisse, keine Exceptions. Architektur §10 baut darauf auf: `update` liefert
  `Either[UpdateError, Commit]`.
- **Vertragsverletzungen des Aufrufers fliegen.** `EditorContractViolation` bei abgelaufenem
  Tx-Handle, verschachteltem `update`, Aufruf nach `dispose`. Ein `Either` würde suggerieren,
  ein Aufrufer könne sinnvoll darauf reagieren.

`EditorError` ist ein offener Trait, damit fremde Feature-Module eigene Fehler beitragen
können, ohne den Kern zu ändern — dieselbe Entscheidung wie beim offenen Node-Vertrag §8.1.
Geschlossen ist dagegen `PathSegment`: die Struktur eines Diagnosepfads ist Kerneigenschaft.

`DiagnosticPath` erfüllt die Forderung aus §8.2 und §19.2, dass ungültige Eingaben Pfad, ID
und Grund liefern:

```scala
DiagnosticPath.field("children").index(2).node("p-17").field("text").render
// <root>.children[2]#p-17.text
```

## Tests

```bash
sbt --server "scalajs-ember-core/Test/testOnly *"
```

`CoreEnvironmentSpec` belegt, dass der Kern in einer Umgebung ohne `window` und `document`
lädt, und prüft die Fehlerkonvention. Die Abhängigkeitsgrenze prüft er *nicht* — ein
gelinktes Scala.js-Modul hat weder Classpath noch Dateisystem. Dafür ist `boundaryCheck`
zuständig, der über `Compile / sources` ohnehin vor jedem Testlauf greift.
