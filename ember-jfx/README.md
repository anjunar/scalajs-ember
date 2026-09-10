# scalajs-ember-jfx

Die Dokumentansicht des Ember-Editors: eine keyed Projektion des Dokuments auf den
JFX-Komponentenbaum. Das einzige veröffentlichte Modul, das JFX kennt.

Verbindlicher Entwurf: [JFX_EDITOR_ARCHITECTURE.md](../JFX_EDITOR_ARCHITECTURE.md) §§5, 15.1.
Der Vertrag zur Runtime: [JFX_CORE_INTEGRATION.md](../JFX_CORE_INTEGRATION.md).

| | |
| --- | --- |
| sbt-ID / Artefakt | `scalajs-ember-jfx` |
| Scala-Paket | `ember.editor.jfx` |
| Produktionsabhängigkeiten | `scalajs-ember-core`, `scalajs-ember-html`, `com.anjunar:scalajs-jfx-core` |

`jfx-core` wird im Build als Quell-Abhängigkeit auf `../scalajs-jfx` eingebunden, im POM steht
aber das veröffentlichte Artefakt `com.anjunar:scalajs-jfx-core_sjs1_3`. Damit ist §6s
Publish-Regel gewahrt — ein veröffentlichtes Modul zeigt nur auf veröffentlichte Artefakte.
Nachprüfbar mit `sbt --server "scalajs-ember-jfx/makePom"`.

## Stand

P09 abgeschlossen. Vorhanden: `NodeView`, `DocumentProjection`, `DocumentView`,
`EditorProperties`. Noch nicht hier: Selection und Fokus (P21), Hydration (P20), native
Eingabe (P22).

## Was hier bewusst nicht steht

Kein zweiter Renderer, kein VDOM, kein Scheduler (§2, §15.1). `DocumentProjection` erzeugt kein
einziges DOM-Element und bewegt keines — sie ordnet Knoten-IDs Komponenten zu und ruft
`Runtime`-APIs. Besitz, Einfügen, Verschieben und Entfernen gehören ausschließlich JFX.

Der Index ist „eine Zuordnung, keine zweite Ownership-Liste". Er sagt, welche Komponente zu
welcher ID gehört; er sagt nicht, wer sie besitzt.

## Verwendung

```scala
val view = DocumentView.mount(session, DomCursor.root(container), ParagraphSupport.views)

view.componentFor(NodeId("t0"))   // Option[AbstractComponent]
view.projectedRevision            // was gerade zu sehen ist
view.onProjected(revision => …)   // und wann es sich ändert
view.dispose()
```

Derselbe Aufruf mit einem `SsrCursor` liefert die serverseitige Ausgabe. Dafür gibt es
`renderToHtml`, wenn es kein lebendes Dokument braucht:

```scala
DocumentView.renderToHtml(document, ParagraphSupport.views) // Profil Content
```

§9: „SSR rendert ein Document ohne lokale Selection, History oder Fokus." Genau deshalb nimmt
diese Methode ein `Document` und keine Sitzung.

## Commit und Projektion sind zwei Zeitpunkte

§5 trennt beides ausdrücklich. Der Kern veröffentlicht einen Zustand; ob eine Ansicht ihn
zeigt, ist eine andere Frage. Ein Commit-Konsument darf `commit.current` lesen, aber nicht
daraus schließen, dass irgendetwas gerendert ist.

| | |
| --- | --- |
| `onProjected(listener)` | meldet **nach** der Projektion die dargestellte Revision |
| `projectedRevision` | dasselbe zum Nachlesen |

Beim Mount wird nicht gemeldet — einen Zuhörer kann es zu diesem Zeitpunkt nicht geben, weil
die Ansicht erst danach zurückgegeben wird. Wer sich später registriert, hat trotzdem nichts
verpasst: `projectedRevision` sagt, was zu sehen ist.

## KeyedChildren, und warum der Aufbau eine Rekursion ist

Jeder Container bekommt eine `KeyedChildren`-Gruppe, gekeyt auf `NodeId`. Damit ist die
Reihenfolgeabstimmung nicht selbst geschrieben, sondern getesteter Code aus `jfx-core`, und ein
Knoten, dessen Wert gleich geblieben ist, wird gar nicht erst angefasst.

