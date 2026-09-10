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

**Meilenstein A steht, B begonnen** — P01–P07 abgeschlossen, P08–P30 offen. Der frühere
`contenteditable`-Prototyp (`ember.core.Editor` mit `execCommand` und HTML-String als
Zustand) und seine vite-Demo wurden entfernt — Architektur §2 und §25 schließen diesen
Ansatz aus.

`ember-core` trägt den headless Kern unter `ember.editor.core`: Fehlerkonvention,
erzwungene Abhängigkeitsgrenze, das unveränderliche Dokumentmodell mit vollständiger
Strukturvalidierung sowie die primitiven Operationen mit komponierbarer Positionsabbildung.
Dazu Sitzung, atomare Transaktionen, typisierte Zustandsfelder, Commands mit
Prioritäten, Extensions mit Auflösung und Rollback sowie die Transform-Schleife.
`ember-rich-text` setzt darauf das Profil: Absätze, Editing-Semantik und
UAX-29-Graphemgrenzen. Damit lässt sich Text ohne DOM bearbeiten — die Abnahmezeile
von Meilenstein A.

## Module

Konvention: Verzeichnis `ember-<modul>`, sbt-ID und Artefakt `scalajs-ember-<modul>`,
Scala-Paket `ember.editor.<modul>`. Die vollständige Modultabelle steht in
Architektur §6; angelegt wird ein Modul erst in der Phase, die es braucht.

Vorhanden:

- [`ember-core`](ember-core/README.md) — sbt-ID `scalajs-ember-core`, Paket
  `ember.editor.core`. Headless, keine Produktionsabhängigkeiten außer der
  Standardbibliothek. Die Grenze ist als Build-Gate erzwungen (`boundaryCheck`).
- [`ember-rich-text`](ember-rich-text/README.md) — sbt-ID `scalajs-ember-rich-text`,
  Paket `ember.editor.richtext`. Absätze, Editing-Commands, Normalisierung und die
  UAX-29-Graphemgrenzen. Hängt ausschließlich am Kern.
- [`ember-integration`](ember-integration/browser/README.md) — sbt-ID
  `scalajs-ember-integration`. **Nicht publiziert.** Browser-Harness, die die tatsächlich
  gelinkte Anwendung in echten Engines ausführt. Einziges Modul, das an `jfx-core` hängt.

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

Einziger Konsument ist seit P07 `ember-integration` — und das darf es, weil es nie
veröffentlicht wird: die Publish-Regel aus §6 verlangt nur von *veröffentlichten* Modulen,
dass sie ausschließlich auf veröffentlichte Artefakte zeigen. `ember-jfx` in P09 wird das
nicht dürfen und braucht dort einen eigenen, publizierbaren Vertrag.

`ember-core` und `ember-rich-text` bleiben davon unberührt: sie sind headless
(Architektur §7), hängen an nichts aus dem Nachbar-Repo, und ihr Gate läuft ohne es.

## Entwicklung

Voraussetzungen: JDK und sbt. Für die Browser-Harness zusätzlich Node/npm.

```bash
sbt --server "Test/testOnly *"
```

Die Harness läuft getrennt, weil sie den Linkeroutput braucht:

```bash
sbt --server "scalajs-ember-integration/fullLinkJS"
cd ember-integration/browser && npm ci && npm run verify
```

`sbt --server` ist Pflicht: der sbtn-Thin-Client scheitert auf diesem Rechner am
Serverstart. `sbt --server test` delegiert in sbt 2 auf `testQuick` und taugt nicht
als Abnahme-Gate.

Chromium und WebKit sind grün. **Firefox startet auf diesem Rechner nicht** — Playwright
scheitert mit `spawn UNKNOWN`, bevor ein Test läuft. Dasselbe Bild zeigt das Nachbar-Repo;
es ist eine Umgebungsfrage, keine des Codes. Firefox bleibt trotzdem im Standardlauf und in
der CI: ihn herauszunehmen beseitigte das Symptom und den Nachweis gleich mit.
