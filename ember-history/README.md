# scalajs-ember-history

Undo und Redo für den Ember-Editor: Snapshots, explizite Gruppierungsregeln, Grenzen. Headless
und optional — eine Anwendung ohne Undo linkt dieses Modul nicht mit.

Verbindlicher Entwurf: [UI_EDITOR_ARCHITECTURE.md](../UI_EDITOR_ARCHITECTURE.md) §14.

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-history` |
| Scala-Paket | `ember.editor.history` |
| Produktionsabhängigkeiten | `scalajs-ember-core` |

## Stand

P11 abgeschlossen. Vorhanden: `History` als Extension, die Gruppierungsregeln aus §14, Limits mit
geschätztem Byte-Budget, `HistoryCommands.Undo`/`Redo` und der Gruppenvertrag, den die
`CompositionSession` aus P23 benutzen wird.

## Verwendung

```scala
val history  = new History(HistoryConfig.default)
val resolved = ExtensionResolver.resolve(Vector(RichText(generator), history)).getOrElse(…)
val session  = EditorSession.create(document, resolved, resolved.sessionConfig()).getOrElse(…)

history.canUndo   // Boolean
history.undo()    // Either[UpdateError, Boolean] — false, wenn es nichts zurückzunehmen gab
history.redo()
history.reset()
```

Eine `History` ist veränderlich und gehört genau **einer** Sitzung. Sie zweimal zu installieren
ist ein Aufrufvertragsfehler, und der Kern gibt dann gar keine Sitzung heraus (§13).

Für Tastenbindungen (P22) gibt es zusätzlich die Commands:

```scala
session.dispatch(HistoryCommands.Undo)
```

## Warum die History nicht im Sitzungszustand steht

§14: „ViewState, DOM, Uploads und **rekursiv die History selbst** werden nicht in
History-Snapshots aufgenommen." Wäre sie ein `StateField`, stünde sie in jedem `EditorState` — und
jeder Snapshot enthielte alle vorherigen. Deshalb lebt sie in einem Objekt neben der Sitzung.

Der Preis ist sichtbar und wird nicht versteckt: dieses Modul ist der einzige Ort im Editor, an dem
ein Wert veränderlichen Zustand über Commits hinweg hält.

## Was ein Undo ist

Es setzt einen früheren Stand ein, es dreht keine Uhr zurück. §9: „Beide steigen auch bei Undo: der
wiederhergestellte Inhalt ist ein neuer Stand." Dokument und Selection kommen in **einem** Commit
zurück (§14) — nicht in zweien, sonst sähe ein Beobachter dazwischen einen Stand, den es nie gab.

Der Weg dorthin ist `Transaction.restore` im Kern. Das ist kein `Operation`: eine Operation weiß,
was sie bewirkt, und liefert ChangeSet und Positionsabbildung selbst; bei einer Wiederherstellung
gibt es nur zwei Stände, und der Unterschied wird ausgerechnet (`DocumentDiff`). §14 legt die
History ausdrücklich auf Snapshots fest und verweist operationsbasierte History in eine spätere
Erweiterung.

## Gruppierung

Die Regeln aus §14 stehen in `HistoryGrouping`, ohne Sitzung prüfbar:

| Regel | Wie sie hier entschieden wird |
| --- | --- |
| Zusammenhängendes Tippen verschmilzt | gleicher Knoten, der neue Splice beginnt, wo der letzte endete |
| … mit gleicher Mark-Konfiguration | die Markierungen des betroffenen Textlaufs sind gleich |
| … innerhalb des Zeitfensters | `mergeWindowMillis`, gemessen an der injizierten Uhr |
| Backspace und Delete sind getrennt | siehe unten |
| Struktur, Formatierung, Bereichsersetzung sind Grenzen | `EditKind.Structural`, verschmilzt nie |
| Ein Selection-Sprung beendet die Gruppe | ein selection-only-Commit schließt sie, ohne sie zu entfernen |

**Abgeleitet, nicht gemeldet.** Ein Editor könnte jeden Command beschriften („das war Backspace").
Die Beschriftung wäre eine zweite Wahrheit neben dem, was tatsächlich passiert ist, und beide liefen
auseinander, sobald ein Command etwas anderes tut als sein Name sagt. Was geschehen ist, steht im
`ChangeSet`.

### Backspace und Delete

Am Ergebnis sind sie ununterscheidbar: bei Caret 5 löscht Backspace `[4,5)` und lässt den Caret auf
4; bei Caret 4 löscht Delete `[4,5)` und lässt ihn auf 4. Gleicher Splice, gleiche Endposition.

Der Unterschied steht ausschließlich im Caret **davor**, und genau den liest `HistoryGrouping`
(`commit.previous.selection`). Ohne diesen Blick zurück wäre §14s Forderung, dass beide getrennte
Gruppen bilden, nicht erfüllbar.

### Explizite Gruppen

```scala
history.beginGroup(Some("composition"))
// … mehrere Transaktionen …
history.endGroup()
```

Alles dazwischen wird eine Stufe, mit dem Stand vor `beginGroup`. Hat sich nichts geändert, entsteht
nichts — genau der Vertrag, den §14 für die `CompositionSession` beschreibt. Composition selbst ist
P23; hier steht der Vertrag, den sie benutzen wird, und er ist ohne Browser prüfbar.

Nicht enthalten: das Zurückstellen fremder Transaktionen während der Gruppe. §14 verlangt es, aber
es ist eine Eigenschaft der Sitzung, nicht der History, und es betrifft nur Composition.

## Typisierte Metadaten

`Origin` und `HistoryPolicy` liegen im Kern und sind Teil von `TransactionMeta` — §14 führt beide
als typisierte Metadaten. Der Kern wertet sie nicht aus, er trägt sie.

| | |
| --- | --- |
| `Origin.History` | wird nie aufgezeichnet |
| `Origin.Import` | setzt die History zurück (abschaltbar über `HistoryConfig.resetOnImport`) |
| `HistoryPolicy.Push` | erzwingt eine eigene Stufe |
| `HistoryPolicy.Merge` | verschmilzt, auch wenn die Regeln getrennt hätten |
| `HistoryPolicy.Ignore` | zeichnet nicht auf |

Die Policy ist die **Ausnahme**, nicht der Regelfall: fehlt sie, entscheiden die Regeln. Wer sie
setzt, weiß etwas, das im ChangeSet nicht steht.

## Grenzen

`HistoryLimits` deckelt Stufenzahl und ein **geschätztes** Retained-Byte-Budget. §14 warnt
ausdrücklich davor, die Schätzung als Heapgröße darzustellen, und der Grund ist das Structural
Sharing: `before` und `after` teilen fast alle Knoten, und `after` der einen Stufe ist regelmäßig
dasselbe Objekt wie `before` der nächsten. Die Summe der Dokumentgrößen wäre um Größenordnungen zu
hoch.

Gezählt wird deshalb, was eine Stufe **zusätzlich** festhält: die Knoten, in denen sich ihre beiden
Stände unterscheiden. Auch das ist eine Näherung — aber sie wächst mit dem, womit der Speicherbedarf
tatsächlich wächst.

**Die neueste Stufe bleibt immer**, auch wenn sie das Budget allein sprengt. Die Alternative wäre
eine History, die ausgerechnet die letzte Aktion nicht zurücknehmen kann. §14 nennt den Fall und
verweist ihn woandershin: „ein riesiger einzelner Import ist separat zu behandeln" — und ein Import
setzt die History ohnehin zurück.

## Tests

```bash
sbt --server "scalajs-ember-history/Test/testOnly *"
```

`HistorySpec` fährt Undo, Redo, jede Gruppierungsregel, die typisierten Metadaten, die Commands und
die expliziten Gruppen. `HistoryRetentionSpec` prüft Trimmen, Budget und Freigabe.

Die Uhr ist injiziert (`HistoryClock.Fake`) — §14 verlangt es, und ohne sie wäre kein Zeitfenster
prüfbar: ein Test, der auf echte Millisekunden wartet, prüft die Systemuhr und nicht die Regel.

Eine Heapmessung gibt es unter Scala.js nicht, und eine erfundene wäre schlechter als keine.
`HistoryRetentionSpec` prüft deshalb das Beobachtbare: dass getrimmte Stufen verschwunden sind, dass
die Schätzung mitfällt, und dass nach einem Reset kein Snapshot mehr referenziert wird.