`build` hängt einem Container seine Gruppe **vor** dem Mount ein. Der Container montiert sie in
seinem `compose`, und die Gruppe ruft dabei wieder `build`. So entsteht der ganze Baum in einem
Durchgang, in Dokumentreihenfolge, und der Cursor steht bei jedem Kind genau dort, wo es
hingehört. Ein Nachtragen der Gruppen nach dem Mount hätte diese Reihenfolge nicht — der erste
Versuch in P09 lieferte dafür leere Absätze.

### Die Reihenfolge einer Anwendung

1. Entfernte Knoten aus dem Index nehmen — die Gruppen räumen sie selbst ab.
2. Verschobene Knoten zwischen Gruppen übertragen, über `transferTo`. **Vor** der Neuordnung,
   sonst sähe die Zielgruppe den Knoten als neu an und erzeugte ihn ein zweites Mal.
3. Textsplices anwenden. Ebenfalls vor der Neuordnung, damit der anschließende Wertvergleich
   der Gruppe keinen Unterschied mehr findet und den Text nicht ein zweites Mal schreibt.
4. Geänderte Kindlisten neu ordnen.
5. Geänderte Knoten nachführen.

Schritt 3 sieht nach Umweg aus und ist der Kern der Sache: ein `spliceText` schreibt
`CharacterData.replaceData` für den geänderten Bereich, ein `setText` den ganzen Lauf. Bei
einem langen Absatz ist das der Unterschied, um den es §15.1 geht.

## Was ein Adapter darf

§15.1: „Ein Adapter erhält immutable Node-Daten und Rendering-Kontext, nicht unbeschränkte
DOM-Schreibrechte." `NodeView.create` liefert eine Komponente, `update` führt sie nach —
beides ohne Cursor, ohne Zugriff auf Geschwister, ohne Möglichkeit, am Baum zu montieren.

Der Regelfall schreibt diesen Vertrag nicht selbst, sondern leitet ihn ab:

```scala
ViewSupport.semantic(ParagraphSupport.semantics)
```

Eigene Adapter sind für Atome gedacht, deren Inneres kein Textbereich ist (§8.1) — ein Bild mit
Auswahlrahmen, ein eingebettetes Diagramm.

Drei Komponentenarten entstehen dabei:

| | |
| --- | --- |
| `SemanticElement` | Tag und geprüfte Attribute; schreibt Unverändertes nicht erneut |
| `ContainerElement` | dazu die Kindergruppe |
| `TextRunElement` | dazu ein Textkind, das nie ausgetauscht wird |

Ein Knoten mit Kindern, dessen `NodeView` keinen `ContainerElement` liefert, ist ein
Vertragsbruch und wird als solcher gemeldet — nicht still ohne Kinder gerendert. Dasselbe gilt
für einen Tagwechsel unter gleicher ID: das ist nach §15.1 eine ausdrückliche View-Ersetzung,
die es noch nicht gibt, und ein stehengebliebenes `<p>` unter einer Überschrift wäre die
schlechtere Auskunft.

## EditorProperties

Ein lesender Adapter zwischen Sitzungszustand und JFX-Properties — für eine Toolbar, die „kann
rückgängig machen" anzeigt, oder eine Statuszeile mit der Wortzahl.

```scala
val (document, cleanup) = EditorProperties.document(session)
```

Nur lesend, und das ist keine Bequemlichkeitsentscheidung. §4: „Eine Property ist weder
Transaktion noch History." Sie benachrichtigt synchron und einzeln; ein schreibender Adapter
würde eine Änderung an der Commit-Grenze vorbeiführen und damit Atomarität, ChangeSet und
Positionsabbildung umgehen. Wer ändern will, nimmt `session.update` oder einen Command.

Der Rückgabewert enthält die Aufräumaktion. Ohne sie überlebte die Registrierung die
Komponente, die sie angelegt hat.

## Tests

Dieses Modul hat keine eigene Suite — eine Projektion ohne Knotenarten hat nichts zu zeigen.
Geprüft wird sie dort, wo beide Seiten zusammenkommen:

```bash
sbt --server "scalajs-ember-standard/Test/testOnly *"   # headless, gegen SsrCursor
cd ember-integration/browser && npm run verify           # DOM-Identität, Schreibumfang
```
