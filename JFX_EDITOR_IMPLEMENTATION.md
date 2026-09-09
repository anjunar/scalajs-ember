# JFX Editor: ausführbarer Implementierungsplan

Status: **P01–P04 abgeschlossen** (207 Tests grün, `sbt --server "Test/testOnly *"`),
P05–P30 offen. Dieses Repository (`scalajs-ember`) ist das in
Architektur und Plan gemeinte „eigene Repository“. Die generischen JFX-Core-Anteile aus
P08/P09, P19a, P20 und P23 sind **nicht hier, sondern im Nachbar-Repo `../scalajs-jfx`**
implementiert und über eine Quell-Abhängigkeit auf dessen Submodul `jfx-core` eingebunden
(`ProjectRef` in [build.sbt](build.sbt)); der Vertrag steht in
[JFX_CORE_INTEGRATION.md](JFX_CORE_INTEGRATION.md). Das erledigt nicht die jeweiligen
Editor-Integrationsphasen. Stand: 9. September 2026.

Verbindliche Grundlage ist [JFX_EDITOR_ARCHITECTURE.md](JFX_EDITOR_ARCHITECTURE.md). Der Editor wird neu gebaut. Der Prototyp wird weder analysiert noch intern weiterentwickelt; öffentliche API-Namen können als Inspiration dienen. Bestehende Nutzerdaten und öffentliche Konsumenten werden erst bei der bewussten Ablösung betrachtet.

### Abweichungen gegenüber dem ursprünglichen Plan

Der Plan entstand in `scalajs-jfx` und wurde beim Umzug hierher angepasst. Was sich geändert hat:

- **Namen.** Verzeichnis `ember-<modul>`, sbt-ID und Artefakt `scalajs-ember-<modul>`,
  Scala-Paket `ember.editor.<modul>`. Vorher `jfx-editor-*` / `scalajs-jfx-editor-*` / `jfx.editor.*`.
- **Zwei Repositories.** Pfade ohne Präfix liegen hier, Pfade mit `../scalajs-jfx/` im Nachbar-Repo.
  Phasen, deren „Ändern“-Liste ausschließlich `../scalajs-jfx/`-Pfade enthält (P08, P19a, der
  `KeyedChildren`-Anteil von P09, der `HydrationBoundary`-Anteil von P20, der
  `HostMutationGuard`-Anteil von P23), sind dort bereits erledigt und hier nur noch als
  Voraussetzung zu **prüfen**, nicht zu implementieren.
- **Der lokale Prototyp ist entfernt.** `ember-core` enthielt einen `contenteditable`-/
  `execCommand`-Editor mit HTML-String als Zustand — genau das, was Architektur §2 und §25
  ausschließen. Er wurde samt vite-Demo gelöscht, bevor P01 beginnt. Architektur §25 („Ablösung“)
  betrifft daher nur noch den Prototyp und `scalajs-lexical` **im Nachbar-Repo**.
- **Kein Git.** Dieses Repository ist (noch) keine Git-Arbeitskopie; `git status --short` im
  Arbeitsvertrag unten entfällt, solange das so bleibt.

## Arbeitsvertrag für jede Phase

Eine Ausführung bearbeitet eine Phase, prüft deren Dependencies und Abnahme und aktualisiert anschließend den belegten Status. Ist ein Vertrag widerlegt, zuerst Architektur und Plan mit Ursache korrigieren. Kein Ersatzrenderer, keine zweite Property-Runtime und kein Kopieren unverständlicher Lexical-Browserzweige. Kein vorzeitiges Umstellen produktiver Einstiege.

Vor Beginn: [../scalajs-jfx/AGENTS.md](../scalajs-jfx/AGENTS.md) (dieses Repo hat noch keine eigene), Architekturabschnitte der Phase und die tatsächlichen Quelldateien lesen — hier *und*, wo die Phase sie nennt, in `../scalajs-jfx`. Fremde Änderungen bleiben erhalten. Die Editor-Phasen stehen sämtlich auf **offen**; die bereits verfügbaren JFX-Voraussetzungen werden im separaten Core-Vertrag beschrieben. Keine Freigabe durch einen grünen Prototyptest ableiten.

Pfadkonventionen in den Phasen:

- `C` = `ember-core/src/main/scala-3/ember/editor/core/`.
- Für Modulkurzname `M` steht `M/Foo.scala` für `ember-M/src/main/scala-3/ember/editor/<paket>/Foo.scala`; Bindestriche entfallen im Scala-Paketnamen (`rich-text` → `richtext`, `browser-support` → `browsersupport`, `code-highlighting` → `codehighlighting`). Die folgenden Dateien ohne erneut angegebenen Präfix liegen jeweils im selben Modul-/Paketverzeichnis wie die erste Datei ihrer Gruppe.
- Scala-Tests liegen entsprechend unter `src/test/scala-3/ember/editor/<paket>/` und haben die angegebenen Suite-Namen. Dies sind konkrete geplante Pfade, keine bereits existierenden Dateien.
- `IT` = neues **nicht publiziertes** `ember-integration/` mit Scala-Test-App unter `src/main/scala-3/ember/editor/integration/` und Browser-Harness unter `browser/`. Es wird in P07 eingerichtet.
- JFX-Core-Pfade und vorhandene npm-Pfade werden vollständig relativ zum Repository angegeben. Modulnamen/Projekt-IDs folgen der Tabelle in Architektur §6.
- Verkürztes `../scalajs-jfx/jfx-core/.../` bezeichnet in Quelldateigruppen `../scalajs-jfx/jfx-core/src/main/scala-3/jfx/core/`, in Testdateigruppen `../scalajs-jfx/jfx-core/src/test/scala-3/jfx/core/`; ein nachfolgendes `.../` behält den Basispräfix der unmittelbar davor ausgeschriebenen Datei. Geschweifte Dateigruppen sind einzelne Dateien desselben Verzeichnisses.

Beim ersten Anlegen eines Moduls werden `build.sbt`, `dependsOn`, Root-Aggregation, Test- und Publishing-Settings angepasst. Produktionsabhängigkeiten entstehen nur in Pfeilrichtung des Architekturgraphen. Noch nicht benötigte Projekte werden nicht als leere Platzhalter angelegt. Abhängigkeiten auf spätere Browserintegration werden erst in deren Phase ergänzt; §6 beschreibt den endgültigen Graphen.

### Test- und Commit-Vertrag

Für die schnelle Schleife gezielte neue Suites mit `Test/testOnly`, beispielsweise nach Anlage des Core-Projekts:

```powershell
sbt --server "scalajs-ember-core/Test/testOnly ember.editor.core.DocumentSpec"
```

Vor jedem Commit der vollständige Lauf:

```powershell
sbt --server "Test/testOnly *"
```

Achtung: Die Quell-Abhängigkeit lädt `../scalajs-jfx` als zweiten Build mit. `Test/testOnly *`
von hier aus deckt die aggregierten Projekte **dieses** Builds ab, nicht die von scalajs-jfx.
Wer dort etwas ändert, führt das Gate zusätzlich im Nachbar-Repo aus:

```powershell
cd ../scalajs-jfx
sbt --server "Test/testOnly *"
sbt --server "scalajs-jfx-core-browser-tests/fullLinkJS" "scalajs-jfx-bridge/fullLinkJS"
npm run verify --workspaces --if-present
```

Der lokale Browser-Harness kommt erst mit P07 (`ember-integration`); bis dahin gibt es hier
kein npm-Gate. Die vite-Demo des Prototyps ist entfernt, `npm run dev` existiert nicht mehr.

`sbt --server test` ist in sbt 2 nur `testQuick`; „No tests to run“ ist keine Abnahme. Immer `sbt`, niemals `sbtn` — und hier immer mit `--server`, weil der sbtn-Thin-Client auf diesem Rechner den Serverstart nicht schafft. Keine generierten JavaScript-Sourcen durchsuchen oder editieren. Geplante Browserbefehle werden in P07 verbindlich eingerichtet und erst dann verwendet. Reale IME-/Screen-Reader-Abnahmen benötigen dokumentierte manuelle Tests, die ein Agent nicht durch synthetische Events als erledigt markieren darf.

## Abhängigkeits- und Meilensteinübersicht

| Phase | Ergebnis | Voraussetzungen |
| --- | --- | --- |
| P01 | Core-Projekt und Grenztests | Architektur |
| P02 | Immutable Document/Schema/IDs | P01 |
| P03 | Operationen und Selection-Mapping | P02 |
| P04 | Atomare Transaktionen und StateFields | P03 |
| P05 | Commands/Extensions/Transforms | P04 |
| P06 | Kleiner headless Texteditor | P05 |
| P07 | Reale Scala.js-Browser-Test-App | P06 |
| P08 | JFX-Text-Splices und begrenzte Moves | P07 |
| P09 | Keyed JFX-Projection und semantisches SSR | P08 |
| P10 | Versioniertes JSON | P05 |
| P11 | History | P06 |
| P12 | Marks und Rich-Text-Struktur | P06, P09, P11 |
| P13 | Listen | P12 |
| P14 | Links | P12 |
| P15 | Code | P12 |
| P16 | Image-/Media-Modell | P05, P09, P10 |
| P17 | Markdown-Blockparser | P06 |
| P18 | Markdown-Inlineparser/Writer/Adapter | P13–P17, P10 |
| P19a | Generischer JFX-Textarea-Vertrag | P07, P09 |
| P19b | Source-Formular und No-JS-SSR | P10, P18, P19a |
| P20 | Isolierte Hydration und Verlustschutz | P07, P09, P19 |
| P21 | DOM-Selection und Fokus | P09, P20 |
| P22 | Normale Eingabe und NativeInput | P11–P15, P21 |
| P23 | Composition und Mutation-Recovery | P22 |
| P24 | Sicherer HTML-Import | P09, P13–P16 |
| P25 | Clipboard und strukturierter Drop | P10, P23, P24 |
| P26 | Async-Medien und Multipart-Vertrag | P16, P19, P23, P25 |
| P27 | Optionale UI/Toolbar/Dialoge | P11, P14, P21, P26 |
| P28 | Qualitäts-, Geräte- und Bundle-Abnahme | P18–P27 |
| P29 | TypeScript-Fassade in gemeinsamer Runtime | P28 |
| P30 | Ablösung und Entfernung von Lexical | P29 |

Meilenstein A: P01–P06. Rendererbeweis B: P07–P09. Format-/Fallback-Meilenstein C: P10, P12–P20. Editing-Meilenstein D: P11, P21–P23. Austausch/Media E: P24–P26. Produktintegration F: P27–P30.

`P19` bezeichnet im übrigen Plan beide separat ausführbaren Schritte P19a und P19b; abgeschlossen ist P19 erst nach beiden Abnahmen.

P10, P11 und P17 sind nach ihren jeweiligen Voraussetzungen unabhängig vom Rendererstrang ausführbar. P13–P16 lassen sich mit getrennten Moduldateien parallel bearbeiten; Änderungen an `build.sbt` und Integrationsregistrierungen werden koordiniert. P20–P23 bilden den wichtigsten Browserpfad. Ein Scheitern der JFX-Ownership-/Composition-Nachweise sperrt P28 und die Ablösung.

