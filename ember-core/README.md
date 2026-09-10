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

P01–P05 abgeschlossen. Vorhanden: Fehlerkonvention, Abhängigkeitsgrenze, unveränderliches
Dokumentmodell mit vollständiger Strukturvalidierung, offene Node-, Mark- und
Selection-Verträge, Schema, ID-Generator, primitive Operationen mit komponierbarer
Positionsabbildung, Sitzung, atomare Transaktionen, typisierte Zustandsfelder sowie
Commands, Extensions und Transforms. Der kleine headless Texteditor folgt mit P06 im
Modul `ember-rich-text`. Leere Platzhaltertypen werden bewusst nicht vorweggenommen.

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
| `Point` / `Affinity` | Logische Position mit Text- oder Kindoffset, plus Klebeseite |
| `Selection` / `SelectionSupport` | Offener Auswahlvertrag, Range und Node eingebaut |
| `Operation` / `OperationError` | Sieben Primitive: Insert, Remove, Move, Replace, SpliceText, SplitText, MergeText |
| `PositionMapping` / `MappedPoint` | Komponierbare Nachführung von Positionen |
| `ChangeSet` / `TextSplice` | Was sich geändert hat, getrennt von bloß berührten Vorfahren |
| `Bookmark` / `RevisionMapping` | Gemerkte Position samt Ablaufvertrag |
| `TextBoundaryService` | Nur der Vertrag; implementiert in [`ember-rich-text`](../ember-rich-text/README.md) |
| `EditorState` / `Commit` | Veröffentlichter Sitzungszustand, mit zwei Revisionen |
| `EditorSession` / `SessionConfig` | Besitzt den Zustand, hält die einzige Commit-Grenze |
| `Transaction` | Privater Entwurf mit eingerastetem Fehler und begrenzter Lebensdauer |
| `StateField` / `StateFields` | Typisierte Sitzungsfelder mit reinem, ablehnendem Reducer |
| `PreCommitRule` | Synchrones Urteil über den fertigen Kandidaten |
| `UpdateError` / `Subscription` | Warum ein Commit ausblieb; aufkündbare Registrierung |
| `EditorCommand` / `CommandRegistry` | Absichten mit Instanzidentität, Prioritäten und Pass/Handled |
| `Transform` / `TransformScope` | Normalisierung bis zum Fixpunkt, mit eingeschränktem Zugriff |
| `Extension` / `ExtensionResolver` | Deklarative Beiträge, Abhängigkeitsordnung, Install-Rollback |

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

## Operationen und Positionsabbildung

Jede Operation liefert drei Dinge auf einmal (§10): das neue Dokument, ein `ChangeSet` und
eine `PositionMapping`. Sie entstehen gemeinsam, weil sie sonst auseinanderlaufen könnten.

```scala
document.applyOperation(Operation.SpliceText(t1, 5, 0, "!")) // Either[OperationError, OperationResult]
document.applyAll(Seq(…))                                    // dasselbe, komponiert
```

Atomar: bei einem Fehler bleibt das Ausgangsdokument unverändert. Es gibt nichts
zurückzurollen — jeder Zwischenstand ist ein eigener unveränderlicher Wert.

Die Operationen validieren **nicht** voll durch. Sie prüfen ihre Vorbedingungen und
konstruieren das Ergebnis so, dass die Invarianten erhalten bleiben; eine Vollvalidierung pro
Tastendruck wäre linear in der Dokumentgröße und genau das, was §8.2 ausschließt. Weil das
eine Behauptung ist, prüft `DocumentOperationModelSpec` sie: 30 Runden zu je 40 zufälligen
Operationen, nach **jedem** Schritt gegen drei unabhängige Instanzen — den `DocumentValidator`
(Neuaufbau von Grund auf), ein Referenzmodell aus schlichten Maps, und mitgeführte Punkte, die
im neuen Dokument darstellbar sein müssen.

### Affinität

`MappedPoint` unterscheidet `Preserved` von `Displaced`. Das ist kein Luxus: ein Caret darf auf
eine Grenze zurückfallen, ein Upload-Bookmark darf das nicht — sonst landet das fertige Bild an
beliebiger Stelle (§20). Verschiebung ist ansteckend: was in einem Schritt verschwunden ist,
taucht im nächsten nicht wieder auf.

`Affinity` entscheidet, woran ein Punkt klebt, wenn genau an seiner Position eingefügt wird.
Der lehrreichste Fall steht in `PositionMappingSpec`: beim Umsortieren von `[t1, t2]` zu
`[t2, t1]` wandert `Children(c1, 2, Before)` auf Offset 1, weil der Punkt an `t2` klebt und
`t2` nach vorn gerückt ist. Eine Kindposition ist eine Grenze zwischen Geschwistern, keine
Nummer, die stehen bleibt.

## Sitzung und Commit-Grenze

```scala
val editor = EditorSession.create(document, SessionConfig(fields = Vector(TypingMarks)))

editor.update { tx =>
  tx.spliceText(t1, 5, 0, "!")
  tx.select(RangeSelection.caret(Point.textAfter(t1, 6)))
}  // Either[UpdateError, Commit]
```

Eine Transaktion ist ein privater Entwurf. Sie rastet den ersten Fehlschlag ein und weist
alles Weitere ab — die Closure liefert `Unit`, ein ignoriertes `Either` darf also nicht dazu
führen, dass auf einem kaputten Entwurf weitergearbeitet wird. Ihr Handle gilt nur innerhalb
der Closure; danach wirft jeder Zugriff, denn es in einem `Future` aufzuheben ist ein
Programmierfehler, kein Datenfehler.

