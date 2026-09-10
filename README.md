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

**Meilenstein A und B stehen, C angefangen** — P01–P10 abgeschlossen, P11–P30 offen. Der frühere
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

Seit P09 gibt es die Ansicht dazu. `ember-html` beschreibt, wie eine Knotenart als HTML
aussieht; `ember-jfx` projiziert das Dokument keyed auf den JFX-Komponentenbaum, ohne
zweiten Renderer und ohne VDOM; `ember-standard` verbindet beide Seiten. Aus **einer**
Beschreibung entstehen die serverseitige Ausgabe und die Editierfläche im Browser, und ein
Textedit schreibt genau einen `characterData`-Eintrag — im Browser mit einem
MutationObserver belegt. Das ist die Abnahmezeile von Meilenstein B.

Mit P10 kommt die Persistenz dazu. `ember-json` schreibt und liest ein versioniertes
Dokumentformat -- mit IDs, mit getrennten Format-, Schema- und Codec-Versionen, mit Grenzen
gegen fremde Payloads und mit reinen Migrationsfunktionen. Es haengt allein am Kern: ein
Server, der Dokumente speichert, linkt weder JFX noch HTML mit.

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
- [`ember-json`](ember-json/README.md) — sbt-ID `scalajs-ember-json`, Paket
  `ember.editor.json`. Wire-ADT, Node- und Mark-Codecs, Grenzen, Schema-Migration. Headless.
- [`ember-html`](ember-html/README.md) — sbt-ID `scalajs-ember-html`, Paket
  `ember.editor.html`. Der semantische HTML-Vertrag und eine unveränderliche
  Fragmentdarstellung. Headless; der Importparser folgt mit P24.
- [`ember-jfx`](ember-jfx/README.md) — sbt-ID `scalajs-ember-jfx`, Paket
  `ember.editor.jfx`. Die keyed Dokumentansicht auf der JFX-Runtime. Einziges
  **veröffentlichtes** Modul, das JFX kennt.
- [`ember-standard`](ember-standard/README.md) — sbt-ID `scalajs-ember-standard`, Paket
  `ember.editor.standard`. Die einzeln wählbaren Standardadapter — der Ort, an dem
  Knotenarten und Renderer einander kennen.
- [`ember-demo`](ember-demo/README.md) — sbt-ID `scalajs-ember-demo`. **Nicht publiziert.**
  Die laufende Demo: Editierfläche links, derselbe Stand als Baum, JSON und HTML rechts.
- [`ember-integration`](ember-integration/browser/README.md) — sbt-ID
  `scalajs-ember-integration`. **Nicht publiziert.** Browser-Harness, die die tatsächlich
  gelinkte Anwendung in echten Engines ausführt.

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

Konsumenten sind `ember-jfx` und `ember-integration`. Für `ember-jfx` gilt die Publish-Regel
aus §6 — ein veröffentlichtes Modul zeigt ausschließlich auf veröffentlichte Artefakte —, und
sie ist gewahrt: der generierte POM nennt `com.anjunar:scalajs-jfx-core_sjs1_3:3.0.4`, nicht
ein Verzeichnis. Nachprüfbar mit `sbt --server "scalajs-ember-jfx/makePom"`. Die
Quell-Abhängigkeit ist eine Sache des Builds, nicht der Veröffentlichung.

`ember-core` und `ember-rich-text` bleiben davon unberührt: sie sind headless
(Architektur §7), hängen an nichts aus dem Nachbar-Repo, und ihr Gate läuft ohne es.

## Entwicklung

Voraussetzungen: JDK und sbt. Für die Browser-Harness zusätzlich Node/npm.

```bash
sbt --server "Test/testOnly *"
```

Die Demo ansehen:

```bash
sbt --server "scalajs-ember-demo/fastLinkJS"
```

```bash
node ember-demo/dev/server.mjs
```

Dann [http://127.0.0.1:4200](http://127.0.0.1:4200). Was dort zu sehen ist -- und was
ausdrücklich noch fehlt -- steht in [ember-demo/README.md](ember-demo/README.md).

Die Harness läuft getrennt, weil sie den Linkeroutput braucht:

```bash
sbt --server "scalajs-ember-integration/fullLinkJS"
cd ember-integration/browser && npm ci && npm run verify
```

`sbt --server` ist Pflicht: der sbtn-Thin-Client scheitert auf diesem Rechner am
Serverstart. `sbt --server test` delegiert in sbt 2 auf `testQuick` und taugt nicht
als Abnahme-Gate.

Chromium, Firefox und WebKit sind grün (126 Fälle, 42 je Engine). Auf diesem Rechner startet der von
Playwright mitgelieferte Firefox allerdings nicht — er verlangt eine private
Side-by-Side-Assembly, die Windows ihm verweigert. Ein regulär installierter Firefox
derselben Version startet einwandfrei, das Problem liegt also im mitgelieferten Build.
Deshalb lokal:

```bash
EMBER_FIREFOX_CHANNEL=moz-firefox npm run test:browser
```

Die vollständige Diagnose steht in
[ember-integration/browser/README.md](ember-integration/browser/README.md).