## P01 — Core-Projekt und Abhängigkeitsgrenze

> **Abgeschlossen.** `scalajs-ember-core` steht mit Fehlerkonvention
> ([package.scala](ember-core/src/main/scala-3/ember/editor/core/package.scala)), Grenz-Gate
> (`boundaryCheck` in [build.sbt](build.sbt), [project/EditorBoundary.scala](project/EditorBoundary.scala))
> und [CoreEnvironmentSpec](ember-core/src/test/scala-3/ember/editor/core/CoreEnvironmentSpec.scala).
> Abnahme: `sbt --server "scalajs-ember-core/Test/testOnly *"` → 12 Tests grün.
> Modulvertrag: [ember-core/README.md](ember-core/README.md).
>
> **Widerlegte Annahmen dieser Phase:**
>
> 1. *„Build-/Dependency-Prüfung“ als Test.* Die Phase listet sie unter **Tests**. Ein
>    Scala.js-Test kann sie nicht leisten — das gelinkte Modul hat weder Classpath noch
>    Dateisystem. Die Prüfung liegt deshalb im Build (`boundaryCheck`) und läuft über
>    `Compile / sources` vor jedem Compile und damit vor jedem `testOnly`.
> 2. *`Compile / compile` als Hook.* In sbt 2 scheitert die Neuzuweisung an
>    `JsonFormat[xsbti.compile.CompileAnalysis]`; sie ginge nur mit `Def.uncached` und hätte
>    den Compile-Schritt aus dem Action-Cache genommen (gemessen 78–100 % Trefferquote). Der
>    Hook sitzt daher an `Compile / sources`. Der Lint selbst *ist* `Def.uncached` — er liest
>    `update` und `thisProject`, die sbt nicht hashen kann, und ein Lint mit Cache-Treffer wäre
>    wertlos.
> 3. *`commonLibrarySettings` anpassen.* Der Block ist ersatzlos entfallen. Er bündelte
>    `scalajs-dom` und ScalaTest; getrennte `testSettings`/`domSettings` machen die
>    DOM-Abhängigkeit zur bewussten Wahl eines Moduls statt zu einer, die man vergisst
>    auszunehmen. Genau das war das unter **Risiken** genannte Problem.
> 4. *Negativnachweis.* Alle drei Verletzungsarten wurden einmal absichtlich ausgelöst
>    (verbotener Import, `scalajs-dom` als `libraryDependency`, `dependsOn` auf ein
>    Dummy-Projekt) und brachen den Build ab.

- **Ziel:** Ein publizierbares, browserfreies Scala.js-Modul, ohne den vorhandenen Editor anzufassen.
- **Module:** Neues `ember-core`; vorhandene Buildkonfiguration.
- **Neue Dateien:** `ember-core/README.md`, `C/package.scala`; Test `CoreEnvironmentSpec.scala`.
- **Ändern:** `build.sbt`: neues Projekt `scalajs-ember-core`, Root-Aggregation und eigene Settings ohne die pauschale `scalajs-dom`-Dependency aus `commonLibrarySettings`.
- **API:** Zunächst nur Paket- und Fehlerkonvention, keine leeren Feature-APIs.
- **Tests:** Import/Initialisierung im Node-Testprozess ohne `window/document`; Build-/Dependency-Prüfung gegen unerlaubte JFX/DOM/UI-Imports.
- **Akzeptanz:** Core kompiliert/testet unabhängig von Forms/Controls/Lexical; veröffentlichbare POM-Dependencies sind auflösbar; der bisherige Build bleibt lauffähig.
- **Risiken:** Allgemeine sbt-2-Settings gelten für alle Projekte; Änderungen daran können ungewollte Dependencies injizieren.
- **Dependencies:** Architektur §§5–7 und 24.

## P02 — Document, NodeTypes und stabile IDs

> **Abgeschlossen.** Alle geplanten Dateien liegen unter
> `ember-core/src/main/scala-3/ember/editor/core/`. Abnahme:
> `sbt --server "Test/testOnly *"` → 66 Tests grün (54 neue).
>
> **Widerlegte Annahmen und Abweichungen:**
>
> 1. *„Fehlender Element-Deskriptor verhindert eine Schema-Registrierung als Container."*
>    (Architektur §8.1, letzter Absatz.) Das geht nicht: bei der Registrierung liefert ein
>    Deskriptor seinen Node-Typ nur über `project`, es gibt zur Registrierungszeit keinen
>    Knoten, an dem sich prüfen ließe, ob er Kinder trägt. Die Prüfung sitzt deshalb in der
>    Validierung — ein [[ElementNode]], dessen Deskriptor kein `ElementNodeType` ist, ergibt
>    `Violation.MissingElementDescriptor`. Wirkung identisch (ein solcher Container kommt nie
>    in ein gültiges Dokument), Zeitpunkt anders. **Architektur §8.1 ist entsprechend zu
>    korrigieren.**
> 2. *Generator „reserviert vorhandene IDs".* Ein eigener Reservierungszustand kann mit dem
>    Dokument aus dem Tritt geraten. `NodeIdGenerator.next(isTaken)` fragt stattdessen den
>    Aufrufer; das Dokument ist die Wahrheit darüber, welche IDs vergeben sind. `nextBatch`
>    zählt die noch nicht eingefügten selbst mit.
> 3. *Geschlossenes `Violation`-ADT bei offenem Node-Vertrag.* Beides steht in §8.1 und ist
>    zunächst widersprüchlich — ein fremder Deskriptor muss ablehnen können, darf aber keine
>    Fehlerart hinzufügen. Aufgelöst über `Violation.NodeRejected(nodeId, reason, path)` als
>    einzige Escape-Luke: fremder Grundtext, aber keine neue Fallunterscheidung für Aufrufer.
> 4. *Validierung bricht nach der ersten fehlerhaften Stufe ab.* Nicht im Plan gefordert, aber
>    nötig für die Akzeptanz „Pfad/ID/Grund": aus einer ins Leere zeigenden Kindreferenz folgen
>    sonst zwangsläufig Unerreichbarkeits- und Schemafehler, die die Ursache zudecken.
> 5. *Zwei zusätzliche Suites.* `MarkSetSpec` und `NodeIdGeneratorSpec` neben den geplanten
>    `DocumentSpec`/`SchemaSpec`. `MarkSet` trägt die Zusicherung, an der in P12 das
>    Wiederzusammenwachsen getrennter Textläufe hängt; die soll dort einzeln auffindbar sein.
>    Dazu das Fixture `ember-core/src/test/scala-3/ember/editor/foreign/ForeignNodes.scala` —
>    der geforderte „Custom Node aus getrenntem Testpaket" braucht eine eigene Datei außerhalb
>    von `ember.editor.core`, sonst könnte er sich unbemerkt auf `private[core]` stützen.
> 6. *Kein ScalaCheck.* Die generativen Tests verwenden `scala.util.Random` mit festem Seed.
>    Reproduzierbar, und der Kern bleibt ohne weitere Testabhängigkeit.
> 7. *`Document.trusted` ist `private[core]`.* Die Operationen aus P03 müssen ein Ergebnis
>    zusammensetzen können, dessen Gültigkeit sie selbst sichergestellt haben, ohne bei jedem
>    Tastendruck den ganzen Baum neu zu validieren. Von außen führt der einzige Weg über
>    `Document.build`.

- **Ziel:** Kanonisches, immutable Modell mit vollständiger Strukturvalidierung.
- **Module:** core.
- **Neue Dateien:** `C/NodeId.scala`, `EditorNode.scala`, `NodeType.scala`, `TextMark.scala`, `MarkSet.scala`, `Schema.scala`, `Document.scala`, `DocumentRead.scala`, `DocumentValidator.scala`, `NodeIdGenerator.scala`; Tests `DocumentSpec.scala`, `SchemaSpec.scala`.
- **Ändern:** Core-Paketdokumentation; Testkonfiguration für deterministische generative Testdaten bei Bedarf.
- **API:** Root/Text/Element/Atom-Verträge, offene Mark-/MarkSet-Verträge ohne konkrete Rich-Marks, private Document-Konstruktion über validierten Builder, `NodeType[N].project/rekey`, `ElementNodeType[N].withChildren`, iterativer Visitor, injizierter ID-Generator. Parent-Index nur abgeleitet, keine öffentliche mutable Map.
- **Tests:** Zyklen, Mehrfacheltern, Orphans, doppelte IDs, falsche Root, unbekannter Typ, tiefe Bäume, Custom Node aus getrenntem Testpaket; rekey/withChildren erhalten fremde Zusatzfelder; IDs nach Snapshot bleiben stabil.
- **Akzeptanz:** Jeder erfolgreich gebaute Document erfüllt die Invarianten; ungültige Eingaben liefern Pfad/ID/Grund; fremde Feature-Nodes erfordern keine Core-Änderung.
- **Risiken:** Ein sealed Node-Hauptvertrag würde externe Erweiterungen verhindern; öffentliche Child-/Node-Maps dürfen Mutationen nicht ermöglichen.
- **Dependencies:** P01; Architektur §8.

## P03 — Primitive Operationen und Selection-Mapping

