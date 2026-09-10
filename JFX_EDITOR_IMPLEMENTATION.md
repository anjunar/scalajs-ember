# JFX Editor: ausführbarer Implementierungsplan

Status: **Meilenstein A und B stehen, C und D angefangen** — P01–P16 abgeschlossen (719
Scala-Tests und 126 Browserfälle in Chromium, Firefox und WebKit grün),
P17–P30 offen. Dieses Repository (`scalajs-ember`) ist das in
Architektur und Plan gemeinte „eigene Repository“. Die generischen JFX-Core-Anteile aus
P08/P09, P19a, P20 und P23 sind **nicht hier, sondern im Nachbar-Repo `../scalajs-jfx`**
implementiert und seit P17 als veröffentlichtes Artefakt `com.anjunar:scalajs-jfx-core:3.0.5`
eingebunden ([build.sbt](build.sbt)), vorher als Quell-Abhängigkeit; der Vertrag steht in
[JFX_CORE_INTEGRATION.md](JFX_CORE_INTEGRATION.md). Das erledigt nicht die jeweiligen
Editor-Integrationsphasen. Stand: 10. September 2026.

Eine laufende Demo des jeweils erreichten Standes liegt in
[ember-demo/](ember-demo/README.md) -- nicht publiziert, ohne Bundler, `node
ember-demo/dev/server.mjs`. Sie ist keine Phase des Plans, sondern die Probe darauf, dass sich
die Module tatsaechlich zu einer Anwendung zusammensetzen lassen.

Kommentare und Scaladoc im Quelltext werden ab dem 10. September 2026 auf **Englisch**
geschrieben; die Module bis P11 tragen noch deutsche Kommentare, und die werden nicht
nachträglich umgestellt. Diese Dokumente bleiben deutsch.

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

Seit P17 lädt dieser Befehl nur noch **diesen** Build — `jfx-core` kommt als Binärartefakt, das
Nachbarverzeichnis wird nicht mehr mitgeladen. Wer dort etwas ändert, führt das Gate im
Nachbar-Repo aus und veröffentlicht eine neue Version; hier ankommen kann die Änderung erst
danach:

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

