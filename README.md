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

**Meilenstein A und B stehen, C und D angefangen** — P01–P20 abgeschlossen, P21–P30 offen. Der frühere
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

P11 bringt Undo und Redo. `ember-history` hält strukturell geteilte Snapshots, gruppiert nach
den ausdrücklichen Regeln aus §14 -- zusammenhängendes Tippen verschmilzt, Backspace und Delete
nicht, ein Caretsprung beendet die Gruppe -- und begrenzt sich über Stufenzahl und ein
geschätztes Byte-Budget. Auch dieses Modul hängt allein am Kern und ist optional.

P12 macht aus dem Textprofil ein Rich-Text-Profil: die fünf eingebauten Marks, Bereichs-
formatierung, Überschriften, Zitate und Umbrüche. Getrennte Textläufe wachsen nach dem
Entformatieren wieder zusammen — in derselben Transaktion, also ohne eigenen Undo-Schritt --,
und was die nächste Eingabe formatiert, steht in einem Zustandsfeld statt in der Darstellung.

P13 ergänzt Listen. Ein- und Ausrücken bewegen Knoten, statt sie neu zu bauen -- ein Caret im
dritten Wort steht danach im dritten Wort --, Enter und Backspace bedeuten an Listengrenzen
etwas anderes und treten über §12s Prioritätskette vor die Rich-Text-Handler, und vier
Normalisierungsregeln halten die Struktur legal, egal wer sie verändert hat.

P14 bringt Links. Eine Adresse wird zu einem `LinkUrl`, und den gibt es nur durch die Policy --
Command und Import können deshalb nicht auseinanderlaufen. Was die Policy abweist, sind nicht
nur `javascript:`-Präfixe, sondern auch ihre verschleierten Formen: Steuerzeichen im Schema,
entity-kodierte Buchstaben, protokollrelative Ziele.

P15 bringt Codeblöcke. Die Sprache ist ein Metadatum, kein Highlighter -- was das Modul
garantiert, ist der Inhalt wörtlich in einem unmarkierten Lauf, einschließlich seiner
Leerzeilen. Genau das brauchen sowohl ein späterer Highlighter als auch ein Markdown-Fence.

P16 bringt Bilder, und zwar als Referenz: eine Adresse und eine optionale Kennung, keine
Dateidaten und keine Objekt-URL. Ein Upload ist laut §20 ein Anwendungsservice; was hier
ankommt, ist etwas, das schon existiert. Damit ist ein Bild einzufügen eine gewöhnliche
Dokumentänderung mit einer History-Stufe -- und ein Undo entfernt den Knoten, ohne irgendwo
eine Datei anzufassen. Die Media-Policy ist strenger als die für Links, weil ein Link von einem
Leser gefolgt wird, ein Bild aber von der Seite selbst geladen.

P17 und die erste Hälfte von P18 bringen Markdown. `ember-markdown` parst CommonMark 0.31.2 in
einen Syntaxbaum mit UTF-16-Quellbereichen bis hinunter zum einzelnen Delimiter, und
`MarkdownWriter` schreibt ihn zurück. Wie weit das reicht, ist **gemessen und nicht
behauptet**: 651 der 652 Beispiele der offiziellen Konformitätssuite kommen zeichengenau
heraus, und 621 überleben Schreiben und Neu-Parsen unverändert. Beide Zahlen werden exakt
geprüft — eine Verschlechterung fällt damit ebenso auf wie eine Verbesserung, die jemand
nachzutragen vergisst.

Und mit P18 geht beides auch ins Dokument: eine typisierte SPI bildet Syntax auf registrierte
NodeTypes ab (§18.1), ohne HTML oder DOM als Zwischenstufe. Emphasis wird dabei eine **Mark**
und kein Knoten — `*a*` ist ein Lauf mit einer Eigenschaft, kein Knoten um einen Lauf herum
(§8.2). Was Markdown nicht schreiben kann — Unterstreichung, Bildmaße, ein abgewiesenes
Linkziel —, meldet der Export: unter `Strict` als Fehler, unter `AllowLossy` als Diagnose.
Still verloren geht nichts.

P19 macht den Editor zu einem Formularfeld. Genau eine benannte Textarea trägt den Wert; ohne
JavaScript ist sie sichtbar und normal submitbar, und erst nach erfolgreicher Aktivierung
verschwindet sie hinter der Rich-Ansicht — verborgen, nie `disabled`. Im Quelltextmodus besitzt
ein `SourceDraft` den Wert, und eine Dokumentänderung überschreibt ihn nicht: sie wird
zurückgestellt oder abgewiesen. Die **Formatgrenze liegt vor dem Commit** — ein
`ToggleUnderline` in einem Strict-CommonMark-Feld erzeugt kein Dokument, dessen Formwert
veraltet wäre, sondern gar keines.

P20 nimmt eine schon ausgelieferte Seite in Betrieb. Was darauf stand, bevor das Skript lief —
der getippte Text, die Auswahl samt Richtung, der Fokus —, wird **vor** dem ersten Claim
erfasst; ein Editor-Check vergleicht IDs, Text und die genannten Attribute gegen dieselbe
Semantik, die die Seite gerendert hat. Scheitert er, baut sich nur die Vorschau neu auf: die
Textarea liegt außerhalb dieser Grenze und behält, was jemand hineingeschrieben hat.
Editierbar wird das Feld erst nach erfolgreichem Claim **und** abgeschlossener äußerer
Hydration — und solange eine Eingabesitzung offen sein könnte oder der Quelltext seit dem
Rendern geändert wurde, gar nicht.

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
- [`ember-list`](ember-list/README.md) — sbt-ID `scalajs-ember-list`, Paket
  `ember.editor.list`. Listen, Ein- und Ausrücken, Listennormalisierung. Headless und optional.