> **Abgeschlossen.** Alle geplanten Dateien liegen unter
> `ember-core/src/main/scala-3/ember/editor/core/`; `Document.scala` und `DocumentRead.scala`
> sind entsprechend erweitert. Abnahme: `sbt --server "Test/testOnly *"` → 152 Tests grün
> (86 neue). Die Regeltabelle aus §11 ist Zeile für Zeile in `PositionMappingSpec` abgebildet.
>
> **Ergänzungen gegenüber dem Plan:**
>
> 1. *`MappedPoint` mit `Preserved`/`Displaced`.* Ohne diese Unterscheidung wäre
>    `ExpiredBookmark` nicht umsetzbar. Ein Caret **darf** auf eine Grenze zurückfallen — er
>    muss irgendwo stehen. Ein Upload-Bookmark darf das nicht: wäre das Zielbild gelöscht,
>    setzte eine Einfügung an der Grenze das Ergebnis an beliebiger anderer Stelle ein (§20).
>    Beide brauchen dieselbe Abbildung, aber verschiedene Antworten darauf.
> 2. *`Revision` hier statt in P04.* `Bookmark` kann ohne sie nicht sagen, worauf es sich
>    bezieht. Derselbe Typ wird in P04s `EditorState` verwendet.
> 3. *`SelectionSupport`-Registry.* §11 verlangt den Erweiterungspunkt für X01s Zellbereich.
>    Nachträglich eingeführt würde er jede Aufrufstelle ändern, deshalb jetzt. Belegt durch
>    `CellRangeSelection` im Fremdmodul-Fixture.
> 4. *`DocumentRead.comparePoints` / `pathIndices` / `indexOfChild`.* „Vorwärts/rückwärts ergibt
>    sich aus der aktuellen Dokumentordnung, nicht aus lexikographischer ID-Sortierung" (§11)
>    braucht einen tatsächlichen Ordnungsvergleich über den Baum.
>
> **Präzisierungen, die der Plan offenließ:**
>
> 5. *`Replace` lässt die Kindliste unangetastet.* §10 nennt „replace" als Primitiv, ohne den
>    Umfang festzulegen. Dürfte es Kinder umhängen, würden die bisherigen zu Waisen — und die
>    Abbildungsregel wäre nicht mehr bestimmbar. So ist `Replace` genau das „updated" aus dem
>    ChangeSet, Struktur läuft über Insert/Remove/Move.
> 6. *`Insert` nimmt einen ganzen Teilbaum.* Ein einzelner Knoten mit Kindreferenzen auf noch
>    nicht eingefügte Knoten wäre ein ungültiger Zwischenstand, und §10 verlangt, dass keiner
>    sichtbar wird.
> 7. *`MergeText` weist verschiedene Marks ab.* Ein Merge darüber hinweg verlöre Formatierung
>    stillschweigend. Die Normalisierung aus §8.2 führt ohnehin nur gleich markierte Läufe
>    zusammen; das Primitiv verlässt sich nicht darauf, sondern lehnt ab.
> 8. *Surrogatpaar-Prüfung an Schnittstellen.* Ein Schnitt mitten in ein Paar erzeugte zwei
>    Strings mit je einem halben Codepoint. Das ist eine UTF-16-Gültigkeitsfrage, keine
>    Unicode-Segmentierung — Graphemcluster bleiben Sache des `TextBoundaryService` (P06).
>
> **Befund aus der Umsetzung:**
>
> 9. *Kindgrenzen folgen beim Umsortieren ihrem Inhalt, nicht ihrer Nummer.* Beim Reorder
>    `[t1, t2]` → `[t2, t1]` bildet die komponierte Abbildung `Children(c1, 2, Before)` auf
>    `Children(c1, 1, Before)` ab, nicht auf 2. Das ist richtig: mit `Before` klebt der Punkt an
>    `t2`, und die Position unmittelbar hinter `t2` ist nach dem Umsortieren Offset 1. Meine
>    erste Testerwartung war naiv; der Fall steht jetzt mit beiden Affinitäten in
>    `PositionMappingSpec` und ist dort begründet. Das ist genau das Off-by-one, vor dem die
>    Risikozeile dieser Phase warnt — nur an anderer Stelle als vermutet.

- **Ziel:** Strukturänderung und Positionsabbildung als eine atomar berechenbare Operation.
- **Module:** core.
- **Neue Dateien:** `C/Selection.scala`, `Point.scala`, `PositionMapping.scala`, `Operation.scala`, `ChangeSet.scala`, `Bookmark.scala`, `TextBoundaryService.scala` (nur Interface); Tests `OperationSpec.scala`, `PositionMappingSpec.scala`, `DocumentOperationModelSpec.scala`.
- **Ändern:** `Document.scala`, `DocumentValidator.scala` für kontrolliertes insert/remove/move/replace/splice/split/merge.
- **API:** Range/NodeSelection, Text-/Children-Punkte mit Affinität, `applyOperation` auf immutable Ausgangswert, Resultat mit neuem Document/ChangeSet/Mapping. Noch keine Browser-Selection.
- **Tests:** Alle Mapping-Regeln aus Architektur §11, rückwärts gerichtete Bereiche, genaue Insert-Grenzen, gelöschte Vorfahren, Reorder, ID-Remapping und abgelaufene Bookmarks; Insert/Move/Rekey mit fremdem Element-/Atomtyp und erhaltenen Metadaten; zufällige Operationsfolgen gegen einfaches Referenzmodell.
- **Akzeptanz:** Kein ungültiger Zustand nach erfolgreicher Operation; Move erhält Node-Identität; Fehler verändert Ausgangswert nicht; alter Snapshot bleibt lesbar.
- **Risiken:** Child-Offsets verschieben sich bei Move an zwei Stellen; Mapping-Reihenfolge ist fachlich relevant. Kein lexikographischer ID-Vergleich für Dokumentordnung.
- **Dependencies:** P02; Architektur §§8, 11.

## P04 — EditorState und atomare Transaktionen

> **Abgeschlossen.** Alle geplanten Dateien liegen unter
> `ember-core/src/main/scala-3/ember/editor/core/`. Abnahme:
> `sbt --server "Test/testOnly *"` → 207 Tests grün (55 neue). Schritt 4 der Commit-Reihenfolge
> (Transforms) bleibt planmäßig P05 vorbehalten.
>
> **Befund aus der Umsetzung:**
>
> 1. *Eine ausdrückliche Feldzuweisung schlägt `DocumentChangePolicy.Reset`.* Meine erste
>    Fassung wandte die Policy auf den fertigen Kandidaten an und kassierte damit einen Wert
>    wieder ein, den dieselbe Transaktion gerade gesetzt hatte. Ein Test hat es gefangen. Damit
>    wären Reset-Felder praktisch unbenutzbar gewesen: der häufigste Fall ist gerade, dass eine
>    dokumentändernde Transaktion den zugehörigen Folgewert mitsetzt. Die Transaktion merkt sich
>    jetzt, welche Felder sie selbst zugewiesen hat; nur die übrigen werden zurückgesetzt.
>
> **Ergänzungen gegenüber dem Plan:**
>
> 2. *Zwei Revisionen, immer getrennt.* §9 erlaubt die Trennung „bei Bedarf"; hier ist sie fest.
>    `revision` steigt bei jeder Veröffentlichung, `documentRevision` nur bei echter
>    Dokumentänderung. Wer speichert, vergleicht die zweite — ein bewegter Cursor löst dann
>    keinen Schreibvorgang aus. Eine bedingte Trennung wäre komplizierter und hätte denselben
>    Zweck.
> 3. *`session.mappingSince(revision)` mit begrenzter Retention.* §11 verlangt einen
>    `ExpiredBookmark`, „wenn benötigte Maps nicht mehr verfügbar sind" — dafür muss sie jemand
>    halten. Die Sitzung tut das, begrenzt über `SessionConfig.mappingRetention`. Ein Bookmark
>    jenseits des Fensters läuft ab, statt eine Position zu raten.
> 4. *Error-Sink.* Ein geworfener Listener und ein fehlgeschlagenes Update aus der
>    Warteschlange haben keinen Aufrufer, der ein `Either` entgegennehmen könnte. Ohne Sink
>    verschwänden beide spurlos. Voreinstellung ist ein Nichtstuer; eine Anwendung sollte hier
>    protokollieren.
> 5. *`SessionConfig`.* Bündelt Felder, Regeln, Auswahlarten, Retention und Sink. P05 baut sie
>    aus Extensions zusammen.
>
> **Präzisierungen, die der Plan offenließ:**
>
> 6. *Ein No-op liefert `Right(commit)` mit `isNoOp`, benachrichtigt aber niemanden.* So bleibt
>    `update` total und der Aufrufer erfährt, dass nichts passiert ist, ohne dass Listener für
>    nichts geweckt werden.
> 7. *Eine geworfene Exception im Body propagiert.* Sie ist ein Programmierfehler und wird nach
>    P01s Konvention nicht in ein `Left` verwandelt. Die Sitzung bleibt trotzdem konsistent:
>    veröffentlicht wird erst ganz am Ende, und das Handle läuft im `finally` ab.
> 8. *Die Transaktion rastet den ersten Fehlschlag ein.* Die Closure liefert `Unit`, ein
>    ignoriertes `Either` darf also nicht dazu führen, dass auf einem kaputten Entwurf
>    weitergearbeitet wird.
> 9. *`dispose` aus einem Listener heraus bricht die Benachrichtigungsrunde ab.* Weitere
>    Konsumenten einer entsorgten Sitzung zu bedienen wäre schlechter, als sie zu übergehen.
> 10. *`enqueueUpdate` außerhalb einer Transaktion läuft sofort.* Alles andere wäre eine
>     Überraschung; innerhalb einer Transaktion landet es wie vorgesehen in der FIFO-Schlange.

- **Ziel:** Eine synchrone, serialisierte Commit-Grenze mit definierten Fehlern und Listenern.
- **Module:** core.
- **Neue Dateien:** `C/EditorState.scala`, `EditorSession.scala`, `Transaction.scala`, `TransactionMeta.scala`, `StateField.scala`, `PreCommitRule.scala`, `Subscription.scala`, `UpdateError.scala`; Tests `TransactionSpec.scala`, `StateFieldSpec.scala`, `SubscriptionSpec.scala`.
- **Ändern:** Operations-/ChangeSet-API aus P03, soweit mehrere Draft-Operationen komponiert werden.
- **API:** `update(tx => Unit): Either[UpdateError, Commit]`, immutable Snapshot, `enqueueUpdate`, typed StateFields und Commit-Subscription; synchrone PreCommit-Regeln und mit typisiertem Fehler ablehnende Field-Reducer; private Drafts mit Lifetime-Prüfung.
- **Tests:** Mehrere Operationen ein Commit; Fehler vollständiger Rollback; Tx nach Ablauf unbenutzbar; nested update abgewiesen; Listenerfehler isoliert; FIFO-Folgeupdates, No-op, Dispose während Notification.
- **Akzeptanz:** Keine Zwischenzustände sichtbar; Index und Selection passen zu derselben Revision; Folgeupdate beginnt erst nach Abschluss der Notification/Projection-Phase. Async-Ergebnisse müssen neue Updates beginnen.
- **Risiken:** „State committed“ und „View gerendert“ sind getrennte Zeitpunkte. Ein Listenerfehler darf nicht einen gültigen Commit halb zurückrollen.
- **Dependencies:** P03; Architektur §§9–10.

## P05 — Commands, Extensions und Transforms

- **Ziel:** Funktionen unabhängig installieren, priorisieren und zuverlässig aufräumen.
- **Module:** core.
- **Neue Dateien:** `C/EditorCommand.scala`, `CommandRegistry.scala`, `Extension.scala`, `ExtensionResolver.scala`, `Transform.scala`, `TransformQueue.scala`; Tests `CommandSpec.scala`, `ExtensionSpec.scala`, `TransformSpec.scala`.
- **Ändern:** `EditorSession.scala`, `Transaction.scala`, `Schema.scala` für Installations-/Normalize-/Dispatch-Phasen.
- **API:** Typisierte Commands mit Instanzidentität, benannte Prioritäten, Pass/Handled, `tx.dispatch`; deklarative Dependencies, Konfigurationsbeiträge und Factory-Replacement. Transforms registrieren gegen `NodeType[N]`.
- **Tests:** Falsche Payloads als Compile-Negativtests; Reihenfolge/Cancellation; Pass mit Mutation diagnostiziert; Zyklen, doppelte Typen/Replacement, widersprüchliche Konfiguration; install rollback; nicht terminierende/wechselnde Transforms.
- **Akzeptanz:** Auflösungsfehler vor Installation; Transformfehler verwirft Tx; vollständiger Cleanup genau einmal; Custom Extension ohne UI möglich.
- **Risiken:** Heterogene Typregistrierung benötigt gekapselte Typzeugen; kein `Any` als öffentliche API und kein globaler activeEditor.
- **Dependencies:** P04; Architektur §§10, 12–13.