**Zwei Revisionen.** `revision` steigt bei jeder Veröffentlichung, `documentRevision` nur bei
echter Dokumentänderung. Wer speichert, vergleicht die zweite — ein bewegter Cursor löst dann
keinen Schreibvorgang aus (§9).

**Ein Commit ist nicht gerendert.** Der Kern veröffentlicht einen Zustand; ob eine View ihn
zeigt, ist ein anderer Zeitpunkt (§5). P09 hängt die Projektion in dieselbe Phase, in der
heute die Warteschlange abgearbeitet wird.

**Keine Reentranz.** `update` innerhalb eines `update` ergibt `UpdateError.NestedUpdate`. Wer
aus einem Listener heraus ändern will, nimmt `enqueueUpdate` — FIFO, und erst wenn die
Benachrichtigungsphase durch ist. Ein geworfener Listener wird an den Error-Sink gemeldet und
reißt die übrigen nicht mit; ein bereits veröffentlichter Commit wird deswegen nicht halb
zurückgedreht.

### Zustandsfelder

`StateField[A]` ist keine frei beschreibbare Zelle. Der Reducer läuft im Commit gegen den
fertigen Kandidaten und darf **ablehnen** — daran hängt P19b: ein `ToggleUnderline` in einem
Strict-CommonMark-Feld muss vor dem Commit scheitern, nicht danach einen veralteten
Formularwert hinterlassen.

`DocumentChangePolicy` erklärt ausdrücklich, was bei einer Dokumentänderung geschieht.
`Reset` überspringt dabei Felder, die dieselbe Transaktion selbst zugewiesen hat — sonst
könnte eine ändernde Transaktion ihren eigenen Folgewert nie setzen.

## Commands, Extensions und Transforms

```scala
val Bold = EditorCommand.unit("bold")

val resolved = ExtensionResolver.resolve(Vector(CoreNodes, RichText(), Lists())).getOrElse(...)
val document = Document.build(resolved.schema, rootId, nodes).getOrElse(...)
val editor   = EditorSession.create(document, resolved, resolved.sessionConfig()).getOrElse(...)

editor.dispatch(Bold)  // Either[UpdateError, DispatchOutcome]
```

Ein Command wird über **Referenzgleichheit** nachgeschlagen, nie über einen String. Damit gibt
es keine Namenskollisionen zwischen Modulen, keine Tippfehler, die erst zur Laufzeit auffallen,
und der Compiler prüft den Payload-Typ. Handler laufen von `Critical` bis `Fallback`, bei
gleicher Priorität in Registrierungsreihenfolge; das erste `Handled` beendet die Kette.

`Pass` muss nebenwirkungsfrei sein. Wer ändert und trotzdem weiterreicht, hinterlässt einen
Zustand, mit dem der nächste Handler nicht rechnet — und der Fehler zeigt sich weit entfernt
von seiner Ursache. `SessionConfig.strictCommands` (Voreinstellung: an) fängt das ab.

### Transforms

Stellen Invarianten her, **bevor** etwas sichtbar wird — statt einer Kaskade aus
Listener-Updates, bei der jeder Zwischenstand kurz gilt (§3.2). Sie laufen nach dem
Transaktions-Body und vor Regeln und Reducern; eine Regel, die vorher urteilte, urteilte über
einen Zwischenstand.

Reihenfolge: Phase (`Early`/`Normalize`/`Late`), dann Knotentiefe (tiefste zuerst), dann
Registrierung. Bloß berührte Vorfahren sind **keine** Kandidaten — sonst liefe bei jedem
Tastendruck der Pfad bis zur Wurzel durch die Normalisierung.

Ein Transform bekommt `TransformScope`, nicht die ganze Transaktion. Kein `dispatch`, kein
Feldzugriff: §10s „nur den Entwurf lesen, keine Nebenwirkungen" ist damit Konstruktion statt
Behauptung.

Kommt die Schleife nicht zur Ruhe, **scheitert** die Transaktion mit den Namen aller
beteiligten Transforms. Das Budget ist ausdrücklich kein stilles Abschneiden (§10) — bei zwei
Regeln, die einander zurückdrehen, ist keine für sich auffällig, erst das Paar ist der Befund.

### Extensions

`resolve → validate → install → dispose in umgekehrter Reihenfolge`. Die Auflösung prüft
vollständig, bevor irgendetwas gebaut wird: doppelte Extensions, fehlende Abhängigkeiten,
Zyklen, doppelte Wire-Namen, mehrfache oder ins Leere zeigende Ersetzungen. Scheitert eine
Installation, wird alles bis dahin Installierte wieder abgebaut und **keine** Sitzung
herausgegeben.

Beiträge sind rein deklarativ: eine Extension-Fabrik ist ein Wert, sie beschreibt, was sie
beiträgt, und tut es nicht selbst. Deshalb lässt sich eine Konfiguration serverseitig auflösen
und ohne Browser prüfen.

Eine Knotenart-Ersetzung (§8.3) tritt an die Stelle des Originals, und der alte Wire-Name
bleibt auflösbar — sonst wäre jede bereits gespeicherte Datei nach einer Spezialisierung
undekodierbar.

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
