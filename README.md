# scalajs-ember

Ember ist ein modularer HTML-WYSIWYG-Editor für Scala.js. Er wird als eigenständige
Engine neu entwickelt; Lexical liefert Konzepte und Vergleichsfälle, keine
Laufzeitabhängigkeit.

## Dokumente

| Datei | Inhalt |
| --- | --- |
| [JFX_EDITOR_ARCHITECTURE.md](JFX_EDITOR_ARCHITECTURE.md) | Verbindlicher Architekturentwurf, §1–§26. |
| [JFX_EDITOR_IMPLEMENTATION.md](JFX_EDITOR_IMPLEMENTATION.md) | Ausführbarer Phasenplan P01–P30 plus optionale Folgepakete X01–X03. |
| [JFX_CORE_INTEGRATION.md](JFX_CORE_INTEGRATION.md) | Vertrag der bereits vorhandenen JFX-Core-Editing-Primitive im Nachbar-Repo. |

## Stand

**P01–P03 abgeschlossen**, P04–P30 offen (152 Tests). Der frühere
`contenteditable`-Prototyp (`ember.core.Editor` mit `execCommand` und HTML-String als
Zustand) und seine vite-Demo wurden entfernt — Architektur §2 und §25 schließen diesen
Ansatz aus.

`ember-core` trägt den headless Kern unter `ember.editor.core`: Fehlerkonvention,
erzwungene Abhängigkeitsgrenze, das unveränderliche Dokumentmodell mit vollständiger
Strukturvalidierung sowie die primitiven Operationen mit komponierbarer Positionsabbildung.
Transaktionen und Commands folgen ab P04.

## Module

Konvention: Verzeichnis `ember-<modul>`, sbt-ID und Artefakt `scalajs-ember-<modul>`,
Scala-Paket `ember.editor.<modul>`. Die vollständige Modultabelle steht in
Architektur §6; angelegt wird ein Modul erst in der Phase, die es braucht.

Vorhanden:

- [`ember-core`](ember-core/README.md) — sbt-ID `scalajs-ember-core`, Paket
  `ember.editor.core`. Headless, keine Produktionsabhängigkeiten außer der
  Standardbibliothek. Die Grenze ist als Build-Gate erzwungen (`boundaryCheck`).

## Abhängigkeit auf scalajs-jfx

Die generischen Editing-Primitive (Text-Splices, `Runtime.move`, `KeyedChildren`,
`TextArea`, `HydrationBoundary`, `HostMutationGuard`) liegen im Nachbar-Repo
`../scalajs-jfx` und sind dort implementiert und getestet. Sie werden als
Quell-Abhängigkeit über das Verzeichnis eingebunden, nicht als veröffentlichtes
Artefakt:

```scala
lazy val jfxCore = ProjectRef(file("../scalajs-jfx"), "scalajs-jfx-core")
```

Das Nachbar-Repo muss also ausgecheckt danebenliegen. Beide Builds laufen auf
sbt 2.0.8, sbt-scalajs 1.22.0 und Scala 3.3.8.

Die Kante ist in `build.sbt` definiert, aber bewusst noch an **kein** Modul gehängt:
`ember-core` ist headless (Architektur §7), und ein anderes Modul gibt es noch nicht.
Erster Konsument wird `ember-jfx` in P09. Solange die Kante unbenutzt ist, lädt sbt das
Nachbar-Repo nicht mit — dieser Build ist also von parallelen Arbeiten dort unabhängig.

## Entwicklung

Voraussetzungen: JDK und sbt. Node/npm wird erst mit P07 wieder gebraucht
(Playwright-Harness in `ember-integration`).

```bash
sbt --server "Test/testOnly *"
```

`sbt --server` ist Pflicht: der sbtn-Thin-Client scheitert auf diesem Rechner am
Serverstart. `sbt --server test` delegiert in sbt 2 auf `testQuick` und taugt nicht
als Abnahme-Gate.