## P06 — Kleiner headless Texteditor

- **Ziel:** Erste vollständig nutzbare vertikale Core-Funktion: Paragraph, Caret, Einfügen, Löschen und Paragraph-Split.
- **Module:** Neues rich-text.
- **Neue Dateien:** `rich-text/ParagraphNode.scala`, `RichText.scala`, `TextEditing.scala`, `UnicodeTextBoundaries.scala` (Implementierung des Core-Interfaces); Tests `TextEditingSpec.scala`, `UnicodeBoundarySpec.scala`.
- **Ändern:** `build.sbt`; Core-API nur bei belegter benötigter primitiver Operation.
- **API:** `RichText()`, `insertText(tx, text)`, `deleteBackward/Forward`, `insertParagraph`, leere Dokumentnormalisierung; deterministische Unicode-Grenzen mit deklarierter Datenversion.
- **Tests:** Einfügen am Caret/in Range, Mehrnode-Ersetzung, leere Paragraphen, Split/Merge, Emoji/Surrogatpaare/Combining/ZWJ, Wortgrenzen, fehlerhafte UTF-16-Offsets.
- **Akzeptanz:** Reine Scala.js-Test-App kann Text ohne DOM editieren; delete-Commands verletzen keine Graphemgrenze; Operationen/Selection bleiben gemeinsam gültig.
- **Risiken:** Offsetmaß und Benutzerzeichen sind verschieden. Unicode-Datenquelle und Version sind vor Abnahme festzulegen; ein reiner Codepoint-Fallback erfüllt Graphemtests nicht.
- **Dependencies:** P05; Architektur §§8, 11.

## P07 — Echte Browser-Test-App

- **Ziel:** Kleine unabhängige Harness, welche die neue Scala-Engine ausführt und frühe Runtime-Nachweise ermöglicht.
- **Module:** Neues nicht publiziertes IT; bestehendes jfx-core.
- **Neue Dateien:** `ember-integration/src/main/scala-3/ember/editor/integration/EditorTestApp.scala`, `ember-integration/browser/package.json`, `playwright.config.ts`, `fixtures.ts`, `identity.spec.ts`, `README.md`.
- **Ändern:** `build.sbt` für `scalajs-ember-integration`; CI zunächst für vorhandene Harness-Tests; Testskripte/Lockfile des isolierten Browserpakets.
- **API:** Test-only Scala.js-Exports für Mount/Read/Dispatch/Dispose und Testfixtures. Keine produktive Bridge-API einfrieren.
- **Tests:** Chromium/Firefox/WebKit starten, JFX-Komponente aus echtem Linkeroutput mounten, Event auslösen, Dispose prüfen; Server-Import ohne DOM.
- **Akzeptanz:** Dokumentierter Befehl `sbt --server "scalajs-ember-integration/fullLinkJS"`, danach im Browserpaket `npm ci` und `npm run test:browser`; Testlauf gegen tatsächliche Runtime, kein Stub als Abnahme.
- **Risiken:** IT darf privat bleiben, aber kein publiziertes Modul darf davon abhängen. Test-App separat gelinkt ist zulässig, weil sie eine isolierte Anwendung ist.
- **Dependencies:** P06; Architektur §24.

## P08 — JFX Text-Splice und Move physischer Komponenten

> **Im Nachbar-Repo erledigt.** `spliceText` liegt in `../scalajs-jfx/jfx-core/src/main/scala-3/jfx/core/render/TextNode.scala:8`,
> `render/DomTextNode.scala:12` und `layout/TextComponent.scala:20`; `Runtime.move` und
> `Runtime.contentCursor` in `component/Runtime.scala:180` bzw. `:168`. Hier bleibt nur die
> Nutzung in P09 und die IT-Absicherung, sobald P07 den Harness stellt — nicht die Implementierung.

- **Ziel:** Editor braucht eine verlässliche bestehende Runtime, keine eigenen DOM-Writer.
- **Module:** jfx-core (`../scalajs-jfx`); IT.
- **Neue Dateien:** Tests `../scalajs-jfx/jfx-core/src/test/scala-3/jfx/core/render/TextSpliceSpec.scala`, `.../component/RuntimeMoveSpec.scala`; IT `move.spec.ts`, `text-splice.spec.ts`.
- **Ändern:** `../scalajs-jfx/jfx-core/src/main/scala-3/jfx/core/render/{TextNode,DomTextNode,SsrTextNode,SsrNode,SsrHostElement}.scala`, `.../layout/TextComponent.scala`, `.../component/Runtime.scala`; nur tatsächlich benötigte Host-Verträge ergänzen.
- **API:** UTF-16 `spliceText` und No-op-Schutz; `Runtime.move` für physische Hosts mit unveränderten editorweiten Services. Unsupported Virtual-Reparenting explizit ablehnen.
- **Tests:** Identischer Text erzeugt keine Mutation; Splice erhält Textobjekt. Reorder/Cross-parent-Move erhält Host/Listener, stimmt mit logischer Reihenfolge überein; reaktive Folgeänderung im neuen Parent; SSR keine Duplikate; Dispose einmal.
- **Akzeptanz:** Derselbe Testfall stimmt in SSR und Browser überein; Move benötigt keine editorinterne Manipulation von `_children` oder DOM; Zyklusfehler vor Mutation.
- **Risiken:** Gespeicherte Cursor/Async-Mounts und geerbte Kontexte bei Reparenting. Diese Phase beansprucht ausdrücklich keine universelle virtuelle Move-Semantik.
- **Dependencies:** P07; Architektur §15.1.

## P09 — Semantischer Rendervertrag und keyed DocumentView

- **Ziel:** Kleine Dokumente SSR-rendern und durch gezielte Commits ohne Remount unveränderter Nodes aktualisieren.
- **Module:** Neue html (zunächst Semantik-SPI), jfx und standard; jfx-core; IT.
- **Neue Dateien:** `html/HtmlFragment.scala`, `HtmlSemantics.scala`; `jfx/NodeView.scala`, `DocumentView.scala`, `DocumentProjection.scala`, `EditorProperties.scala`; `standard/ParagraphSupport.scala`; ~~`../scalajs-jfx/jfx-core/.../statement/KeyedChildren.scala`~~ (existiert bereits); Tests `ProjectionSpec.scala`, `KeyedChildrenSpec.scala`, IT `projection.spec.ts`.
- **Ändern:** `build.sbt`; Runtime-Move-API aus P08 bei Bedarf; IT-App für DocumentView.
- **API:** Typisierte NodeView-Registrierung, getrennte Content-/Editor-Renderprofile, KeyedChildren mit Datenupdate statt Neubau, `afterProjection(revision)`; ReadOnlyProperty als Adapter.
- **Tests:** Ein Textedit schreibt nur betroffenen Leaf; Child-Move erhält Instanz, Remove disposed; gleiche ID mit Typwechsel ersetzt bewusst; SSR-HTML enthält semantische p/Text/Mark-Basis; zwei Editoren verwechselt keine IDs.
- **Akzeptanz:** Kein zweiter DOM-Renderer/VDOM/Scheduler; Mount/Unmount/Move ausschließlich JFX. Ein 10k-Node-Dokument wird für einen Textedit nicht vollständig traversiert. Gleiches initiales Rendering in SSR und Browser.
- **Risiken:** Eager Sammelregistrierungen halten optionale Module fest. `HtmlFragment` darf keine eigene Update-/Diff-Laufzeit bekommen; rohe benachbarte SSR-Textnodes vermeiden.
- **Dependencies:** P08; Architektur §§5–7, 15 und 19.

## P10 — JSON-Codecs und Schema-Migration

- **Ziel:** Dokumente unabhängig von View und Browser verlustfrei persistieren.
- **Module:** Neues json; core.
- **Neue Dateien:** `json/JsonValue.scala`, `DocumentJson.scala`, `NodeJsonCodec.scala`, `DecodeLimits.scala`, `SchemaMigration.scala`, `CoreJsonSupport.scala`; Tests `DocumentJsonSpec.scala`, `SchemaMigrationSpec.scala` mit lokalen typisierten Testnodes.
- **Ändern:** `build.sbt`; P02-Builder für validierte Decode-Ergebnisse, falls nötig.
- **API:** Versioniertes Envelope, typisierte Codecs, Strict-/Preservation-Policy, DecodeResult mit Pfad-/Typdiagnosen. Default ohne Selection/History/DOM.
- **Tests:** Roundtrip mit IDs, unbekannte Version/Typ, doppelte Keys/IDs, invalides JSON, Limits, sichere Unknown-Node-Erhaltung, alte→neue Schemaversion; Script-Endmarker im Payload für spätere SSR-Verwendung.
- **Akzeptanz:** Ungültiger Payload erzeugt keinen teilweise gültigen Editor. Unbekannte Daten werden nur mit expliziter Policy erhalten; keine automatische Klassendeserialisierung.
- **Risiken:** JSON-Parser kann doppelte Objektkeys bereits zusammenfassen; die erlaubte Policy muss dokumentiert/testbar sein, während doppelte Node-IDs immer Fehler bleiben.
- **Dependencies:** P05; Architektur §19.2. Paragraph-/Feature-Codecs im Integrationsmodul folgen in P16/P18; P10 benötigt dessen Rendererstrang nicht.

## P11 — History

- **Ziel:** Deterministisches Undo/Redo ohne DOM und ohne Native-History-Abhängigkeit.
- **Module:** Neues history; rich-text; IT optional.
- **Neue Dateien:** `history/History.scala`, `HistoryState.scala`, `HistoryCommands.scala`, `HistoryGrouping.scala`, `HistoryLimits.scala`; Tests `HistorySpec.scala`, `HistoryRetentionSpec.scala`.
- **Ändern:** `build.sbt`; Tx-Metadaten falls ein erforderlicher Origin/Policy-Vertrag fehlt.
- **API:** `History(config, clock)`, Undo/Redo/CanUndo/CanRedo, Push/Merge/Ignore, Reset; gespeicherte Selection vor/nach jeder Gruppe.
- **Tests:** Fake Clock, Typing/Deletion getrennt, Caretsprung, Paste-/Format-Grenze, Redo nach Undo, Selection-only, Snapshot-Trim, Import/History-Origin; simulierte CompositionSession als eine Gruppe.
- **Akzeptanz:** Undo stellt Document und Selection atomar wieder her; Revision bleibt monoton; History enthält weder sich selbst noch View-/Effect-State.
- **Risiken:** Retained-Byte-Schätzung bei Structural Sharing nicht als exakte Heapgröße darstellen; Remote-Edits erfordern später einen anderen Undo-Vertrag.
- **Dependencies:** P06; Architektur §14.

## P12 — Rich-Text-Marks und Blocksemantik