- [`ember-link`](ember-link/README.md) — sbt-ID `scalajs-ember-link`, Paket
  `ember.editor.link`. Inline-Links mit geprüfter URL-Policy. Headless und optional.
- [`ember-code`](ember-code/README.md) — sbt-ID `scalajs-ember-code`, Paket
  `ember.editor.code`. Codeblöcke mit typisierten Sprachmetadaten, ohne Highlighter.
- [`ember-image`](ember-image/README.md) — sbt-ID `scalajs-ember-image`, Paket
  `ember.editor.image`. Externe Bilder als Inline-Atome mit geprüfter Media-Policy. Hängt
  allein am Kern — ein Bild braucht vom Rich-Text-Profil nichts.
- [`ember-markdown`](ember-markdown/README.md) — sbt-ID `scalajs-ember-markdown`, Paket
  `ember.editor.markdown`. CommonMark-Parser, -Writer, Syntaxbaum, SourceMaps und die
  Adapter-SPI. Headless, hängt allein am Kern — die Regeln kommen von außen.
- [`ember-forms`](ember-forms/README.md) — sbt-ID `scalajs-ember-forms`, Paket
  `ember.editor.forms`. Das Editorfeld als Formularfeld: eine benannte Textarea, ein
  geschützter Quelltextentwurf und der Weg ohne JavaScript.
- [`ember-json`](ember-json/README.md) — sbt-ID `scalajs-ember-json`, Paket
  `ember.editor.json`. Wire-ADT, Node- und Mark-Codecs, Grenzen, Schema-Migration. Headless.
- [`ember-history`](ember-history/README.md) — sbt-ID `scalajs-ember-history`, Paket
  `ember.editor.history`. Undo/Redo, Gruppierungsregeln, Limits. Headless und optional.
- [`ember-html`](ember-html/README.md) — sbt-ID `scalajs-ember-html`, Paket
  `ember.editor.html`. Der semantische HTML-Vertrag und eine unveränderliche
  Fragmentdarstellung. Headless; der Importparser folgt mit P24.
- [`ember-jfx`](ember-jfx/README.md) — sbt-ID `scalajs-ember-jfx`, Paket
  `ember.editor.jfx`. Die keyed Dokumentansicht auf der JFX-Runtime. Einziges
  **veröffentlichtes** Modul, das JFX kennt.
- [`ember-browser`](ember-browser/README.md) — sbt-ID `scalajs-ember-browser`, Paket
  `ember.editor.browser`. Hydration mit Verlustschutz: Erfassung vor dem Claim, der
  Semantikabgleich und die Aktivierungsentscheidung. Selection und Eingabe folgen mit P21/P22.
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
`../scalajs-jfx` und sind dort implementiert und getestet. Sie kommen seit P17 als
veröffentlichtes Artefakt von Maven Central:

```scala
libraryDependencies += "com.anjunar" %% "scalajs-jfx-core" % "3.0.5"
```

**Das Nachbar-Repo muss also nicht mehr danebenliegen.** Bis P16 war es eine Quell-Abhängigkeit
(`ProjectRef(file("../scalajs-jfx"), "scalajs-jfx-core")`), und das war richtig, solange
jfx-core sich unter dem Editor bewegte — er war dessen erster ernsthafter Konsument, und jeder
Befund musste dort sofort behoben werden können. Der Preis war, dass beide Builds aneinander
hingen: ein halb gespeicherter Stand nebenan hat diesen Build mehrfach zum Stehen gebracht,
ohne dass hier etwas falsch war. Mit 3.0.5 ist der Vertrag abgenommen, also endet die Kopplung
— und der Settings-Graph schrumpft dabei von 34403 auf 18552.

`%%` und nicht `%%%`, obwohl das Artefakt `scalajs-jfx-core_sjs1_3` heißt: in einem Projekt mit
aktiviertem `ScalaJSPlugin` setzt das Plugin das `sjs1_`-Präfix bereits selbst.

Konsumenten sind `ember-jfx`, `ember-standard`, `ember-integration` und `ember-demo`. Für
`ember-jfx` gilt die Publish-Regel aus §6 — ein veröffentlichtes Modul zeigt ausschließlich auf
veröffentlichte Artefakte —, und sie ist gewahrt; nachprüfbar mit
`sbt --server "scalajs-ember-jfx/makePom"`.

**Nur der Kern, und das steht jetzt im Lint.** Die Quell-Abhängigkeit garantierte strukturell,
dass kein weiteres JFX-Modul auf dem Classpath liegt; ein Binärartefakt tut das nicht. Der
Grenz-Lint verbietet deshalb `scalajs-jfx` als Ganzes und gibt über `allowedModules` genau
`scalajs-jfx-core` wieder frei. Ein versehentliches `jfx-forms` bricht den Build mit einer
klaren Meldung, statt still durchzurutschen.

`ember-core` und `ember-rich-text` bleiben davon unberührt: sie sind headless
(Architektur §7), hängen an nichts aus dem Nachbar-Repo, und ihr Gate läuft ohne es.

## Entwicklung

Voraussetzungen: JDK und sbt. Für die Browser-Harness zusätzlich Node/npm.

Kommentare und Scaladoc im Quelltext: **Englisch** (seit dem 10. September 2026). Die Module
bis P11 tragen noch deutsche Kommentare und werden nicht nachträglich umgestellt. Die
Markdown-Dokumente bleiben deutsch.

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