> **Abgeschlossen.** Alle geplanten Dateien liegen unter
> `ember-core/src/main/scala-3/ember/editor/core/`; `EditorSession.scala`, `Transaction.scala`
> und `Schema.scala` sind entsprechend erweitert. Abnahme:
> `sbt --server "Test/testOnly *"` → 261 Tests grün (54 neue).
>
> **Befund aus der Umsetzung:**
>
> 1. *Bloß berührte Vorfahren sind keine Transform-Kandidaten.* Mein erster Test erwartete, dass
>    beim Einfügen unter `c1` auch `root` besucht wird — „Root zuletzt", wie §3.4 es für Lexical
>    beschreibt. Der Lauf war rot, und zwar zu Recht: `root` steht in diesem Fall nur in
>    `touchedAncestors`, nicht in `changedNodes`. §3.4 sagt genau das („Nur zur Traversierung
>    markierte Vorfahren sind keine gleichwertigen Transform-Kandidaten"), und es ist der
>    Unterschied zwischen einer lokalen Normalisierung und einer, die bei jedem Tastendruck den
>    Pfad bis zur Wurzel durchläuft. Root wird besucht, wenn sich seine eigene Kindliste ändert
>    — beide Fälle stehen jetzt getrennt in `TransformSpec`.
>
> **Ergänzungen gegenüber dem Plan:**
>
> 2. *`TransformScope` statt der ganzen Transaktion.* §10 verlangt, dass Transforms
>    ausschließlich den Entwurf lesen und keine DOM- oder Netzwerknebenwirkungen haben. Reichte
>    man ihnen die `Transaction`, wäre das eine Behauptung in der Dokumentation. Der eigene Typ
>    macht es zur Konstruktion: kein `dispatch`, kein Feldzugriff, keine Metadaten. Command-Handler
>    bekommen denselben Zugriff — eine Command-Kette, die sich selbst verlängert, ist genau die
>    verdeckte Reentranz, die §10 ausschließt.
> 3. *Der Wire-Name einer ersetzten Knotenart bleibt auflösbar.* §8.3 sagt, ein Rendererwechsel
>    migriere kein Dokument. Also muss `schema.byId(alterName)` weiter den Ersatz liefern,
>    sonst wäre jede bereits gespeicherte Datei nach einer Spezialisierung undekodierbar.
> 4. *`ResolvedExtensions` trägt das Schema.* Das Dokument braucht es, bevor die Sitzung
>    existiert. Der Ablauf ist deshalb: auflösen → Dokument gegen `resolved.schema` bauen →
>    Sitzung erzeugen. Ein Dokument aus einem anderen Schema wird mit
>    `ExtensionError.SchemaMismatch` abgewiesen, statt erst beim ersten Transform aufzufallen.
> 5. *`DispatchOutcome` trennt „übernommen" von „geändert".* Ein Handler kann prüfen und
>    feststellen, dass nichts zu tun ist; er hat die Absicht trotzdem bearbeitet. `commit.isNoOp`
>    beantwortet die andere Frage.
>
> **Präzisierungen, die der Plan offenließ:**
>
> 6. *Ein Pass mit Mutation wirft.* §12 verlangt die Diagnose „im Entwicklungsmodus". Es ist eine
>    Vertragsverletzung des Handlers, nach P01s Konvention also eine Exception und kein `Left`.
>    `SessionConfig.strictCommands` schaltet die Überwachung für Produktion ab — der Vertrag
>    gilt dann unverändert, nur unbeobachtet.
> 7. *Budgetüberschreitung ist ein Fehler, kein Abschneiden.* §10 ist da eindeutig. Die Meldung
>    nennt alle beteiligten Transforms: bei zwei Regeln, die einander zurückdrehen, ist keine
>    von beiden für sich auffällig — erst das Paar ist der Befund.
> 8. *Die Dirty-Menge einer Runde ist der gesamte aufgelaufene ChangeSet.* Weil Transforms
>    idempotent sein müssen, ist es unschädlich, dass eine spätere Runde bereits normalisierte
>    Knoten noch einmal ansieht. Eine inkrementelle Menge wäre schneller und kommt, wenn eine
>    Messung sie rechtfertigt (§8.3) — nicht auf Verdacht.
> 9. *Kein globaler `activeEditor`.* Die heterogenen Registries (`CommandRegistry`, `Schema`,
>    `StateFields`) kapseln je genau einen `asInstanceOf`, dessen Typzeuge das nachgeschlagene
>    Objekt selbst ist. Nach außen gibt es kein `Any`.


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

> **Abgeschlossen — damit steht Meilenstein A.** Neues Modul `ember-rich-text`
> (sbt-ID `scalajs-ember-rich-text`, Paket `ember.editor.richtext`) mit allen geplanten
> Dateien. Abnahme: `sbt --server "Test/testOnly *"` → 316 Tests grün (55 neue).
>
> **Befund aus der Umsetzung:**
>
> 1. *Ein Literal kann einen Unicode-Test still entwerten.* Mein Test für kombinierende
>    Zeichen stand als `open("café")` da — mit **vorkomponiertem** é. Das sind vier Zeichen,
>    nicht fünf, und geprüft worden wäre gar keine Graphemgrenze. Aufgefallen ist es nur, weil
>    der Caret bei Offset 5 aus dem Bereich fiel. Beide Vorkommen stehen jetzt als ausdrückliches
>    `\u0301`-Escape im Quelltext: die zerlegte und die vorkomponierte Schreibweise sehen im
>    Editor identisch aus, und genau darauf darf sich ein Test nicht verlassen.
>
> **Ergänzungen gegenüber dem Plan:**
>
> 2. *`RichText` trägt `RootNode` und `TextNode` mit bei*, obwohl beide im Kern definiert sind.
>    Der Kern ist ein Modell, kein Profil — er registriert nichts von selbst. Module, die auf
>    rich-text aufbauen (P13 Listen, P14 Links), tragen sie nicht erneut bei, sondern
>    deklarieren `dependsOn`.
> 3. *Drei Normalisierungs-Transforms* statt der im Plan nur erwähnten „leeren
>    Dokumentnormalisierung": Wurzel braucht Block, Block braucht Textlauf, überflüssige leere
>    Läufe weg. Die dritte hat zwei Wächter, beide notwendig — ohne den ersten liefe sie mit der
>    zweiten in eine Endlosschleife, die erst das Arbeitsbudget nach 32 Runden abbricht; ohne
>    den zweiten verlöre sie den Caret.
> 4. *`RichText.emptyDocument` und `caretAtStart`.* §8.2 verlangt, dass das Profil für eine leere
>    Fläche Absatz und Caretposition herstellt. Die Transforms halten das während des Editierens
>    aufrecht, aber beim Anlegen ist noch nichts schmutzig — es liefe kein Transform.
> 5. *`boundarySettings` in `build.sbt` verallgemeinert.* Der Grenz-Lint aus P01 nimmt jetzt
>    Allowlist und verbotene Pakete als Parameter. `ember-rich-text` darf genau `ember-core`,
>    sonst nichts; negativ geprüft.
>
> **Präzisierungen, die der Plan offenließ:**
>
> 6. *Die Unicode-Datenversion ist festgelegt* — die Risikozeile verlangt das ausdrücklich.
>    `unicodeVersion` lautet `"16.0.0 (Teilmenge, ohne GB9c)"` und benennt damit auch, was
>    **nicht** zugesichert wird. Implementiert sind GB1–GB13 vollständig, einschließlich der
>    beiden kontextabhängigen Regeln GB11 (Emoji-ZWJ) und GB12/GB13 (Flaggen-Parität) — an
>    genau denen scheitert der Codepoint-Fallback, vor dem die Risikozeile warnt. Die
>    Zeicheneigenschaften stammen zweigeteilt aus ausdrücklichen Bereichen (strukturell) und
>    `Character.getType` (kategoriegetrieben); beides ist im Quelltext benannt. Nicht
>    implementiert: GB9c (Indic Conjunct Break). `Extended_Pictographic` ist über gepflegte
>    Bereiche angenähert, die Wortgrenzen sind eine dokumentierte Vereinfachung statt UAX #29 §4.
> 7. *`TextEditing` arbeitet mit „Block" = Elternknoten eines Textlaufs*, nicht mit
>    `ParagraphNode`. So bleiben die Funktionen für P12–P15 erweiterbar, ohne jetzt schon Fälle
>    zu behandeln, die es noch nicht gibt.
> 8. *Enter teilt am Anfang und am Ende ausdrücklich nicht.* Ein Split bei Offset 0 oder
>    Textlänge erzeugte einen leeren Lauf, den P12 wieder einsammeln müsste. Stattdessen werden
>    nur Geschwister verschoben.
> 9. *Blockzusammenführung verschmilzt die beiden Textläufe an der Naht nicht.* Das ist §8.2s
>    Normalisierung: sie darf nur gleich markierte Läufe zusammenführen, und über Marks weiß
>    dieses Modul noch nichts. Deshalb gehört sie zu P12.
> 10. *Keine Marks in P06.* §8.2 nennt Strong, Emphasis, Underline, Strike und InlineCode als
>     eingebaute Marks des Profils, aber sie gehören mit Bereichsformatierung, `TypingMarks` und
>     der Lauf-Normalisierung zusammen — ein halber Mark-Vertrag jetzt wäre eine API, die P12
>     gleich wieder umbaute.


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

> **Abgeschlossen.** Neues, nicht publiziertes Modul
> `ember-integration` (sbt-ID `scalajs-ember-integration`) samt Browser-Harness unter
> `ember-integration/browser/`. Damit ist die `jfx-core`-Kante zum ersten Mal scharf.
>
> Abnahme:
>
> ```
> sbt --server "scalajs-ember-integration/fullLinkJS"
> cd ember-integration/browser && npm ci && npm run verify
> ```
>
> | Lauf | Ergebnis |
> | --- | --- |
> | Serverimport ohne Browserglobals | grün |
> | Chromium, 8 Fälle | grün |
> | WebKit, 8 Fälle | grün |
> | Firefox, 8 Fälle | grün (über `EMBER_FIREFOX_CHANNEL=moz-firefox`, siehe unten) |
> | Scala-Gate (316 Tests) | unverändert grün |
>
> **Befund — der mitgelieferte Firefox startet unter Windows nicht:**
>
> 1. Playwright meldet nur `browserType.launch: spawn UNKNOWN`, noch **vor** dem ersten Test.
>    Das Anwendungsereignisprotokoll nennt die Ursache: `firefox.exe` verlangt im Manifest die
>    private Side-by-Side-Assembly `mozglue`, und Windows kann sie nicht auflösen.
>
>    Vier Hypothesen geprüft und ausgeschlossen: kein beschädigter Download
>    (`install --force` liefert dieselbe Datei), keine Identitätsabweichung (angefordertes und
>    deklariertes `assemblyIdentity` stimmen überein), nicht die Ablageform (eine zusätzliche
>    `mozglue.manifest` ändert nichts), und **nicht die Maschine**: ein regulär installierter
>    Firefox derselben Version hat dieselbe Manifest-Abhängigkeit und startet einwandfrei. Das
>    Problem liegt im mitgelieferten Build.
>
>    Gelöst über den offiziell unterstützten Kanal `moz-firefox`, der den installierten Firefox
>    ansteuert. Er spricht WebDriver BiDi statt Juggler — dieselbe Engine, anderer Steuerkanal.
>    Deshalb ist er **nicht** die Voreinstellung: die Konfiguration liest
>    `EMBER_FIREFOX_CHANNEL`, und ohne die Variable läuft der kanonische mitgelieferte Build,
>    so wie in der CI unter Linux. Die Dreimotoren-Abnahme aus §24 ist damit erbracht, die
>    genaue Ursache im Build aber nicht abschließend geklärt — dafür bräuchte es ein
>    `sxstrace` mit Administratorrechten. Die Befehle stehen im Harness-README.
>
> **Ergänzungen gegenüber dem Plan:**
>
> 2. *`server-import.mjs` als eigener Lauf.* Der Plan listet den Server-Import unter Tests,
>    ohne ihm eine Datei zu geben. §15.2 verlangt, dass das Modul beim Laden weder `window`
>    noch `document` liest — das ist die Voraussetzung für SSR und lässt sich in Node direkt
>    prüfen: dort gibt es die Globals nicht, ein Zugriff würde also werfen statt still
>    gutzugehen.
> 3. *`.github/workflows/verify.yml`.* Zwei getrennte Jobs: die headless Gates brauchen weder
>    Browser noch das Nachbar-Repo, die Integration beides. Ein Problem in scalajs-jfx reißt
>    so nicht Kern und rich-text mit. **Hier nicht verifiziert** — der Workflow läuft erst beim
>    nächsten Push.
> 4. *Modell und Darstellung getrennt abfragbar.* `read()` liefert das Modell, `rendered()`
>    den DOM-Text. Dass beide dasselbe sagen, ist der eigentliche Nachweis: die zwei Runtimes
>    arbeiten zusammen und nicht bloß nebeneinander.
>
> **Präzisierungen, die der Plan offenließ:**
>
> 5. *Harness in `.mjs` statt `.ts`.* Der Plan nennt `playwright.config.ts`, `fixtures.ts`,
>    `identity.spec.ts`. Die funktionierende Harness im Nachbar-Repo verwendet `.mjs`, und eine
>    zweite Dateikonvention in derselben Projektfamilie hat für einen Testtreiber keinen
>    Nutzen. `fixtures.ts` entfällt ganz — die Fixtures leben in der Scala-App, die Datei wäre
>    leer geblieben.
> 6. *Die Fixture-App rendert nach jedem Commit vollständig neu.* Ausdrücklich die naive
>    Variante. §15.1 verlangt für den echten Editor das Gegenteil, und genau das baut P09 —
>    hier wäre eine keyed Projektion verfrüht und würde den Nachweis vermengen.
> 7. *Kein `contenteditable`.* Der Container fängt Tastendrücke ab und verhindert die native
>    Aktion. Native Eingabe mit Composition, Mutation-Observer und Recovery ist P21 bis P23.
> 8. *Warum dieses Modul an `jfx-core` hängen darf.* Die Publish-Regel aus §6 verlangt, dass
>    ein **veröffentlichtes** Modul nur auf veröffentlichte Artefakte zeigt. `ember-integration`
>    wird nie veröffentlicht. `ember-jfx` in P09 wird das nicht dürfen und braucht dort einen
>    eigenen, publizierbaren Vertrag.
> 9. *String-basierte `@JSExport`-API.* Ein Testtreiber in JavaScript hat nichts anderes. Die
>    produktive Fassade aus §23 arbeitet mit opaken Handles und validierten DTOs; hier wird
>    davon nichts eingefroren.


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

> **Abgeschlossen als Prüfphase.** Die Implementierung lag bereits im Nachbar-Repo; hier
> entstanden die Integrationsnachweise. Abnahme:
> `EMBER_FIREFOX_CHANNEL=moz-firefox npx playwright test` → **87 Fälle grün** (29 je Engine,
> Chromium/Firefox/WebKit), davon 21 neu für diese Phase.
>
> **Bewusste Abweichung — keine neuen Scala-Specs im Nachbar-Repo:**
>
> 1. Der Plan listet `TextSpliceSpec.scala` und `RuntimeMoveSpec.scala` unter
>    `../scalajs-jfx/jfx-core/src/test/`. Beide wären Duplikate: `HostEditingSpec` deckt dort
>    mit 68 Fällen genau diese Verträge ab — „use UTF-16 offsets and retain the same host",
>    „reject invalid ranges without changing mounted or pending text", „move existing
>    references instead of duplicating them", „validate cycles and anchors before removing the
>    source", „preserve physical children and later updates across parents". Dazu kommt: in
>    jenem Repo wird gerade gearbeitet (unfertige Änderungen im Baum). Tests in einen fremden,
>    offenen Arbeitsbaum zu schreiben, die dort schon existieren, wäre in beide Richtungen
>    falsch.
>
> **Was dieses Repo stattdessen beisteuert:**
>
> 2. *DOM-Knotenidentität.* Die JVM-Suite kann sie nicht prüfen — es gibt dort keinen echten
>    DOM. Ob ein Splice denselben Textknoten behält und ein Move dasselbe Element bewegt, ist
>    ein `===`-Vergleich auf DOM-Objekten, und für den Editor hängt daran alles: ein neu
>    erzeugter Textknoten nähme Caret, Selection und eine laufende IME-Eingabe mit ins Grab.
>    Zusätzlich belegt über einen Listener, der den Move überlebt.
> 3. *Der No-op-Vertrag, mit einem MutationObserver bewiesen.* „Identischer Text erzeugt keine
>    Mutation" lässt sich nur so ehrlich prüfen: der Observer sieht **jeden** Schreibzugriff,
>    auch einen, der denselben Wert setzt. Mit Gegenprobe — eine echte Änderung erzeugt genau
>    einen Record, sonst wäre der Test auch bei totem Observer grün.
> 4. *Logische Kindliste synchron zum DOM, black-box belegt.* `_children` ist `private[jfx]`
>    und von außen nicht lesbar. Stattdessen wird nach einem Move ein weiteres Label über
>    `contentCursor` montiert: läge die Kindliste der Runtime daneben, landete es an der
>    falschen Stelle — ohne Fehlermeldung, nur mit falscher Reihenfolge.
> 5. *Gegen **diese** Linkerausgabe.* Der Nachbar testet seinen eigenen Build. Wir linken
>    `jfx-core` mit unseren Einstellungen — ESModule, ES2021, `fullLinkJS` mit optimierter
>    Semantik. Ein Vertrag kann dort halten und hier brechen; jetzt ist geprüft, dass er es
>    nicht tut. (P17 hat die damalige Quell-Abhängigkeit durch das Artefakt 3.0.5 ersetzt.
>    Das Argument wird dadurch stärker, nicht schwächer: geprüft wird jetzt genau der Stand,
>    den ein fremder Konsument bekäme.)
>
> **Nachgereicht in P09:**
>
> 6. *„Derselbe Testfall stimmt in SSR und Browser überein."* Zum Zeitpunkt von P08 hatte
>    dieses Repo keinen SSR-Pfad. Mit P09 gibt es ihn, und der Vergleich steht in **einem**
>    Testfall: `projection.spec.mjs` → „rendert initial dasselbe wie der SSR-Weg" stellt
>    `innerHTML` der Editierfläche gegen `DocumentView.renderToHtml` desselben Dokuments.


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

> **Abgeschlossen.** Drei neue Module: `ember-html` (Semantik-SPI und `HtmlFragment`),
> `ember-jfx` (keyed Projektion, `DocumentView`, `EditorProperties`) und `ember-standard`
> (die Adapter für Wurzel, Absatz und Textlauf). Abnahme:
>
> ```
> sbt --server "Test/testOnly *"
> sbt --server "scalajs-ember-integration/fullLinkJS"
> cd ember-integration/browser && EMBER_FIREFOX_CHANNEL=moz-firefox npm run verify
> ```
>
> | Lauf | Ergebnis |
> | --- | --- |
> | `scalajs-ember-core` | 261 Tests grün |
> | `scalajs-ember-rich-text` | 55 Tests grün |
> | `scalajs-ember-standard` (`ProjectionSpec`) | 19 Tests grün |
> | Chromium / Firefox / WebKit | je 42 Fälle grün, davon 13 neu (`projection.spec.mjs`) |
> | Serverimport ohne Browserglobals | grün |
>
> Der POM von `ember-jfx` zeigt auf `com.anjunar:scalajs-jfx-core_sjs1_3:3.0.4` — ein
> veröffentlichtes Artefakt, kein Verzeichnis. Die Publish-Regel aus §6 ist damit belegt und
> nicht nur beabsichtigt (`sbt --server "scalajs-ember-jfx/makePom"`).
>
> **Widerlegt — der Textlauf braucht den Wrapper sofort, nicht erst in P21:**
>
> Der erste Entwurf hat `TextNode` auf einen rohen DOM-Textknoten abgebildet und §15.1s
> „stabilen Wrapper mit einem Textkind" als Entscheidung für P21 behandelt, wo der
> SelectionPort ihn braucht. Zwei unabhängige Verträge widersprechen:
>
> 1. §15.1 nennt den Grund selbst, und er gilt für **beide** Profile: der Wrapper „vermeidet
>    zusammengefasste benachbarte SSR-Textnodes". Zwei Läufe nebeneinander wären in der
>    Ausgabe ein einziger Textknoten — die Grenze ließe sich beim Hydrieren nicht mehr finden.
>    Der Risikoabschnitt dieser Phase sagt dasselbe: „rohe benachbarte SSR-Textnodes vermeiden".
> 2. `KeyedChildren` weist einen Textknoten als Kind ab: *„Keyed children require physical
>    element components."* Ein Textknoten hat keinen eigenen Host, an dem sich eine Reihenfolge
>    festmachen ließe.
>
> `HtmlShape` hat deshalb kein `Text` mehr, sondern `TextRun(tag, value, attributes)` — ein
> Element mit genau einem Textkind. `ParagraphSupport.text` liefert `span`, in beiden Profilen.
>
> **Bewusste Abweichung — kein `KeyedChildrenSpec.scala`:**
>
> Wie in P08 wäre die Suite ein Duplikat. `KeyedChildren` ist im Nachbar-Repo implementiert
> **und** geprüft: `HostEditingSpec` deckt `transferTo` über Gruppengrenzen samt anschließender
> Updates im Ziel und den Preflight einer ganzen keyed Aktualisierung ab, `TableViewSpec` fährt
> dieselbe Klasse über `TableColumnProjection`. Was hier fehlte, war nicht ihre Prüfung, sondern
> ihre Verwendung durch den Editor — und die prüfen `ProjectionSpec` und `projection.spec.mjs`.
>
> **Zwei Korrekturen am eigenen Entwurf, die die Tests erzwungen haben:**
>
> 1. *`HtmlAttribute.apply` verdeckte das synthetische der Case-Klasse.* `parse` baute mit
>    `HtmlAttribute(...)` und landete damit wieder in `apply` — eine Endlosrekursion, die erst
>    als `RangeError: Maximum call stack size exceeded` auffiel. Gebaut wird jetzt mit `new`,
>    und der Konstruktor ist privat, damit auch `copy` niemandem an der Prüfung vorbeihilft.
> 2. *Die Kindergruppen entstanden zu spät.* Ein erster Versuch trug sie nach dem Mount nach
>    und bekam leere Absätze. Richtig ist die Reihenfolge, die `KeyedChildren` ohnehin vorgibt:
>    `DocumentProjection.build` hängt die Gruppe **vor** dem Mount ein, der Container montiert
>    sie in seinem `compose`, und die Gruppe ruft dabei wieder `build`. Ein Durchgang, in
>    Dokumentreihenfolge.
>
> **Und eine dritte, die der Browser gefunden hat:** `DocumentView` meldete beim Mount die
> Anfangsrevision an `onProjected` — an Zuhörer, die es zu diesem Zeitpunkt nicht geben kann,
> weil die Ansicht erst danach zurückgegeben wird. Statt der toten Meldung gibt es jetzt
> `projectedRevision`: wer sich später registriert, hat nichts verpasst.
>
> Modulverträge: [ember-html/README.md](ember-html/README.md),
> [ember-jfx/README.md](ember-jfx/README.md), [ember-standard/README.md](ember-standard/README.md).

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

> **Abgeschlossen.** Neues Modul `ember-json` (sbt-ID `scalajs-ember-json`, Paket
> `ember.editor.json`), abhängig allein vom Kern. Abnahme:
>
> ```
> sbt --server "scalajs-ember-json/Test/testOnly *"
> ```
>
> | Suite | Ergebnis |
> | --- | --- |
> | `DocumentJsonSpec` | 39 Tests grün |
> | `SchemaMigrationSpec` | 13 Tests grün |
> | Gesamtes Scala-Gate | 387 Tests grün |
>
> **Zwei Entwurfsentscheidungen, die die Risikozeile erzwungen hat:**
>
> 1. *Die Knotenliste ist ein Array, kein nach ID geschlüsseltes Objekt.* Der Plan warnt: „JSON-Parser
>    kann doppelte Objektkeys bereits zusammenfassen […] während doppelte Node-IDs immer Fehler
>    bleiben." Beides zugleich geht nur so. `js.JSON.parse` fasst `{"a":1,"a":2}` zu `{"a":2}`
>    zusammen, bevor dieses Modul den Wert sieht — als Objekt wäre eine doppelte Knoten-ID spurlos
>    verschwunden und das Dokument sähe gültig aus. Als Array bleibt sie sichtbar
>    (`DecodeError.DuplicateNodeId`). Für die Objektschlüssel selbst hält ein Test die
>    dokumentierte Regel fest — letzter Wert gewinnt —, statt eine Prüfung zu behaupten, die es
>    nicht gibt.
> 2. *Serialisiert wird selbst, nicht mit `js.JSON.stringify`.* Zwei Gründe: die Feldreihenfolge
>    muss deterministisch sein (sonst sind Roundtrip-Fixtures wertlos), und der Payload landet
>    später in einem `<script>` (§16) — `<`, `>`, `&`, U+2028 und U+2029 entkommen deshalb
>    grundsätzlich. Der Testfall dazu steckt ein `</script><script>alert(1)</script>` in einen
>    Textlauf und prüft, dass die Ausgabe kein einziges `<` enthält und der Roundtrip trotzdem
>    stimmt.
>
> **Über den Plan hinaus — `MarkJsonCodec`:** `TextNode.marks` existiert seit P02, Marks sind
> offen (§8.2), und der Kern kennt keine einzige. Ohne Mark-SPI wäre ein markierter Textlauf
> heute nicht verlustfrei persistierbar, und „verlustfrei" ist die Zielzeile dieser Phase. Die
> konkreten Marks kommen weiterhin erst mit P12.
>
> **Bewusste Abgrenzungen:**
>
> - *Kein zweiter Validator.* Referenzielle Integrität, Zyklen, mehrfache Eltern, Erreichbarkeit
>   und Schemakonformität prüft `Document.build`. Der „P02-Builder für validierte
>   Decode-Ergebnisse" aus der Änderungsliste war deshalb nicht nötig — der Kern konnte es schon.
>   Geprüft wird hier nur, was der Kern gar nicht sehen kann: Typen, Zahlenbereiche, Limits,
>   doppelte IDs im Payload, Versionen.
> - *`UnsupportedNode` ist ein Container.* Ein unbekannter Knoten kann bekannte enthalten. Ohne
>   Kindliste wären die Absätze unter einer unbekannten Tabelle nach dem Dekodieren unerreichbar,
>   und der Validator lehnte das Dokument ab — die Erhaltung hätte zerstört, wozu es sie gibt.
> - *Eine bekannte Art ohne Codec bleibt auch unter `Preserve` ein Fehler.* Das ist ein
>   Verdrahtungsfehler der Anwendung, kein unbekanntes Datum.
> - *Alle Knotenfehler auf einmal.* Wer einen fremden Payload debuggt, will nicht zwanzig Läufe
>   für zwanzig Tippfehler. Ein teilweise gültiges Dokument entsteht dabei ohnehin nicht.
>
> **Nachgetragen fuer die Demo:** `JsonText.renderPretty` -- eingerueckt, gleiche
> Zeichenmaskierung, gleiche Feldreihenfolge. Die Wire-Form bleibt kompakt; diese ist fuer
> Diagnoseausgaben und die Demo, und ein Test haelt beide auseinander.
>
> Modulvertrag: [ember-json/README.md](ember-json/README.md).

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

> **Abgeschlossen.** Neues Modul `ember-history` (sbt-ID `scalajs-ember-history`, Paket
> `ember.editor.history`), abhängig allein vom Kern. Abnahme:
>
> ```
> sbt --server "scalajs-ember-history/Test/testOnly *"
> ```
>
> | Suite | Ergebnis |
> | --- | --- |
> | `HistorySpec` | 33 Tests grün |
> | `HistoryRetentionSpec` | 11 Tests grün |
> | Gesamtes Scala-Gate | 440 Tests grün |
>
> **Kernänderung, die die Phase erzwungen hat — `Transaction.restore`:**
>
> §14 legt die History auf „strukturell geteilte Document-Snapshots" fest und verweist eine
> operationsbasierte History ausdrücklich in eine spätere Erweiterung. Ein Undo hat damit keine
> Operationen, sondern zwei Stände — und der Kern hatte keinen Weg, einen Stand einzusetzen. Neu
> sind deshalb:
>
> - `Transaction.restore(document, selection)` (und dasselbe auf `TransformScope`, für den Weg aus
>   §12: `editor.register(Undo) { (tx, _) => history.undo(tx) }`),
> - `DocumentDiff` — rechnet den Unterschied zweier Stände in ChangeSet und Positionsabbildung um,
>   an genau einer Stelle und nicht in der Projektion,
> - `UpdateError.ForeignSchema` — §13: „Das Schema einer Session ist fest."
>
> Das ist mehr als die Änderungsliste vorsah („Tx-Metadaten falls ein erforderlicher
> Origin/Policy-Vertrag fehlt"), aber weniger, als es aussieht: `restore` ist kein neues Primitiv
> unter den anderen. Eine `Operation` beschreibt, was jemand **tut**, und liefert ihre Wirkung
> selbst mit; `restore` beschreibt, wohin ein Stand zurückgesetzt wird, und die Wirkung muss
> ausgerechnet werden. Die Scaladoc sagt ausdrücklich, dass es kein bequemer Ersatz für eine
> Bearbeitung ist.
>
> **`HistoryPolicy` ist typisiert, nicht ein String-Tag.** `TransactionMeta` trug den Hinweis,
> P11 werde die Policy „über `tags` anbinden". §14 führt sie aber neben `Origin` als *typisierte*
> Metadaten auf, und ein `Set[String]` ist das nicht. Sie steht jetzt als `enum HistoryPolicy` im
> Kern, wie `Origin` — der Kern wertet beide nicht aus, er trägt sie.
>
> **Backspace und Delete sind am Ergebnis nicht unterscheidbar.** Bei Caret 5 löscht Backspace
> `[4,5)` und lässt den Caret auf 4; bei Caret 4 löscht Delete `[4,5)` und lässt ihn auf 4 —
> gleicher Splice, gleiche Endposition. §14 verlangt trotzdem getrennte Gruppen. Der Unterschied
> steht ausschließlich im Caret **davor**, und genau den liest `HistoryGrouping`
> (`commit.previous.selection`). Ohne diesen Blick zurück wäre die Regel nicht erfüllbar.
>
> **Gruppierung wird abgeleitet, nicht gemeldet.** Ein Editor könnte jeden Command beschriften;
> die Beschriftung wäre eine zweite Wahrheit neben dem, was tatsächlich passiert ist. Was
> geschehen ist, steht im ChangeSet. `HistoryPolicy` bleibt die ausdrückliche Ausnahme für
> Wissen, das dort nicht steht.
>
> **Zwei Wege, auf denen ein Undo sich selbst nicht aufzeichnet:** `history.undo()` setzt
> `Origin.History`, und der Rekorder überspringt diese Herkunft (§14). Wer dagegen
> `HistoryCommands.Undo` dispatcht, bestimmt die Herkunft nicht — deshalb merkt sich die History
> zusätzlich das Dokumentobjekt, das sie gerade eingesetzt hat, und überspringt den Commit, der
> genau dieses Objekt veröffentlicht. Dass das trägt, hängt an einer Eigenschaft, die ein Test
> festhält: jeder gespeicherte Snapshot ist ein veröffentlichter und damit normalisierter Stand,
> Transforms finden daran nichts mehr zu tun.
>
> **Bewusste Abgrenzungen:**
>
> - *Keine Heapmessung.* §14 verlangt Benchmarks zur Freigabe alter Snapshots; unter Scala.js gibt
>   es keine Heapmessung, und eine erfundene wäre schlechter als keine. `HistoryRetentionSpec`
>   prüft das Beobachtbare — getrimmte Stufen sind weg, die Schätzung fällt mit, nach einem Reset
>   ist nichts mehr referenziert.
> - *Die neueste Stufe überlebt das Byte-Budget.* Sonst könnte man ausgerechnet die letzte Aktion
>   nicht zurücknehmen. §14 verweist den Fall („ein riesiger einzelner Import") woandershin, und
>   ein Import setzt die History ohnehin zurück.
> - *Kein Zurückstellen fremder Transaktionen während einer Gruppe.* §14 verlangt es, aber es ist
>   eine Eigenschaft der Sitzung und betrifft nur Composition — P23.
>
> **IT:** Die Demo hat Undo/Redo bekommen, als Knöpfe und über Strg+Z / Strg+Shift+Z. Die
> Statuszeile zeigt die Tiefe beider Stapel.
>
> Modulvertrag: [ember-history/README.md](ember-history/README.md).

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

> **Abgeschlossen.** Marks, Heading, Quote, Breaks, Bereichsformatierung, `TypingMarks` und die
> Textlauf-Normalisierung — in `ember-rich-text`, mit Adaptern in `ember-standard` und dem
> Vertrag dafür in `ember-core`, `ember-history`, `ember-html` und `ember-jfx`. Abnahme:
>
> ```
> sbt --server "Test/testOnly *"
> ```
>
> | Suite | Ergebnis |
> | --- | --- |
> | `TextRunNormalizationSpec` | 14 Tests grün |
> | `RangeFormattingSpec` | 17 Tests grün |
> | `TypingMarksSpec` | 16 Tests grün |
> | `RichTextStructureSpec` | 21 Tests grün |
> | `ProjectionSpec` (semantischer Export) | 30 Tests grün, 11 neu |
> | Gesamtes Scala-Gate | 519 Tests grün |
>
> **Der konkrete Normalisierungstest steht wörtlich.** `"Hallo Welt!"`, `"Welt"` fett → drei
> Läufe; Strong wieder weg → wieder **ein** Lauf mit identischem Gesamttext und erhaltener
> linker ID, in **einem** Commit. Fünf Zyklen hintereinander fragmentieren nicht, erneute
> Normalisierung ist ein No-op, und die Gegenfälle (verschiedene Marks, verschiedene Blöcke,
> Breaks dazwischen) verschmelzen nicht.
>
> **Zwei Fehler im eigenen Entwurf, die die Tests gefunden haben:**
>
> 1. *Die Bereichsformatierung markierte den falschen Lauf.* Nach dem Splitten benutzte sie die
>    Range weiter, mit der sie hereinkam — `t0` ist danach aber ein kürzerer Lauf, und dessen
>    Offset 10 bedeutet nichts mehr. Die Transaktion führt die Auswahl über jede Operation mit
>    (§10, Schritt 3); sie muss zurückgelesen und nicht gemerkt werden.
> 2. *Der Merge-Transform lief nie.* Er hing am Absatz — und §3.4 ist eindeutig: ein Vorfahr,
>    der nur auf dem Pfad einer Änderung liegt, ist kein Transform-Kandidat, `touchedAncestors`
>    gibt es genau dafür. Eine Markänderung macht den Absatz nicht dirty. Am Textlauf aufgehängt
>    wird die Regel gefragt, wenn eine Naht entstehen kann — und funktioniert dadurch in jedem
>    Container, ohne einen einzigen zu kennen.
>
> **Backspace/Delete-Erkenntnis aus P11, hier zum zweiten Mal:** eine Regel, die aus dem
> Ergebnis nicht ablesbar ist, muss aus dem Zustand *davor* kommen. Dieselbe Bauform trägt hier
> die Gruppierung und dort die Auswahlrichtung.
>
> **Kernänderungen, die die Phase erzwungen hat:**
>
> - `StateField.onHistoryRestore` samt `HistoryRestorePolicy` und `FieldValue` — §14:
>   "StateFields deklarieren einen eigenen Restore-/Mapping-Vertrag." `TypingMarks` ist der Fall,
>   für den der Satz geschrieben wurde: §11 verbietet ausdrücklich, die für die nächste Eingabe
>   wirksamen Marks nach einem Undo aus der Darstellung zu erraten. `ember-history` trägt den
>   Wert seitdem typisiert im Snapshot, nicht als `Any`.
> - `TransformScope.field`/`setField` — ein Toggle am kollabierten Caret ändert nur dieses Feld.
> - `HtmlShape.TextRun` bekam die inneren Mark-Tags, `TextRunElement` baut die Kette.
>
> **Die explizite View-Ersetzung, gefunden durch die Demo.** `SetHeading` wechselt den Tag unter
> gleicher ID — genau der Fall, den der P09-Wächter noch abgewiesen hat. §15.1 nennt ihn „eine
> explizite View-Ersetzung", also führt die Projektion sie jetzt aus: `NodeView.accepts` fragt
> den Adapter, ob seine Komponente noch passt, und `DocumentProjection.replaceView` baut den
> einen Knoten neu, während die Geschwister nur bewegt werden. Zwei `setItems`-Durchgänge, weil
> `KeyedChildren` einen bekannten Schlüssel grundsätzlich aktualisiert statt neu zu bauen — was
> sein Sinn ist und der Grund, warum unveränderte Geschwister überleben.
>
> Dabei fiel eine Lücke in der Testinfrastruktur auf: ein geworfener Projektionsfehler ging
> still an den Error-Sink, weil die Projektion ein Listener ist. `ProjectionSpec` lässt ihn
> jetzt den Test brechen.
>
> **Bewusste Entscheidungen:**
>
> - *`InlineCode` verdrängt die anderen Marks, und sie ihn.* §8.2 überlässt Widersprüche dem
>   Profil. Der Grund ist kein Geschmack: Markdown kann in einer Code-Spanne nichts fett
>   schreiben — Backticks machen ihren Inhalt wörtlich —, ein Lauf mit beidem wäre also ein
>   Dokument, das §18 nicht verlustfrei exportieren kann.
> - *Toggle über einen gemischten Bereich setzt überall.* Die Alternative — jeden Lauf einzeln
>   invertieren — lässt einen zweiten Druck für den Benutzer wie ein No-op aussehen, während die
>   Stücke stillschweigend tauschen.
> - *Kein `<b>`, kein `<i>`.* §16 verlangt semantisches HTML: `strong` sagt, was gemeint ist,
>   `b` nur, wie es aussieht. Underline bekommt `u` — nicht weil HTML dafür eine gute Antwort
>   hätte, sondern weil §8.2 die Mark aufzählt.
> - *Keine Undo/Redo-Tests in `ember-rich-text`.* §6 stellt `history` neben das Profil, nicht
>   darunter; das Modul kann es nicht linken. Geprüft wird stattdessen, was von hier aus prüfbar
>   ist: dass beide Zustände über dieselben Operationen erreichbar sind und keiner eine Sackgasse
>   ist.
>
> **Nachgetragen in P13.** Die Browser-Suite `projection.spec.mjs` war von der neuen
> Normalisierung genauso betroffen wie `ProjectionSpec` -- drei Faelle fuegen benachbarte Laeufe
> ein und erwarten, dass sie zwei bleiben. Ich hatte die headless-Suite angepasst und die
> Browser-Suite uebersehen; aufgefallen ist es beim Browser-Gate von P13. Die Fixture kann jetzt
> markierte Laeufe einfuegen, und die drei Faelle halten damit wieder das fest, worum es ihnen
> geht: DOM-Identitaet und Reihenfolge.
>
> **Zwischenfall.** Während der Phase hat ein zweiter Agent einen Wort-für-Wort-Ersetzungslauf
> Deutsch→Englisch über 59 Dateien gefahren und die Kommentarprosa zerstört („The Marks in
> stabiler Order after [[MarkId]]"). Kein Code betroffen, Build blieb grün. Zurückgenommen; der
> Fix steht in `c8408fe`. Merkposten: bei parallel arbeitenden Agenten vor längeren Läufen
> `git status` prüfen.
>
> Modulverträge: [ember-rich-text/README.md](ember-rich-text/README.md),
> [ember-standard/README.md](ember-standard/README.md).

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

> **Abgeschlossen.** Neues Modul `ember-list` (sbt-ID `scalajs-ember-list`, Paket
> `ember.editor.list`), abhängig von Kern und Rich-Text-Profil. Abnahme:
>
> ```
> sbt --server "Test/testOnly *"
> ```
>
> | Suite | Ergebnis |
> | --- | --- |
> | `ListEditingSpec` | 28 Tests grün |
> | `ListNormalizationSpec` | 13 Tests grün |
> | `ListProjectionSpec` (ember-standard) | 9 Tests grün |
> | Gesamtes Scala-Gate | 569 Tests grün |
>
> **Kernlücke, die die Phase gefunden hat — `NodeType.validate` lief nie.**
>
> §8.2: „Vollvalidierung erfolgt beim Import; lokale Änderungen validieren betroffene Nodes und
> Strukturpfade." Die strukturellen Invarianten prüfen die Operationen selbst. Die **fachliche**
> Prüfung eines Knotens kann nur sein Deskriptor anstellen — und `NodeType.validate` lief bis
> hierher ausschließlich in `Document.build`, also beim Import und sonst nie. Ein `ListNode` mit
> `start = 0` ging glatt durch.
>
> Neu ist deshalb `EditorSession.checkChangedNodes` samt `UpdateError.InvalidDocument`: an der
> Commit-Grenze, wo §10 auch die `PreCommitRule`n hinstellt — hinter den Transforms, vor der
> Veröffentlichung. Nicht in der Operation, denn ein Zwischenstand verdient kein Urteil: eine
> Formatierung schneidet einen Textlauf in drei Teile und fügt sie danach wieder zusammen. Und
> nur für die tatsächlich geänderten Knoten, damit ein Tastendruck in einem 100 000-Knoten-
> Dokument eine Prüfung kostet und nicht 100 000.
>
> **Projektionsfehler, den die Phase gefunden hat — ein Move in einen neu erzeugten Container.**
>
> Einen Absatz in eine Liste zu fassen sind drei Änderungen auf einmal: Liste erzeugt, Item
> erzeugt, Absatz bewegt. `transferTo` braucht die Zielgruppe — und die entsteht erst durch die
> Neuordnung, die *nach* dem Transfer läuft. In der bisherigen Reihenfolge warf die Neuordnung
> den Absatz aus der alten Gruppe (und unmountete ihn), und die neue Item-Gruppe baute einen
> frischen. §15.1s „Move erhält Node-Identität" galt damit für jeden Move außer dem häufigsten
> in einer Liste.
>
> `DocumentProjection.mountNewContainers` montiert neu erzeugte Container jetzt vorab, mit zwei
> Anpassungen: die Elterngruppe behält die abwandernden Knoten vorläufig (sonst unmountet sie
> deren Komponenten), und die neuen Gruppen entstehen **ohne** die ankommenden Knoten (sonst
> baute die Item-Gruppe einen zweiten Absatz, Sekundenbruchteile bevor der echte ankommt). Der
> Zwischenzustand lebt für die Dauer eines synchronen Commits; §10 garantiert, dass darin kein
> Beobachter läuft.
>
> **Eine Entwurfsentscheidung, die ein Test erzwungen hat — Ausrücken nimmt die Nachfolger mit.**
>
> Ein Item verlässt seine Liste *unten*, also in Lesereihenfolge hinter allem, was noch darin
> steckt. Blieben die nachfolgenden Punkte zurück, stünden sie vor dem Punkt, dem sie folgten:
> `[Zwei, Drei]` ausrücken und das Dokument läse „Drei, Zwei". Sie werden deshalb zur Unterliste
> des ausgerückten Items (verschachtelt) beziehungsweise zu einer neuen Liste hinter den
> herausgelösten Blöcken (oberste Ebene). Das ist auch, was Ein- und Ausrücken zueinander invers
> macht.
>
> **Bewusste Entscheidungen:**
>
> - *Das Teilen ruft die Funktion, nicht den Command.* Der Blocksplit liegt im Rich-Text-Profil
>   und ist über einen Dispatch nicht erreichbar: §10 gibt Command-Handlern einen
>   `TransformScope`, gerade damit sie keine Command-Kette starten können. Der gemeinsame Code
>   wird als Funktion auf dem Entwurf aufgerufen — gleiches Verhalten, keine Kette, und die
>   Beschränkung bleibt intakt statt umgangen.
> - *`adjacentListsJoin` schaut rückwärts.* Der dirty Knoten muss handeln; eine vorwärts
>   schauende Regel würde nur den fragen, der nichts hinter sich hat. Derselbe Fehler wie beim
>   Textlauf-Merge in P12, in derselben Form — und diesmal beim Schreiben erkannt.
> - *Tightness wird getragen, nicht abgeleitet.* Sonst änderte sich das Dokument, sobald jemand
>   einen zweiten Absatz in ein Item schreibt.
> - *`ListSupport` unterdrückt den Absatz eines tight-Items nicht.* Das hieße, ein Kind zu
>   verstecken, und eine `HtmlSemantics` beschreibt einen Knoten ohne seine Kinder mit Absicht
>   (§15.1). Der Unterschied bleibt im Dokument, wo §18.2 ihn will, und zeigt sich beim
>   Markdown-Writer in P18.
> - *Tab-Einrückung in der Demo, nicht als Vertrag.* P13s Risikozeile hält fest, dass sie „eine
>   spätere opt-in Browserentscheidung" bleibt; die Demo bindet sie, das Modul nicht.
>
> Modulvertrag: [ember-list/README.md](ember-list/README.md).

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

> **Abgeschlossen.** Neues Modul `ember-link` (sbt-ID `scalajs-ember-link`, Paket
> `ember.editor.link`), abhängig von Kern und Rich-Text-Profil. Abnahme:
>
> ```
> sbt --server "Test/testOnly *"
> ```
>
> | Suite | Ergebnis |
> | --- | --- |
> | `LinkUrlPolicySpec` | 21 Tests grün |
> | `LinkSpec` | 19 Tests grün |
> | `LinkProjectionSpec` (ember-standard) | 10 Tests grün |
> | Gesamtes Scala-Gate | 619 Tests grün |
>
> **`LinkUrl` ist ein Typ, kein String — und das ist die ganze Abnahme.**
>
> P14 verlangt „URL-Validierung identisch bei Command/Import". Eine Konvention macht daraus ein
> Versprechen, ein Typ eine Tatsache: es gibt keinen Weg, einen `LinkNode` ohne `LinkUrl` zu
> bauen, und keinen, ein `LinkUrl` ohne Policy zu bekommen. Command-Pfad und Importpfad
> **können** nicht auseinanderlaufen, weil es nur eine Tür gibt. Der Importparser aus P24 bekommt
> dieselbe Tür, ohne dass jemand daran denken müsste.
>
> **Die Risikozeile ernst genommen.** „Stringpräfix-Tests allein reichen für normalisierte URLs
> nicht" — `LinkUrlPolicySpec` besteht aus Fällen, die ein Browser ausführt und ein naives
> `startsWith("javascript:")` durchlässt: führender Whitespace, Tab und Zeilenumbruch *im*
> Schema, gemischte Schreibweise, entity-kodierte Schemabuchstaben (`&#106;avascript:`),
> entity-kodierte Trenner (`java&#9;script:`) und protokollrelative Ziele. Die
> Normalisierungsreihenfolge ist deshalb fest und dokumentiert: Entities, dann Whitespace vor dem
> Doppelpunkt, dann Schema-Kleinschreibung — genau die Reihenfolge, die §19.1 für den Importpfad
> vorschreibt.
>
> **Zwei Entscheidungen, die ein Test korrigiert hat:**
>
> 1. *Der Host wird nicht kanonisiert.* Ich hatte erwartet, dass `Example.COM` zu `example.com`
>    wird. Hosts sind zwar case-insensitiv, aber sie zu normalisieren heißt, über Ports, IDN und
>    Userinfo zu entscheiden — halb richtig ist schlechter als gar nicht. Angefasst wird nur das
>    Schema; das ist der sicherheitsrelevante Teil.
> 2. *Ein relatives Ziel, das wie ein Schema beginnt, wird abgewiesen.* `seite:mit:doppelpunkt`
>    *ist* nach RFC 3986 ein Schema. Es als Pfad durchzulassen hieße, dem Autor eine Bedeutung zu
>    unterstellen, die sein Leser nicht sieht. Der Ausweg steht in derselben Norm: `./seite:…`.
>
> **Projektionsfehler, den die Phase gefunden hat — `forget` lief zu früh.**
>
> Entlinken bewegt die Läufe aus dem Link heraus und entfernt ihn im selben Commit.
> `DocumentProjection.apply` nahm entfernte Knoten aber **zuerst** aus dem Index — und damit die
> Gruppe, aus der die Läufe gerade abwandern sollten. `transfer` fand keine Quelle, und die
> Zielgruppe baute die Läufe neu. `forget` steht jetzt am Ende: ein entfernter Container ist
> genau der Ort, aus dem die abwandernden Kinder kommen. Zusammen mit dem `mountNewContainers`
> aus P13 hält §15.1s „Move erhält Node-Identität" jetzt auch dann, wenn Struktur im selben
> Commit entsteht und vergeht.
>
> **`target` und `rel`: die bewusste Voreinstellung ist, nichts zu setzen.**
>
> P14s Abnahme verlangt „External-Link-Attribute bewusst gesetzt" — nicht eine bestimmte Antwort.
> `target="_blank"` ist eine redaktionelle Entscheidung, keine technische: es überschreibt die
> Wahl des Lesers und bricht den Zurück-Knopf, und §16 will, dass die ausgelieferte Fassung das
> ist, was ein Leser erwartet. Eine Anwendung, die es will, wählt `LinkSupport.openingExternally`
> — und bekommt `rel="noopener noreferrer"` automatisch dazu, weil die beiden zusammengehören.
> `mailto:` und `tel:` bekommen nie eines von beiden: sie übergeben an ein anderes Programm, nicht
> an eine andere Seite.
>
> **Bewusste Abgrenzungen:**
>
> - *Die Link-Policy ist nicht die Media-Policy.* §20 gibt Bildern eigene Regeln, und P14s
>   Risikozeile hält beide auseinander. Ein Dokument darf auf eine Seite verlinken, von der es
>   kein Bild lüde.
> - *Caret ohne Auswahl ergibt `Pass`.* Manche Editoren fügen dort die URL als Text ein; das ist
>   eine Entscheidung für eine Oberfläche, nicht für das Modell.
> - *Ein Bereich über mehrere Blöcke wird ein Link je Block.* Ein Link ist inline (§8.2), ein
>   Knoten hat einen Elternteil — über eine Blockgrenze hinweg gibt es keinen gemeinsamen.
> - *Kein Image-Link-Test.* Der Plan verweist ihn ausdrücklich hinter P16, nach P18/P25.
> - *Die Demo wählt im Modell aus.* `SetLink` braucht eine Auswahl, eine DOM-Auswahl gibt es aber
>   erst mit P21. Der Link-Knopf legt deshalb den ganzen Lauf am Caret in einen Link — in *einer*
>   Transaktion, damit ein Undo nicht bloß die Auswahl zurücknimmt.
>
> Modulvertrag: [ember-link/README.md](ember-link/README.md).

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

> **Abgeschlossen.** Neues Modul `ember-code` (sbt-ID `scalajs-ember-code`, Paket
> `ember.editor.code`), abhängig von Kern und Rich-Text-Profil. Abnahme:
>
> ```
> sbt --server "Test/testOnly *"
> ```
>
> | Suite | Ergebnis |
> | --- | --- |
> | `CodeSpec` | 33 Tests grün |
> | `CodeProjectionSpec` (ember-standard) | 10 Tests grün |
> | Gesamtes Scala-Gate | 662 Tests grün |
>
> **Kein Highlighter, und der Vertrag, der ihn später erlaubt.** Die Abnahme verbietet ihn als
> Produktionsabhängigkeit; die Risikozeile sagt, warum es dabei bleiben muss: „Sichtbares
> Highlighting darf später keine persistente Mark-Zerlegung jeder Codezeile erzwingen." Was
> dieses Modul stattdessen garantiert, ist genau das, was ein Highlighter und ein
> Markdown-Fence brauchen — der Inhalt wörtlich, einschließlich innerer Leerzeilen, in **einem**
> unmarkierten Lauf. Ein späteres `ember-code-highlighting` färbt in einer Ansicht ein, ohne das
> Dokument anzufassen.
>
> **Derselbe Transform-Fehler zum dritten Mal — diesmal im eigenen Modul, eine Datei nachdem ich
> ihn kommentiert hatte.** Die Kollabierungsregel hing am `CodeBlockNode` und sollte unter
> anderem Marks vom Lauf entfernen. Eine Markänderung berührt aber den **Lauf**, nicht den Block,
> und §3.4 hält fest, dass ein Vorfahr auf dem Pfad kein Transform-Kandidat ist. Die Regel wurde
> nie gefragt. Jetzt sind es zwei: `contentIsOneRun` am Block (Struktur) und `runInCodeIsPlain`
> am Lauf (Marks). Die Merkregel steht im Scaladoc: **die Regel gehört an den Knoten, der sich
> ändert, nicht an den, dem er gehört.** Nach P12 (Textlauf-Merge), P13 (benachbarte Listen) und
> jetzt P15 ist das dieselbe Form in drei Verkleidungen.
>
> **`HtmlShape.Element` bekam innere Tags.** `<pre><code>` ist, was HTML für einen Codeblock hat,
> und beide Hälften verdienen ihren Platz: `pre` erhält den Whitespace, `code` sagt, was der
> Inhalt ist (§16). Sie gehören **einem** Dokumentknoten — sie in zwei aufzuteilen hieße, dem
> Dokument eine Struktur anzudichten, die nur die Darstellung braucht. Dieselbe Form wie
> `TextRun.marks` aus P12 und aus demselben Grund: der äußere Tag trägt die Identität, die
> inneren beschreiben nur. `ContainerElement` baut die Kette, `NodeView.accepts` vergleicht sie
> mit — ein Wechsel der inneren Tags ist eine View-Ersetzung, ein Attributwechsel nicht.
>
> **`class` ist jetzt erlaubt, mit einer Bedingung.** `class="language-scala"` ist die
> Konvention, die jeder Highlighter liest. §19.1 schließt „beliebige CSS-Strings als
> Dokumentformat" aus, und das ist keiner: der Wert entsteht in `CodeSupport` aus einer
> `CodeLanguage`, die bereits auf Whitespace und Backticks geprüft ist. Ein Importparser (P24)
> entscheidet gesondert, was er davon übernimmt.
>
> **Ein Test hat eine Entwurfsentscheidung korrigiert.** Ich hatte alle Kinder eines Codeblocks
> mit `\n` verbunden. Falsch: zwei **Läufe** nebeneinander waren eine Zeile — ein
> Formatierungssplit, ein Inline-Paste —, und ein Umbruch dazwischen wäre einer, den niemand
> getippt hat. Zwei **Blöcke**, die hineinwandern, waren zwei Zeilen. Läufe werden jetzt ohne
> Trenner verbunden, Blöcke mit Umbruch.
>
> **Bewusste Entscheidungen:**
>
> - *Zurück wird eine Zeile ein Absatz.* Ein `\n` in einem Absatzlauf wäre ein Dokument, das kein
>   Renderer richtig zeigt — HTML macht daraus ein Leerzeichen, und der Inhalt änderte still
>   seine Bedeutung. Hard Breaks wären vertretbar; Codezeilen sind aber Zeilen.
> - *Enter auf leerer letzter Zeile verlässt den Block.* Ein Codeblock hat keine Kante, über die
>   ein Caret treten könnte — ohne die Konvention gäbe es keinen Weg hinaus durch Tippen.
> - *Eine Einrückungseinheit sind zwei Leerzeichen, kein Tab.* Ein Tab rendert in der Breite, die
>   der Betrachter des Lesers wählt, und das ist das eine, was Code-Einrückung nicht tun darf.
> - *Ausrücken entfernt nur so viel, wie da ist.* Sonst könnte es einen einzelnen Druck seines
>   Gegenstücks nicht zurücknehmen.
> - *Das `meta` des Info-Strings steht nicht im HTML.* Es ist Werkzeugkonfiguration und gehört in
>   den Fence (§18.2). Im Dokument bleibt es, damit P18 es zurückschreiben kann.
> - *Ein Info-String ohne gültige Sprache ist kein Fehler.* Er ist ein Info-String ohne Sprache,
>   und sein Text bleibt als Meta erhalten.
>
> Modulvertrag: [ember-code/README.md](ember-code/README.md).

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

> **Abgeschlossen.** Neues Modul `ember-image` (sbt-ID `scalajs-ember-image`, Paket
> `ember.editor.image`), abhängig **allein vom Kern**. Abnahme:
>
> ```
> sbt --server "Test/testOnly *"
> ```
>
> | Suite | Ergebnis |
> | --- | --- |
> | `MediaUrlPolicySpec` | 20 Tests grün |
> | `ImageNodeSpec` | 17 Tests grün |
> | `ImageAdapterSpec` (ember-standard) | 19 Tests grün |
> | `LinkUrlPolicySpec` (ember-link) | 22 Tests grün, einer davon neu |
> | Gesamtes Scala-Gate | 719 Tests grün |
> | Browser-Gate | 126 Fälle in Chromium, Firefox und WebKit grün |
>
> **Nur der Kern, und das ist die Aussage.** §6 stellt `image` neben `rich-text`, nicht darauf.
> Ein Bild braucht vom Rich-Text-Profil nichts: keine Marks, keine Kinder, keinen Absatz, in dem
> es stecken müsste. `ImageNodeSpec` baut sich deshalb einen eigenen `BlockNode` — kein Behelf,
> sondern die Probe, dass die Abhängigkeit wirklich fehlt.
>
> **`isWhitespace || isControl` verfehlt genau die Zeichen, die sich am besten verstecken.**
> Beim Schreiben der Media-Policy fiel auf, dass die Obfuskationsprüfung aus P14 eine Lücke
> hatte: Java schließt das geschützte Leerzeichen aus `isWhitespace` ausdrücklich aus, und
> `isControl` deckt nur die Cc-Gruppe ab. Browser ignorieren innerhalb einer URL aber auch
> U+200B, U+FEFF und U+2060 — `java​script:alert(1)` wäre durchgegangen. Das Prädikat deckt
> jetzt Cc, Zs, U+2000–U+206F und U+FEFF ab, **in beiden Modulen**, mit Tests in beiden. Ein
> neues Modul hat einen Fehler im alten gefunden; das ist der Grund, die Regel zweimal zu
> schreiben statt sie zu teilen — aber nicht der Grund, sie auseinanderlaufen zu lassen.
>
> **Dekodieren ist so streng wie der Command.** `ImageJsonSupport.codec(policy)` nimmt dieselbe
> `MediaUrlPolicy` entgegen und ruft dieselbe Prüfung auf. Eine Quelle aus einem JSON-Payload
> ist genau so ungeprüft wie eine aus einem Dialog, und §20 unterscheidet nicht. Ein Dokument
> mit `javascript:`-Quelle dekodiert **nicht** — es ist kein leicht falsches Dokument, sondern
> eines, das nie einen Renderer erreichen darf.
>
> **`width: 0` ist ein Dekodierfehler, kein stillschweigend verworfenes Feld.** §19.2 verlangt
> die Prüfung von Zahlenbereichen. Es wegzuwerfen erzeugte ein Dokument, das vom Payload
> abweicht, ohne es zu sagen, und der nächste Round-Trip verlöre es endgültig.
>
> **Bewusste Entscheidungen:**
>
> - *Kein Transform.* Es gibt keine Invariante zu reparieren: ein Bild hat keine Kinder, die
>   falsch stehen könnten, und seine Felder sind so typisiert, dass ein falsches nicht gebaut
>   werden kann. Das erste Modul seit P12 ohne Normalisierungsregel.
> - *`alt=""` wird geschrieben, nicht weggelassen.* §20 sagt, dass ein dekoratives Bild
>   ausdrücklich leeren Alt-Text verwendet. Das Attribut wegzulassen ließe einen Screenreader
>   stattdessen den Dateinamen vorlesen — der leere Alt-Text **bedeutet** etwas.
> - *`<img>` bleibt ein Void-Element.* `HtmlShape.Element` ohne inneren Tag und ohne Kinder;
>   `</img>` steht in keiner Ausgabe.
> - *`mailto:` und `tel:` sind gute Links und keine Bilder.* Der Unterschied zwischen den beiden
>   Policies in einer Zeile — und der Grund, warum §20 ihnen eigene Regeln gibt.
> - *Kein Picker, kein Upload, kein `AbortSignal`.* §20 legt alle drei in einen
>   Anwendungsservice. Was hier ankommt, ist eine fertige `MediaReference`; ein Bild einzufügen
>   ist damit eine gewöhnliche Dokumentänderung mit genau einer History-Stufe.
> - *Die Demo zeigt eine echte Datei unter einem relativen Pfad* (`/ember.svg`). Das ist der
>   Fall, den `allowRelative` abdeckt, und er kommt ohne fremden Host aus. Eine `data:`-URL wäre
>   bequemer und wird von derselben Policy abgewiesen.
>
> **Nebenbei repariert:** die letzten drei Dateien mit der Wort-für-Wort-Ersetzung aus dem
> Codex-Zwischenfall (`DemoSession.scala`, `DemoApp.scala`, `core/Transform.scala`). Nur Prosa,
> keine Codezeile.
>
> Modulvertrag: [ember-image/README.md](ember-image/README.md).

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

> **Vorlage vorhanden.** Im Nachbarverzeichnis `../commonmark.js` liegt die
> Referenzimplementierung, CommonMark **0.31.2**, vom Nutzer ausdrücklich für diesen Zweck
> bereitgestellt. Verwertbar sind:
>
> | Datei | Wofür |
> | --- | --- |
> | `lib/blocks.js` (1016 Z.) | Blockparser samt Container- und Lazy-Continuation-Regeln — die „echte Parserarbeit" aus der Risikozeile |
> | `lib/inlines.js` (1074 Z.) | Delimiter- und Bracket-Stacks — P18 |
> | `lib/node.js`, `lib/common.js` | Baum, Entities, Normalisierung |
> | `test/spec.txt` | die offizielle Konformitätssuite, **652 Beispiele** — direkt als versionierte Fixtures verwendbar, und damit ist die „Korpus-/Spezifikationsversion" der Abnahme eine Zahl und keine Behauptung |
>
> **Keine 1:1-Übersetzung.** Der Zielbaum ist das Ember-Dokumentmodell, nicht der
> commonmark-Knotenbaum, und §18 verlangt Größen-, Tiefen-, Token- und Arbeitsschrittlimits, die
> die Vorlage nicht kennt — die Risikozeile „keine katastrophale Regex-Laufzeit" trifft
> commonmark.js selbst. Übernommen wird die Struktur der Regeln, nicht der Code.
>
> **Lizenz:** BSD-2-Clause, Copyright (c) 2014 John MacFarlane. Eine Portierung ist eine
> abgeleitete Arbeit; Copyright-Notiz und Lizenztext gehören in das Modul, das sie enthält.

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