- **Ziel:** Bereichsformatierung und grundlegende Blocktypen auf den primitiven Operationen aufbauen.
- **Module:** rich-text; standard; IT.
- **Neue Dateien:** `rich-text/HeadingNode.scala`, `QuoteNode.scala`, `BreakNode.scala`, `StandardMarks.scala`, `TypingMarks.scala`, `RichTextCommands.scala`, `RangeFormatting.scala`, `TextRunNormalization.scala`; zugehörige `standard/*Support.scala`; Tests `RangeFormattingSpec.scala`, `RichTextStructureSpec.scala`, `TypingMarksSpec.scala`, `TextRunNormalizationSpec.scala`.
- **Ändern:** `RichText.scala`, Textnormalisierung und Renderer-Support.
- **API:** Strong/Emphasis/Underline/Strike/InlineCode, ToggleMark, SetHeading, Quote/Unquote, Hard-/Softbreak, ThematicBreak; typisierte Level und Markkonflikte; transaktionales `TypingMarks(Inherit|Explicit)` mit Caret-Mapping und History-Restore-Policy.
- **Tests:** Format über mehrere Leaves/Blöcke, teilweise ausgewählte Läufe, Toggle am leeren Caret beeinflusst nächste Eingabe, Caretsprung setzt Inherit, Mapping erhält explizite Marks, Undo/Redo restauriert sie; Backward Selection, Split/Merge-Normalisierung, History-Grenzen, semantischer Export.
- **Konkreter Normalisierungstest:** Aus einem TextNode `"Hallo Welt!"` entstehen beim Fettformatieren von `"Welt"` drei TextNodes; Entfernen von Strong erzeugt im selben Commit wieder genau einen TextNode mit identischem Gesamttext und erhaltener linker ID. Caret/Backward Range/Bookmarks werden korrekt gemappt, Undo stellt den formatierten Zustand wieder her, Redo den zusammengeführten. Wiederholte Zyklen fragmentieren nicht; erneute Normalisierung ist No-op. Gegenfälle: unterschiedliche Marks/Metadaten, verschiedene Parents/Links sowie Breaks/Atoms werden nicht zusammengeführt. P23 ergänzt Aufschub während Composition und Merge nach Abschluss.
- **Akzeptanz:** Formatierung wird durch Nodes/Marks bestimmt; kein Browser-execCommand; Selection bleibt nach Node-Split/Mark-Änderung korrekt. Nach abgeschlossener Normalisierung existieren keine direkt benachbarten, semantisch identischen und zusammenführbaren Textläufe desselben Parents; Entformatieren erzeugt keinen eigenen History-Schritt für den Merge.
- **Risiken:** Zu aggressive Text-Merges verlieren Selection/Composition-Identität. Stored-Marks benötigen einen expliziten Selection-/StateField-Vertrag.
- **Dependencies:** P06, P09, P11; Architektur §§8, 11, 15.

## P13 — Listen

- **Ziel:** Strukturell korrekte Listen mit vorhersehbarer Editing-Semantik.
- **Module:** Neues list; standard; IT.
- **Neue Dateien:** `list/ListNode.scala`, `ListItemNode.scala`, `Lists.scala`, `ListCommands.scala`, `ListNormalization.scala`; `standard/ListSupport.scala`; Tests `ListEditingSpec.scala`, `ListNormalizationSpec.scala`.
- **Ändern:** `build.sbt`; Standard-Registrierung nur über explizite Fabriken.
- **API:** Ordered/Unordered mit Startwert/Tightness, Wrap/Unwrap, Indent/Outdent, Enter in leeren/gefüllten Items; ListItems enthalten Blockkinder.
- **Tests:** Verschachtelte Listen, mehrere Paragraphen im Item, Bereich über mehrere Items, Backspace an Anfang, Split/Join, ChangeSet/Selection-Mapping, SSR-Move-Identität.
- **Akzeptanz:** Kein nackter Paragraph direkt in ListNode; Normalisierung terminiert; Undo erhält die Ausgangsliste samt Auswahl.
- **Risiken:** Reparenting kann mehrmals dieselbe Grenze verschieben. Tab-Einrückung bleibt eine spätere opt-in Browserentscheidung.
- **Dependencies:** P12; Architektur §§8, 11, 18.

## P14 — Links

- **Ziel:** Typisierte Inline-Links einschließlich Bereichsoperationen und URL-Regeln.
- **Module:** Neues link; standard.
- **Neue Dateien:** `link/LinkNode.scala`, `Links.scala`, `LinkCommands.scala`, `LinkUrlPolicy.scala`; `standard/LinkSupport.scala`; Tests `LinkSpec.scala`, `LinkUrlPolicySpec.scala`.
- **Ändern:** `build.sbt`; keine Core-Mark-Erweiterung für Links.
- **API:** SetLink/RemoveLink, validierte URL/Title, keine verschachtelten Links, Profilregeln für http(s)/relative/mailto/tel.
- **Tests:** Teilbereich/Backward Range, Unlink erhält Marks/Text, unsafe/obfuskierte URLs, Link über lokalen Test-Inline-Atomtyp, semantisches Anchor-Rendering. Der konkrete Image-Link-Test folgt nach P16 in P18/P25.
- **Akzeptanz:** URL-Validierung identisch bei Command/Import; Linkdialog nicht nötig, headless nutzbar; External-Link-Attribute bewusst gesetzt.
- **Risiken:** Links und Media haben unterschiedliche Policies; Stringpräfix-Tests allein reichen für normalisierte URLs nicht.
- **Dependencies:** P12; Architektur §§8, 19–20.

## P15 — Code

- **Ziel:** Code als semantisches Dokumentfeature, unabhängig von Highlighting.
- **Module:** Neues code; standard.
- **Neue Dateien:** `code/CodeBlockNode.scala`, `Code.scala`, `CodeEditing.scala`; `standard/CodeSupport.scala`; Tests `CodeSpec.scala`.
- **Ändern:** `build.sbt`; Rich-Text-Kontextregeln für Text mit Zeilenumbrüchen.
- **API:** CodeBlock mit Sprach-/Info-Metadaten und Textinhalt; Umwandeln in/aus Paragraphen; optional explizite Indent-Commands.
- **Tests:** Leere/mehrzeilige Blöcke, führende/abschließende Leerzeilen, Enter/Exit, keine unerlaubten Rich-Kinder, pre/code-SSR, Undo.
- **Akzeptanz:** Kein Syntax-Highlighter und kein CodeMirror als Produktionsabhängigkeit; verlustfreier Codeinhalt als Grundlage für Markdown-Fences.
- **Risiken:** Sichtbares Highlighting darf später keine persistente Mark-Zerlegung jeder Codezeile erzwingen.
- **Dependencies:** P12; Architektur §§8, 18.

## P16 — Externe Bilder und Media-Modell

- **Ziel:** Referenzbasierte Medien ohne Browser/File/Upload im Modell.
- **Module:** Neues image; standard; json.
- **Neue Dateien:** `image/MediaReference.scala`, `ImageNode.scala`, `MediaUrlPolicy.scala`, `Images.scala`; `standard/ImageSupport.scala`, `ImageJsonSupport.scala`; Tests `ImageNodeSpec.scala`, `MediaUrlPolicySpec.scala`.
- **Ändern:** `build.sbt`; JSON-/View-Registries nur durch auswählbare Adapter ergänzen.
- **API:** Inline-Atom mit src/alt/title/width/height/optional mediaId, validierte positive Pixelmaße, HTTPS/relative Pfade als Default; HTTP explizit konfigurierbar.
- **Tests:** Externe und interne Quelle in JSON/SSR, ungültige Maße, data/blob/javascript/Protokoll-relative URLs, Unicode/Entities-Normalisierung, sichere Attribute, leerer Alt-Text.
- **Akzeptanz:** Keine Dateidaten oder Object-URL im Document, keine Netzwerkzugriffe beim Rendern/Decodieren, Custom Media-Typ kann dieselbe Atom-/View-SPI nutzen.
- **Risiken:** Markdown stellt zusätzliche Media-Metadaten nicht standardmäßig dar; Verlustdiagnosen in P18 erforderlich.
- **Dependencies:** P05, P09, P10; Architektur §20.

## P17 — Markdown: Blockparser und SourceMap-Grundlage

- **Ziel:** Eigenständiger Scala-Parser mit explizitem Profil, keine HTML-Konvertierung als Umweg.
- **Module:** Neues markdown.
- **Neue Dateien:** `markdown/MarkdownSyntax.scala`, `MarkdownProfile.scala`, `BlockParser.scala`, `SourceMap.scala`, `ParseLimits.scala`; Tests `MarkdownBlockSpec.scala`, versionierte Fixtures `src/test/resources/markdown/`.
- **Ändern:** `build.sbt`; keine Browserabhängigkeit hinzufügen.
- **API:** `parseSyntax(source, profile): ParseResult`, Syntax-Blocks mit UTF-16-Quellspannen; Anfangsprofil wird ausdrücklich als Blockparser ohne vollständige Inline-Konformität gekennzeichnet.
- **Tests:** Headings/Paragraph/Quotes, enge/weite/verschachtelte Listen, ATX/Setext, Code/Fences/Info/Leerzeilen, Thematic Break, Einrückung, CRLF, Limits und tiefe Eingaben.
- **Akzeptanz:** Deterministisches Ergebnis ohne DOM; Korpus-/Spezifikationsversion und Ressourcenlimits dokumentiert; keine unbeschränkte Rekursion oder katastrophale Regex-Laufzeit.
- **Risiken:** Container-/Lazy-Continuation-Regeln sind echte Parserarbeit. Keine behauptete vollständige CommonMark-Konformität aus einfachen Happy-Path-Tests.
- **Dependencies:** P06; Architektur §18.

## P18 — Markdown: Inlines, Writer und Document-Adapter

- **Ziel:** Verbindliche Markdown-Teilmenge direkt zwischen Syntax und Editor-Document austauschen.
- **Module:** markdown; standard; Node-Feature-Module.
- **Neue Dateien:** `markdown/InlineParser.scala`, `DelimiterStack.scala`, `MarkdownWriter.scala`, `MarkdownRule.scala`, `MarkdownCodec.scala`; `standard/MarkdownSupport.scala` plus getrennte Feature-Regeln und `StandardJsonSupport.scala` mit getrennten Built-in-Codecs; Tests `MarkdownInlineSpec.scala`, `MarkdownRoundTripSpec.scala`, `MarkdownSourceMapSpec.scala`, `MarkdownConformanceSpec.scala`, `StandardJsonRoundTripSpec.scala`.
- **Ändern:** Blockparser für Inline-Anbindung; Standard-Dependencies/Writer-Fabriken.
- **API:** decode/encode mit Strict/AllowLossy, Diagnosen, CommonMarkSafe-Profil, Source→Document-Mapping; alle Features aus Architektur §18.2.
- **Tests:** Escapes/Entities, Emphasis-Nesting, Links/Referenzen/Autolinks, Bilder, variable Backticks/Fences, Soft-/Hardbreaks, Alt/Title, semantische Roundtrips; unsupported Underline/Maße/Custom Nodes liefern Verlustdiagnose.
- **Akzeptanz:** Kein HTML-/DOM-Zwischenschritt; Code und unterstützte Semantik erhalten; source-identischer Export wird nicht versprochen. Vollständigkeitsstatus anhand des versionierten Korpus ausgeben.
- **Risiken:** Parser/Writer-Mehrdeutigkeit und langsame Delimiterfälle; vollständiger Text-Neuimport ist nicht dasselbe wie inkrementelles Rich-Editing.
- **Dependencies:** P13, P14, P15, P16, P17, P10; Architektur §18.

## P19a — Generischer JFX-Textarea-Vertrag

> **Im Nachbar-Repo erledigt.** `../scalajs-jfx/jfx-core/src/main/scala-3/jfx/core/layout/TextArea.scala`
> existiert mit `value`/`defaultValue`/`valueProperty`/`setValue`/`reset`/`readNativeValue`;
> Vertrag und Grenzen in [JFX_CORE_INTEGRATION.md](JFX_CORE_INTEGRATION.md), Abschnitt
> „Native Textarea und SSR“. Hier ist nur noch P19b zu bauen.

- **Ziel:** HTML-/Form-Basis korrekt lösen, bevor der Editor darauf aufbaut.
- **Module:** jfx-core (`../scalajs-jfx`); optional schlanker jfx-forms-Adapter; IT.
- **Neue Dateien:** `../scalajs-jfx/jfx-core/src/main/scala-3/jfx/core/layout/TextArea.scala`, `.../render/TextAreaContent.scala`; Tests `.../render/TextAreaSsrSpec.scala`; IT `textarea.spec.ts`.
- **Ändern:** Host-/Cursor-/Hydration-Verträge nur soweit RCDATA und Wertübernahme dies erfordern; nicht `SsrRawTextNode` zu einem ungesicherten Editorweg umdeuten.
- **API:** Textarea-Default/Baseline getrennt von aktuellem value, Source-Text sicher rendern, Pre-claim-Erfassung und No-rewrite-Hydration-Policy; native reset-Semantik.
- **Tests:** `""`, `"\nabc"`, CRLF, `"&</textarea>"`; echter HTML-Parser liefert korrekten Wert, kein Literal-`jfx:text`; vor Hydration geänderter value und Backward Selection; Reset auf Baseline.
- **Akzeptanz:** Leerer Inhalt erzeugt keine Text-Kommentaranker; führende LF geht nicht verloren; sicherer und normal submitbarer SSR-Wert. Die Komponente ist generisch, ohne Editorimporte.
- **Risiken:** Textarea ist RCDATA, nicht gewöhnlicher Elementtext; `textContent`, `defaultValue` und `.value` haben unterschiedliche Aufgaben.
- **Dependencies:** P07, P09; Architektur §§4, 16–17.

## P19b — Source-Feld, Draft und No-JS-Formular

- **Ziel:** Neues Editorfeld funktioniert ohne JavaScript und schützt unbestätigten Source-Text.
- **Module:** Neues forms; jfx; markdown/json; standard; IT.
- **Neue Dateien:** `forms/EditorField.scala`, `FieldCodec.scala`, `EncodedFieldValue.scala`, `SourceDraft.scala`, `EditorFormBinding.scala`, `SubmitPolicy.scala`; IT `source-form.spec.ts`, `nojs-form.spec.ts`, minimaler Testserver `browser/server.ts`.
- **Ändern:** `build.sbt`; IT-App/Server für native POST-/Validation-/Reset-Routen; diese Routen gehören nur zur Testanwendung.
- **API:** `MarkdownField(profile)` / `JsonDocumentField(schema)`, genau eine benannte Textarea, readonly Preview, Source-Draft mit Baseline-Revision; `SourceBusy` bzw. Intent-Queue während Source-Bearbeitung. Synchrone Darstellbarkeitsregel/ablehnender `EncodedFieldValue`-Reducer vor Commit; nach Commit nur den bereits geprüften Wert dieser Revision projizieren.
- **Tests:** JS aus: Lesen/Editieren/Submit/Validation/Reset; ein FormData-Feld, Unicode/Escaping; Source-Decodefehler erhält Text; externe Update-/Upload-Intents überschreiben Dirty Draft nicht. Programmatisches ToggleUnderline/Custom-Node-Insert in Strict-Markdown scheitert vor Commit und lässt Document/Formwert/History unverändert; nicht nur UI deaktivieren.
- **Akzeptanz:** Formwerte jederzeit eindeutig; Rich-Modus synchronisiert Commit→Formwert, Source-Modus schützt Draft; Wechsel/Submit importiert atomar. Vollstring-Materialisierung wird separat gemessen, nicht als lokaler O(1)-Edit ausgegeben.
- **Risiken:** Textarea und Rich-Dokument dürfen keine gleichzeitig konkurrierenden Wahrheiten werden. Servervalidierung und HTTP-Persistenz sind Anwendungsverantwortung.
- **Dependencies:** P10, P18, P19a; Architektur §16.

## P20 — Isolierte Hydration mit Verlustschutz

- **Ziel:** Rich-Subtree übernehmen oder lokal ersetzen, ohne Fallback/Nutzereingabe zu zerstören.
- **Module:** jfx-core; jfx; forms; neues browser mit Hydration-Aktivierung; IT.
- **Neue Dateien:** ~~`../scalajs-jfx/jfx-core/.../render/HydrationBoundary.scala`~~ (existiert bereits, tatsaechlich unter `component/HydrationBoundary.scala`); `browser/EditorHydration.scala`, `HydrationSnapshot.scala`; Tests `HydrationBoundarySpec.scala`; IT `editor-hydration.spec.ts`.
- **Ändern:** `HydratingCursor.scala`, `Runtime.scala` und ggf. `Cursor.scala` für scoped Claim/Preflight/Callback-Cleanup; Formkomposition aus P19b. Fallback außerhalb der fehlschlagenden Rich-Boundary halten.
- **API:** Capture vor Bindung, validierter Payload/Profile/ID-/Semantikabgleich, lokal abgeschlossener Claim plus äußeres afterHydration; Aktivierungsstatus/Fehler. Fokussierte Source mit unbekannter vorangegangener Composition erst nach Blur/Wechselaktion übernehmen.
- **Tests:** Mismatch in Tag/Text/Attribut/ID, fehlender/alter Payload, partial mount cleanup, keine zurückbleibenden Session-Cursor/Callbacks, Nutzertext vor/nach Preflight, SelectionDirection, doppelte Aktivierung und Fokus.
- **Akzeptanz:** Fallback-Wert/Selection/Ownership überlebt jeden lokalen Fehler; ein gültiger Rich-Subtree behält Host-Identität; Editierbarkeit erst nach erfolgreichem Abschluss. Kein ungeprüftes adoptRange.
- **Risiken:** Mount-Rollback kann bereits geclaimte Hosts entfernen. Composition lässt sich beim späten Attach nicht zuverlässig rückwirkend feststellen.
- **Dependencies:** P07, P09, P19a, P19b; Architektur §17.

## P21 — DOM-Selection und Fokus

- **Ziel:** Logische und Browserauswahl zuverlässig in beide Richtungen abbilden.
- **Module:** browser; jfx; IT.
- **Neue Dateien:** `browser/SelectionPort.scala`, `DomPositionMap.scala`, `FocusController.scala`, `BrowserScope.scala`; IT `selection.spec.ts`, `focus.spec.ts`.
- **Ändern:** NodeView-Hostregistrierung/Projection-Abschluss; Browser-Attach-Lifecycle.
- **API:** Read/write Range und NodeSelection, ownerDocument-scoped Events, Restore-Bookmark, nur nach passender Projection-Revision schreiben; native Control-/Nested-Editor-Ownership beachten.
- **Tests:** Forward/Backward, leere Paragraphen, Wrapper/Marks/Breaks, Text-/Elementoffsets, Inline-Atom, viele Leaves, Browser-Pfeile/Bidi, Selection außerhalb, Toolbar-Fokus, zwei Editoren, iframe; Shadow-DOM-Capability gesondert dokumentieren.
- **Akzeptanz:** `read(write(selection))` ist für unterstützte Punkte semantisch äquivalent; keine Selectionchange-Schleife, kein Fokusstehlen bei Hintergrundupdate. Native Inputs innerhalb Atom-Views werden nicht als Editortext behandelt.
- **Risiken:** DOM-Kindoffsets enthalten Renderhilfen; ungeprüfte globale Selection oder innerHTML-Positionen verlieren Modellbezug.
- **Dependencies:** P09, P20; Architektur §§11, 15, 22.

## P22 — Normale Eingabe, Keyboard und NativeInput

- **Ziel:** Ein neuer Rich-Editor für normale Browserbearbeitung; noch keine behauptete vollständige IME-Freigabe.
- **Module:** browser; neues browser-support für konkrete rich-text/list/link/code/history-Verdrahtung; IT.
- **Neue Dateien:** `browser/BrowserInputController.scala`, `InputIntent.scala`, `BeforeInputAdapter.scala`, `NativeInputReader.scala`, `KeyboardBindings.scala`, `InputOperationToken.scala`; `browser-support/HistoryBindings.scala`, `ListBindings.scala`, `LinkBindings.scala`, `CodeBindings.scala`; IT `editing.spec.ts`, `native-input.spec.ts`.
- **Ändern:** `build.sbt` für browser-support; Hydration-Aktivierung verbindet Controller; NodeView-Editorprofil ergänzt nur notwendige editing-Attribute. Browser-Modul erhält keine Feature-/Forms-Rückimporte.
- **API:** Zustandsmaschine Ready/Recovering, cancelable beforeinput→Command; nicht cancelable input→validierte Tx; Shortcut-Registry; Browser-Undo/Redo→eigene Commands.
- **Tests:** insert/delete/Enter/Shift+Enter, Range-Replace, Block-/Listgrenzen, Autokorrektur-Replacement, Drop-/Paste-Token zunächst mit Testport, Input ohne keydown, readonly, native Controls in Atoms, undo bei null/NodeSelection.
- **Akzeptanz:** Jede Eingabe genau einmal; keine direkte Feature-DOM-Manipulation oder execCommand; unbehandelte Navigation bleibt nativ; gesicherter Text bei nicht importierbarer Native-Struktur.
- **Risiken:** Ein beforeinput-Featuretest garantiert nicht alle Inputtypen. Event-Ownership und erfolgreiche Modellübernahme **oder bewusste Ablehnung** bestimmen preventDefault, nicht die bloße Existenz eines Handlers. Readonly-/Limit-/Schema-Reject verhindert native Ersatzmutation.
- **Dependencies:** P11, P12, P13, P14, P15, P21; Architektur §15.

## P23 — Composition, Observer-Abgleich und Recovery

- **Ziel:** IME und Browsermutationen als ausdrücklich getesteter Inputvertrag.
- **Module:** browser; jfx; jfx-core; history; forms; IT.
- **Neue Dateien:** `browser/CompositionSession.scala`, `NativeMutationObserver.scala`, `ProjectionWriteGuard.scala`, `RecoveryController.scala`, `DeferredIntentQueue.scala`; ~~`../scalajs-jfx/jfx-core/.../render/HostMutationGuard.scala`~~ (existiert bereits); IT `composition.spec.ts`, `mutation-race.spec.ts`, `composition-form.spec.ts`, `manual-ime.md`, versionierte Event-Traces.
- **Ändern:** Controller aus P22; Tx-Gate für CompositionBusy, History-Gruppenmetadaten, Submit-/Source-Status und Projection-Schutz. JFX-Text-/Child-/Move-/Mount-/Unmount-Pfade prüfen den opt-in HostMutationGuard vor logischer/physischer Mutation; blockierte Writes melden ohne Seiteneffekt, kein eigener Scheduler. Nach Release projiziert der Editor den aktuellen Snapshot.
- **API:** Gesamter anfänglicher Ersetzungsbereich einschließlich aller betroffenen Leaves/Marks/Atoms/Blöcke geschützt; ggf. ganzer Host. CompositionSession-ID, kontrollierte native Zwischencommits, **alle unabhängigen Dokument-Intents während Composition zurückstellen/abweisen**, abschließende Normalisierung. Observer-Records vor/nach eigener Projektion revisioniert abgleichen.
- **Tests:** compositionend plus letztes input ohne Doppeltext, Cancel/Blur/Dispose, keine Writes im geschützten Bereich, native und eigene Mutation in derselben Zustellung, unerlaubte Strukturänderung, begrenzte Recovery. Zusätzlich Traces für IME ohne Composition-Events, natives Delete trotz preventDefault, mehrere beforeinput vor input und verwaiste Composition-Inputs. Zwischenzeitlicher externer Intent darf durch Composition-Undo nicht verschwinden.
- **Weitere Abnahmefälle:** Composition-Replacement über Marks/mehrere Leaves/Atomgrenzen/mehrere Blöcke; fremde Property- und Move-/Remount-Versuche werden vor Mutation blockiert. Submit/requestSubmit erhält finalen nativen Text genau einmal oder wird verständlich blockiert; Reset und readonly-Umschaltung folgen expliziter Abschluss-/Verwerfungsregel ohne stillen Datenverlust.
- **Akzeptanz:** Eine Composition ergibt eine History-Gruppe; keine Integritätsvalidierung ausgeschaltet; kein Boolean-suppress als alleiniger Observer-Schutz; Source bleibt bei Recovery nutzbar. Reale IME-Abnahme erst mit dokumentiertem Geräteergebnis.
- **Risiken:** Browser-/OS-Ereignisreihenfolgen und Retargeting. Unabhängige Commits während Composition benötigen selektive History und sind im MVP bewusst nicht erlaubt.
- **Dependencies:** P22; Architektur §§14–17.

## P24 — Sicherer HTML-Fragmentimport

- **Ziel:** HTML unabhängig von einem Browser-DOM kontrolliert in das Modell importieren.
- **Module:** html; standard; IT für reale Clipboard-Fixtures.
- **Neue Dateien:** `html/HtmlFragmentParser.scala`, `HtmlTokenizer.scala`, `HtmlImportRule.scala`, `HtmlImportPolicy.scala`, `HtmlImport.scala`; getrennte `standard/*HtmlSupport.scala`; Tests `HtmlParserSpec.scala`, `HtmlImportSpec.scala`, `HtmlSecuritySpec.scala` und versionierte Fremdformat-Fixtures.
- **Ändern:** Semantik-SPI aus P09 bei belegtem Bedarf; Fehler-/Limitdaten mit JSON/Markdown-Konvention harmonisieren, ohne zirkuläre Modulabhängigkeit.
- **API:** parse/import mit dokumentiertem sicheren Profil, Kindverarbeitung, Rule-Reihenfolge, unknown-wrapper Policy und Diagnosen. Kein Anspruch vollständiger HTML5-Tree-Construction.
- **Tests:** Browser/Word-Fragmente, malformed nesting, Entities/Whitespace, p/div/br, Listen, pre/code, Links/Bilder; script/style/events/unsafe URLs/CSS; server-/browsergleiche Ergebnisse und Textfallback.
- **Akzeptanz:** Keine Einfügung rohen HTMLs in den lebenden DOM; unterstützte Semantik bleibt erhalten, Verlust wird diagnostiziert. Nicht unterstützte reale Fragmente bestimmen vor Freigabe Parser-Erweiterung oder explizites Profil-Limit.
- **Risiken:** Security und Fehlformungs-Recovery sind keine kleine Regex-Aufgabe. Ein Scala.js-kompatibler Parser ist bei Bedarf gezielt zu evaluieren; keine versteckte neue JS-Engine-Dependency.
- **Dependencies:** P09, P13, P14, P15, P16; Architektur §19.1.

## P25 — Clipboard und strukturierter Drag/Drop

- **Ziel:** Strukturelle Fragmente sicher austauschen und genau einmal einfügen/löschen.
- **Module:** Neues clipboard; browser; json/html/rich-text; standard.
- **Neue Dateien:** `clipboard/DocumentFragment.scala`, `ClipboardPort.scala`, `ClipboardCodec.scala`, `ClipboardCommands.scala`, `BrowserClipboardPort.scala`, `DropController.scala`; Tests `FragmentSpec.scala`, `ClipboardSpec.scala`; IT `clipboard.spec.ts`, `drop.spec.ts`.
- **Ändern:** `build.sbt`; Input-Token-/NativeInput-Koordination; Browser-Harness für echte Clipboard-Permissions soweit erforderlich.
- **API:** Internes MIME→HTML→Text, validierte Profile/Versionen, remappte IDs, offene Fragmentgrenzen; Cut erst nach erfolgreichem Write und neuer Bookmarkprüfung.
- **Tests:** Teiltext/Marks, mehrere Blöcke, rückwärtige Auswahl, Atom/Listen, ungültiges internes Format mit erlaubtem Fallback, fehlende MIME-Typen, Cutfehler, async Schreibkonflikt, Doppel-Paste, Move im selben Dokument versus fremde Kopie.
- **Akzeptanz:** Daten und Selection bleiben strukturell gültig; eigene History-Grenzen; Native-Control-Clipboard wird nicht gestohlen. Dateien werden als Media-Intent weitergereicht, nicht in Nodes eingebettet.
- **Risiken:** Clipboard-Inhalte sind fremde Eingabe; Browser erlauben nicht jede API identisch. Eventadapter zuerst, Async-API nur mit getestetem Fehlerpfad.
- **Dependencies:** P10, P23, P24; Architektur §21.

## P26 — Medienservice, Upload-Lifecycle und Multipart

- **Ziel:** Derselbe Referenzvertrag für Picker/Paste/Drop und No-JS-Upload, ohne Storage im Core.
- **Module:** image (Modell unverändert halten), forms/browser/clipboard; IT.
- **Neue Dateien:** `forms/MediaService.scala`, `MediaCoordinator.scala`, `MediaStatus.scala`, `BrowserMediaPicker.scala`; IT `media.spec.ts`, `multipart.spec.ts`, dokumentierter Testserver-Servicevertrag.
- **Ändern:** Form-Adapter konsumiert injizierte Browser-/Clipboard-File-Intents; keine Rückabhängigkeit browser/clipboard auf forms. Testserver für dauerhaft referenzierte Fixture-Datei, Validation und Rückgabe des Source-Drafts.
- **API:** `upload(file, cancellation): Future[MediaReference]`, Progress/Fehler außerhalb Document, Live-Bookmark und Dokumentgeneration; Einfügen nur nach erfolgreicher dauerhafter Referenzvalidierung.
- **Tests:** Abbruch, parallele Uploads, Duplicate Completion, Ziel gelöscht, Dokument ersetzt, Undo vor Completion, Dispose, Object-URL-Freigabe, externe URL ohne Upload; JS-aus multipart und Validierungsfehler erhält Source.
- **Akzeptanz:** Keine Base64/Blob/File-Daten im JSON/Markdown; Uploadfehler löscht keinen Text; Undo löscht keine gespeicherte Datei; SourceBusy/CompositionBusy führt zu erneuter Intentvalidierung.
- **Risiken:** Backendvalidierung, CSRF, Storage und Orphan-Cleanup gehören der Anwendung. Der Testserver ist kein neu einzuführendes produktives Uploadsystem.
- **Dependencies:** P16, P19, P23, P25; Architektur §20.

## P27 — Optionale Toolbar und Dialoge

- **Ziel:** Professionell bedienbare UI als austauschbarer Konsument der Editor-API.
- **Module:** Neues ui; jfx-controls/jfx-viewport; IT.
- **Neue Dateien:** `ui/EditorToolbar.scala`, `CommandButton.scala`, `EditorDialogService.scala`, `LinkDialog.scala`, `ImageDialog.scala`; Tests `ToolbarStateSpec.scala`; IT `toolbar-a11y.spec.ts`.
- **Ändern:** `build.sbt`; eigenständige neue Demoansicht, nicht den Prototyp intern erweitern.
- **API:** Buttons dispatchen typisierte Commands, lesen Selection/Stored-Marks/CanUndo; Dialog-Service und gemappte Restore-Bookmarks; Toolbars frei komponierbar. File-Picking/Upload wird als Callback vom Forms-/Anwendungsadapter eingespeist; UI importiert dafür keinen Forms-Service.
- **Tests:** Tastaturführung, aria-pressed/disabled/name, Fokus vor/nach Dialog, verlorenes/abgelaufenes Bookmark, leere Alt-Eingabe, readonly, High Contrast/reduced motion.
- **Akzeptanz:** Editor funktioniert ohne ui/controls/viewport; Dialoge schreiben keine Document-DOM-Nodes; Statusmeldung verständlich und nicht nur visuell.
- **Risiken:** Toolbar-Mousedown und Tastaturaktivierung benötigen unterschiedliche Fokusbehandlung. Kein pauschales preventDefault auf allen UI-Ereignissen.
- **Dependencies:** P11, P14, P21, P26; Architektur §22.

## P28 — Produktreife: Geräte, Korpora, Performance und Packaging

- **Ziel:** Belegte Freigabegrenze statt bloß wachsender Featureliste.
- **Module:** Alle neuen Module; IT; Build/CI.
- **Neue Dateien:** `ember-integration/browser/accessibility-checklist.md`, `support-matrix.md`, `benchmarks/EditorBench.scala`, `benchmarks/corpora/`, `benchmarks/report.md`; `tools/verify-editor-boundaries.mjs`, `tools/measure-editor-bundles.mjs`.
- **Ändern:** `.github/workflows/verify.yml` für echte Browser-/No-JS-Gates; Test-App um Text-only/Markdown/Standard-Profile; Module-READMEs mit tatsächlichem Support.
- **API:** Keine neuen Features. Fehlerdiagnosen, Supportstatus und konfigurierte Grenzen vervollständigen.
- **Tests:** Architektur §24 vollständig: Korpus-/Roundtrip-/Importlimits, 1k/10k/100k Nodes, sehr langer Leaf, Move, History-Trim; Text-only-Bundle ohne optionale Registrierungen; NVDA/VoiceOver, Desktop-/Mobil-IME, Autokorrektur/Spracherkennung dokumentieren.
- **Akzeptanz:** Keine Vollbaumtraversierung oder Geschwister-Remounts für lokale Core-/Projection-Edits; Formstring-Kosten separat ausgewiesen. p50/p95/Heap/Bundlegrößen mit reproduzierbarer Umgebung; keine unbestätigte Browser-/IME-Freigabe. Abhängigkeitsgraph ohne Zyklus/UI-Leak.
- **Risiken:** Manueller Gerätezugang ist ein echter externer Abnahmebedarf. Offene Ergebnisse bleiben offen und sperren die entsprechende Supportbehauptung/Ablösung; keine synthetischen Tests als Ersatz deklarieren.
- **Dependencies:** P18–P27; Architektur §24.

## P29 — TypeScript-Fassade und eine Scala.js-Runtime

- **Ziel:** Thin Facade über die neue native Engine in der vorhandenen gemeinsamen JFX-Bridge.
- **Module:** `../scalajs-jfx`: jfx-bridge, npm/jfx-editor, npm/jfx-demo; native Editor-Module.
- **Neue Dateien:** `../scalajs-jfx/jfx-bridge/src/main/scala-3/jfx/bridge/EditorSessionHandleBridge.scala`, `EditorCommandHandleBridge.scala`, `EditorExtensionHandleBridge.scala`, `EditorDocumentCodecBridge.scala`; `../scalajs-jfx/npm/jfx-editor/src/session.ts`, `commands.ts`, `extensions.ts`; neue Bridge-/Consumer-Tests.
- **Ändern:** `build.sbt`, `BridgeRuntime.scala`, neue bzw. umgestellte Editor-Factory, `../scalajs-jfx/npm/jfx-editor/src/index.ts`, Package-Exports und Tests; Demo zunächst mit eigener nativer Seite. Spätere Linkeraufteilung nur innerhalb gemeinsamer Linkerausgabe.
- **API:** Opaque Handles, typed Payloads, validierte DTOs, create/dispatch/subscribe/dispose; Ext-Fabriken statt Plugin-Stringliste. Kein Scala-Objektgraph oder Promise in synchronem Tx-Draft.
- **Tests:** Falsche Payloads compile/runtime, fremde Runtime-Handles, Dispose, SSR/Hydration/Source, Tarball-Consumer, Client-/SSR-Build und Eine-Runtime-Nachweis; tatsächliche Bundlegrößen der npm-Einstiege.
- **Akzeptanz:** `npm run verify` der betroffenen Pakete sowie globale Gates grün; kein separat gelinkter Editor mit zweiter JFX-Kopie. Eager Bridge-Exporte dürfen minimale Bundlebehauptungen nicht widerlegen.
- **Risiken:** Scala.js-Linking und npm-Tree-Shaking haben unterschiedliche Grenzen; Umstellung der Bridge kann bestehende Konsumenten betreffen. Dokumentierte API-Änderung bewusst testen.
- **Dependencies:** P28; Architektur §23.

## P30 — Bewusste Ablösung und Lexical entfernen

- **Ziel:** Produktive Anwendungen verwenden den neuen Editor, Lexical ist keine Produktionsabhängigkeit mehr.
- **Module:** Alter Editor nur als zu entfernender Prototyp; neue Module; application, jfx-bridge, npm-Pakete.
- **Neue Dateien:** `EDITOR_UPGRADE.md` mit tatsächlicher API-/Datenumstellung; Importfixtures nur für wirklich vorhandene zu übernehmende Formate.
- **Ändern:** `build.sbt`, produktive Scala-/TS-Editor-Einstiege, README-/Paketdokumentation, `package.json`/weitere betroffene npm-Manifests und über Package-Manager regenerierte Lockfiles; alte Prototypquellen/Tests gezielt entfernen bzw. ablösen.
- **API:** Neuer öffentlicher Einstieg festlegen; eventuell `editor(...)` als Komfortfunktion über Session/View/Field. Keine pauschale Lexical-JSON-Kompatibilitätszusage.
- **Tests:** Vollständige Scala-Tests, Production Bridge Link, npm-Verifies, echte Editor-Browser-/No-JS-Gates, Client-/SSR-/Pages-Builds, Dependency-Graph und veröffentlichbare Artefakte; Source-only-Suche nach verbleibenden Lexical-Imports/Registrierungen.
- **Akzeptanz:** Keine scalajs-lexical-/@anjunar/scalajs-lexical-Produktionsabhängigkeit oder erreichbare Legacy-Registrierung; native API in Demos/Consumer; Datenverlustfreiheit anhand tatsächlich benötigter Importfixtures; alle einschlägigen Freigaben aus P28 belegt.
- **Risiken:** Öffentliche API und reale gespeicherte Inhalte sind der einzige mögliche Migrationsbedarf. Im Ausgangsauftrag wurde keine automatische Konvertierung unbekannter Daten autorisiert oder spezifiziert; diese nicht erfinden.
- **Dependencies:** P29; Architektur §25. Kein Umbau des Prototyps in früheren Phasen.

## Optionale Folgepakete nach dem Ersatz

Diese Pakete gehören zum langfristigen Ausbau, nicht zum Gate für die erste Ablösung. Sie werden jeweils mit eigenem konkretem Auftrag umgesetzt; offene Folgepakete machen den hier definierten neuen Editor nicht automatisch unvollständig.

### X01 — Tabellen mit eigenem Selection-Vertrag

- **Ziel:** Fachliche Tabellen mit Zellen, Keyboard-Navigation und rechteckiger Auswahl.
- **Module:** Neues table, separate Adapter; keine Rückabhängigkeit vom Core.
- **Neue Dateien:** `table/TableNode.scala`, `TableRowNode.scala`, `TableCellNode.scala`, `TableSelection.scala`, `TableCommands.scala`, `TableSupport.scala`; Tests `TableStructureSpec.scala`, `TableSelectionSpec.scala`; IT `table-editing.spec.ts`.
- **Ändern:** `build.sbt`, optionale Presets und GFM-Profile, falls ausdrücklich gewählt.
- **API:** Registered Selection-Mapping/Validator, Insert/Delete Row/Column, Zellnavigation; Merge/Span erst mit separat festgelegten Invarianten.
- **Tests:** Rechteckigkeit, Zelllöschung/Selection-Restore, Tab/Escape, Clipboard-Fragmente, SSR `table/tbody/tr/th/td`, Undo und Readonly.
- **Akzeptanz:** Kein Core-Spezialfall für TableSelection; JSON vollständig; Markdown-Verluste für nicht darstellbare Tabellen explizit; normale Editoren ziehen das Modul nicht herein.
- **Risiken:** Tabellen sind keine beliebige NodeSelection-Menge; komplexe Span-Modelle können Editing/Clipboard stark erweitern.
- **Dependencies:** P30, vorhandene Selection-Erweiterbarkeit aus P03/P05.

### X02 — Syntax-Highlighting als View-Erweiterung

- **Ziel:** Code lesbarer darstellen, ohne kanonischen Text in Token-Nodes umzubauen.
- **Module:** Neues code-highlighting; code/jfx; optional Worker-Adapter.
- **Neue Dateien:** `ember-code-highlighting/.../Highlighter.scala`, `HighlightResult.scala`, `CodeDecorations.scala`; Tests für Revision/Stale Results; Browserfixtures.
- **Ändern:** Optionale Code-NodeView; Build-/Preset-Registrierung.
- **API:** Reiner Text→Tokenbereich-Service, versionierte async Ergebnisse, JFX-Dekorationen als abgeleiteter View-State.
- **Tests:** Text/Selection/IME unverändert, veraltetes Worker-Ergebnis ignoriert, SSR ohne Worker, Cleanup, Code-JSON-/Markdown-Roundtrip.
- **Akzeptanz:** Highlighting ausblenden ändert kein Dokument/History; kein zweiter Editor im Codeblock und kein unabhängiger DOM-Renderer.
- **Risiken:** Mehrere Textspans verändern DOM-Offsets; NodeView muss SelectionPort und Composition-Vertrag erfüllen.
- **Dependencies:** P15, P23, P28; Freigabe nach P30.

### X03 — Kollaboration zunächst als eigenständiger Architekturspike

- **Ziel:** Operations-/ID-/Undo-Vertrag für konkurrierende Änderungen belegen, bevor Netzwerkfeatures implementiert werden.
- **Module:** Neues experimentelles collaboration-Testmodul; core/history nur bei nachgewiesenem Vertragsbedarf.
- **Neue Dateien:** `JFX_EDITOR_COLLABORATION.md`, reines Zwei-Replikat-Testmodell und generative Konvergenztests; noch kein produktiver Server/Transport.
- **Ändern:** Architecture ADRs für CRDT/OT-Auswahl, IDs, Remote Selection, History und Schema-Migration.
- **API:** Replikatgebundene IDs, remote Operationsmapping, selektives lokales Undo als zunächst experimentelle Schnittstelle.
- **Tests:** Vertauschte Lieferreihenfolge, Wiederholung, Offline-Rejoin, Selection in gelöschten Bereichen, lokale Undo-Aktion erhält fremde Änderungen; Composition-Konflikte.
- **Akzeptanz:** Konvergenz-/Undo-Vertrag nachgewiesen und sequenzierter Folgeplan erstellt; keine Behauptung, lokale Snapshot-History sei bereits kollaborationsfähig.
- **Risiken:** Persistente Map, stabile IDs und Transactions allein ergeben noch kein CRDT/OT. Protokoll/Server/Auth sind separate Aufgaben.
- **Dependencies:** P30; Architektur §§8, 11, 13–14.

## Vorlage für die spätere Ausführung einer einzelnen Phase

> Implementiere ausschließlich Phase Pxx aus JFX_EDITOR_IMPLEMENTATION.md. Lies zuerst die zugehörigen Architekturabschnitte und aktuellen Quellverträge. Prüfe die angegebenen Dependencies durch vorhandene Implementierung und Tests. Der Editor ist eine Neuentwicklung; den Prototyp nicht als Architekturgrundlage verwenden. Setze Ziel/API/Dateien dieser Phase um, erfülle ihre Tests und Akzeptanzkriterien und ändere keine unabhängigen Bereiche. Dokumentiere widerlegte Annahmen mit Ursache und korrigiertem Vertrag. Verwende sbt und die tatsächlichen Gates aus AGENTS.md; keine kompilierten JavaScript-Sourcen lesen/bearbeiten. Berichte geändertes Verhalten, tatsächliche Testresultate, verbleibende Risiken und den belegten Phasenstatus.

Vor Abschluss jeder Phase werden folgende Artefakte abgelegt: kompakte API-/Vertragsdokumentation, ausführbare Tests mit reproduzierbarem Befehl und tatsächlichem Ergebnis sowie eine aktualisierte Statuszeile. Ein späterer Agent kann damit den nächsten Schritt übernehmen, ohne Gesprächshistorie oder implizite Browserannahmen rekonstruieren zu müssen.
