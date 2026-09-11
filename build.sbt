import org.scalajs.linker.interface.{ESVersion, ModuleKind}
import org.scalajs.sbtplugin.ScalaJSPlugin

version      := "0.1.0-SNAPSHOT"
organization := "com.anjunar"
scalaVersion := "3.3.8"

// Avoids locked inter-project jars on Windows when the persistent sbt server is used.
// Fuer die Meta-Build gilt dasselbe eine Ebene hoeher, siehe project/build.sbt.
exportJars := false

// ---------------------------------------------------------------------------
// sbt 2: blanke Settings sind *common settings* und werden in jedes Subprojekt
// injiziert. Das ist hier keine Stilfrage, sondern die konkrete Gefahr aus P01:
// ein `libraryDependencies += "org.scala-js" %% "scalajs-dom"` auf oberster Ebene
// wuerde den browserfreien Core mitvergiften, ohne dass es irgendwo sichtbar waere.
// Deshalb steht unten *keine* Abhaengigkeit blank -- jede haengt an einem
// benannten Settings-Block, den ein Projekt bewusst waehlt.
//
// Namenskonvention, entsprechend scalajs-ui: Verzeichnis `ember-<modul>`, sbt-ID und
// Artefakt `scalajs-ember-<modul>`, Scala-Paket `ember.editor.<modul>`. Die Modultabelle
// steht in UI_EDITOR_ARCHITECTURE.md §6; angelegt wird ein Modul erst in der Phase,
// die es tatsaechlich braucht -- keine leeren Platzhalterprojekte.
// ---------------------------------------------------------------------------

lazy val commonJsSettings = Seq(
  scalaJSLinkerConfig := scalaJSLinkerConfig.value
    .withModuleKind(ModuleKind.ESModule)
    .withESFeatures(_.withESVersion(ESVersion.ES2021))
    .withSourceMap(true)
)

// Nur ScalaTest, Test-Scope. Landet nicht im POM der Kompilierabhaengigkeiten.
lazy val testSettings = Seq(
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

// Ausschliesslich fuer Module, die `org.scalajs.dom` selbst importieren -- bislang nur der
// Harness. `ember-ui` steht bewusst nicht hier: es spricht ausschliesslich ui-core-Typen an
// und bekommt scalajs-dom transitiv, ohne selbst eine Meinung dazu zu haben.
// `ember-core` waehlt diesen Block ebenfalls nicht, und dort ist es eine Grenze (§7).
lazy val domSettings = Seq(
  libraryDependencies += "org.scala-js" %% "scalajs-dom" % "2.8.1"
)

lazy val publishSettings = Seq(
  // Leerer Doc-Jar: Maven Central verlangt, dass das Artefakt existiert, nicht dass
  // Inhalt drin ist. Gleiche Entscheidung wie in scalajs-ui.
  Compile / doc / sources := Seq.empty,
  publishMavenStyle       := true,
  pomIncludeRepository    := { _ => false },
  versionScheme           := Some("early-semver"),
  licenses                := Seq("MIT" -> uri("https://opensource.org/licenses/MIT")),
  homepage                := Some(uri("https://github.com/anjunar/scalajs-ember"))
)

// Binaerabhaengigkeit auf ui-core von Maven Central, seit P17 statt der Quell-Abhaengigkeit
// auf das Nachbarverzeichnis.
//
// Vorher band `ProjectRef(file("../scalajs-ui"), "scalajs-ui-core")` das Submodul direkt aus
// den Quellen ein. Das war richtig, solange ui-core sich unter uns bewegte: der Editor war
// dessen erster ernsthafter Konsument, und jeder Befund dort musste sofort dort behoben werden
// koennen. Der Preis war, dass beide Builds aneinander haengen -- ein halb gespeicherter Stand
// im Nachbarverzeichnis hat diesen Build mehrfach zum Stehen gebracht, ohne dass hier etwas
// falsch war.
//
// 1.0.0 ist abgenommen und veroeffentlicht, also endet die Kopplung. Beide Repos lassen sich
// jetzt parallel bearbeiten, und was hier gebaut wird, haengt an einer Version statt an einem
// Arbeitsverzeichnis. Der Weg zurueck ist eine Zeile, falls ui-core noch einmal im Gleichschritt
// wachsen muss.
//
// `%%` und nicht `%%%`, obwohl das aufgeloeste Artefakt `scalajs-ui-core_sjs1_3` heisst und
// nicht `_3`: in einem Projekt mit aktiviertem `ScalaJSPlugin` setzt das Plugin das
// `sjs1_`-Praefix fuer `CrossVersion.binary` bereits selbst. `%%%` waere hier ausserdem gar
// nicht verfuegbar -- der Operator gehoert sbt-platform-deps und ist auf oberster Ebene einer
// build.sbt nicht im Scope; ihn zu importieren verschattet obendrein `Project`. Und wer das
// Praefix von Hand nachhilft (`.cross(ScalaJSCrossVersion.binary)`), bekommt es zweimal:
// `scalajs-ui-core_sjs1_sjs1_3` gibt es auf Central nicht.
//
// Transitiv kommen `scalajs-dom` 2.8.1 und `com.anjunar:scala-reflect` mit -- derselbe Satz
// wie vorher, nur nicht mehr ueber den Projektgraphen.
//
// Konsumenten sind `ember-ui`, `ember-integration` und `ember-demo`. Fuer `ember-ui` war die
// Publish-Regel aus §6 schon vorher gewahrt (der POM nannte das veroeffentlichte Artefakt);
// jetzt ist sie es ohne Fussnote. `ember-core` und `ember-rich-text` bleiben headless (§7).
lazy val uiCoreSettings = Seq(
  libraryDependencies += "com.anjunar" %% "scalajs-ui-core" % "1.0.0"
)

// Grenze aus Architektur §7 als Compile-Gate.
//
// Warum nicht an `Test / test`: `testOnly` -- der Befehl aus dem Abnahme-Gate -- haengt nicht
// an `test`, ein dortiger Hook wuerde also genau im Ernstfall schweigen.
//
// Warum an `Compile / sources` und nicht an `Compile / compile`: sbt 2 cached Taskergebnisse
// und verlangt dafuer `JsonFormat`/`HashWriter`. `compile` neu zuzuweisen scheitert an
// `JsonFormat[xsbti.compile.CompileAnalysis]`; es ginge nur mit `Def.uncached`, und das haette
// den Compile-Schritt selbst aus dem Action-Cache genommen (gemessen: 87% Cache-Trefferquote).
// Diesen Preis fuer einen Lint zu zahlen waere falsch herum. `sources` liefert dagegen einen
// serialisierbaren Wert, und "pruefe die Quellen, bevor der Compiler sie bekommt" ist fuer
// einen Import-Lint ohnehin die genauere Aussage. `compile` haengt an `sources`, `Test/compile`
// und damit `testOnly` ebenfalls -- der Hook greift auf allen Wegen.
//
// Der Lint selbst ist bewusst uncached: er liest `update` und `thisProject`, deren Werte sbt
// nicht hashen kann, und ein Lint, der wegen eines Cache-Treffers stumm bleibt, ist wertlos.
lazy val boundaryCheck = taskKey[Unit]("Prueft die Abhaengigkeitsgrenze dieses Moduls.")

// Artefakte, die in keinem Editor-Modul etwas zu suchen haben. Die Editor-Module sind
// allesamt headless oder haengen hoechstens an ui-core -- `scalajs-lexical` gehoert zum
// abzuloesenden Prototyp im Nachbar-Repo (Architektur §25).
lazy val forbiddenArtifacts = Seq("scalajs-ui", "scalajs-lexical")

// Pakete, die kein Modul unterhalb der UI-Schicht importieren darf.
lazy val forbiddenJfxImports =
  Seq(
    "org.scalajs.dom",
    "ui.core",
    "ui.forms",
    "ui.controls",
    "ui.viewport",
    "ui.router",
    "ui.json",
    "ui.bridge"
  )

/** Der Grenz-Lint fuer ein Modul.
  *
  * @param allowedProjects
  *   Projekt-IDs dieses Builds, von denen das Modul abhaengen darf. Alles andere ist ein Fehler --
  *   eine Allowlist, weil §6 den Modulgraphen abschliessend aufzaehlt.
  * @param forbiddenImports
  *   Paketpraefixe, die nicht importiert werden duerfen. Enthaelt bewusst auch Pakete, die es noch
  *   gar nicht gibt: die Regel soll stehen, bevor jemand dagegen verstossen kann.
  * @param allowedModules
  *   Artefakte, die trotz Blocklist erlaubt sind. Bislang genau eines: `scalajs-ui-core` in den
  *   drei Modulen, die UI kennen duerfen.
  */
def boundarySettings(
    allowedProjects: Seq[String],
    forbiddenImports: Seq[String],
    forbiddenModules: Seq[String] = forbiddenArtifacts,
    allowedModules: Seq[String] = Seq.empty
): Seq[Setting[?]] = Seq(
  boundaryCheck := Def.uncached {
    val log      = streams.value.log
    val moduleId = thisProject.value.id

    val failure = EditorBoundary.report(
      moduleName = moduleId,
      projectDependencies = thisProject.value.dependencies.map(_.project.project),
      allowedProjects = allowedProjects,
      resolvedModules =
        update.value.allModules.map(module => s"${module.organization}:${module.name}"),
      forbiddenModules = forbiddenModules,
      importViolations = EditorBoundary.scanImports(
        (Compile / unmanagedSourceDirectories).value ++ (Test / unmanagedSourceDirectories).value,
        forbiddenImports
      ),
      allowedModules = allowedModules
    )

    failure.foreach(sys.error)
    log.debug(s"$moduleId: Abhaengigkeitsgrenze eingehalten.")
  },
  Compile / sources := (Compile / sources).dependsOn(boundaryCheck).value
)

// Module, die auf dem Kern aufbauen, duerfen nicht zurueck in die Schichten ueber ihnen
// greifen. Diese Pakete existieren teilweise noch nicht; die Regel steht trotzdem.
lazy val forbiddenUpwardImports =
  Seq("ember.editor.ui", "ember.editor.browser", "ember.editor.forms", "ember.editor.toolbar")

lazy val emberCore = Project(id = "scalajs-ember-core", base = file("ember-core"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name        := "scalajs-ember-core",
    moduleName  := "scalajs-ember-core",
    description := "Headless document model, transactions and commands for the Ember editor."
  )
  .settings(testSettings)
  .settings(commonJsSettings)
  .settings(publishSettings)
  // §6: Der Kern haengt an nichts ausser der Standardbibliothek, und er kennt keines der
  // Module, die auf ihm aufbauen -- auch nicht die Formatmodule.
  .settings(
    boundarySettings(
      allowedProjects = Seq.empty,
      forbiddenImports = forbiddenJfxImports ++ forbiddenUpwardImports ++
        Seq(
          "ember.editor.richtext",
          "ember.editor.html",
          "ember.editor.markdown",
          "ember.editor.json"
        ),
      forbiddenModules = forbiddenArtifacts :+ "scalajs-dom"
    )
  )

lazy val emberRichText =
  Project(id = "scalajs-ember-rich-text", base = file("ember-rich-text"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore)
    .settings(
      name        := "scalajs-ember-rich-text",
      moduleName  := "scalajs-ember-rich-text",
      description := "Paragraphs, marks and text editing semantics for the Ember editor."
    )
    .settings(testSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    // §6: rich-text haengt ausschliesslich am Kern. Ebenfalls headless -- die
    // Unicode-Grenzen sind reines Scala, kein `Intl.Segmenter`.
    .settings(
      boundarySettings(
        allowedProjects = Seq("scalajs-ember-core"),
        forbiddenImports = forbiddenJfxImports ++ forbiddenUpwardImports,
        forbiddenModules = forbiddenArtifacts :+ "scalajs-dom"
      )
    )

// §6: List/ListItem, Ein-/Ausruecken, Listennormalisierung. Haengt am Rich-Text-Profil, weil
// ein ListItem Blockinhalte enthaelt (§8.2) und der haeufigste davon ein Absatz ist.
lazy val emberList =
  Project(id = "scalajs-ember-list", base = file("ember-list"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore, emberRichText)
    .settings(
      name        := "scalajs-ember-list",
      moduleName  := "scalajs-ember-list",
      description := "Ordered and unordered lists with indent, outdent and normalisation."
    )
    .settings(testSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    .settings(
      boundarySettings(
        allowedProjects = Seq("scalajs-ember-core", "scalajs-ember-rich-text"),
        forbiddenImports = forbiddenJfxImports ++ forbiddenUpwardImports ++
          Seq("ember.editor.html", "ember.editor.markdown", "ember.editor.json"),
        forbiddenModules = forbiddenArtifacts :+ "scalajs-dom"
      )
    )

// §6: Image/Media-Referenzen, Atom-Semantik, validierte Quellen und Masse. Haengt allein am
// Kern -- ein Bild ist ein Inline-Atom ohne Kinder und braucht kein Textprofil.
lazy val emberImage =
  Project(id = "scalajs-ember-image", base = file("ember-image"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore)
    .settings(
      name        := "scalajs-ember-image",
      moduleName  := "scalajs-ember-image",
      description := "Reference-based media with a validated source policy for Ember."
    )
    .settings(testSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    .settings(
      boundarySettings(
        allowedProjects = Seq("scalajs-ember-core"),
        forbiddenImports = forbiddenJfxImports ++ forbiddenUpwardImports ++
          Seq(
            "ember.editor.richtext",
            "ember.editor.html",
            "ember.editor.markdown",
            "ember.editor.json"
          ),
        forbiddenModules = forbiddenArtifacts :+ "scalajs-dom"
      )
    )

// §6: CodeBlock, Sprache als Metadatum, Code-Editing. Auf dem Rich-Text-Profil, weil ein
// Codeblock aus einem Absatz entsteht und wieder zu einem wird.
lazy val emberCode =
  Project(id = "scalajs-ember-code", base = file("ember-code"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore, emberRichText)
    .settings(
      name        := "scalajs-ember-code",
      moduleName  := "scalajs-ember-code",
      description := "Code blocks with typed language metadata, independent of highlighting."
    )
    .settings(testSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    .settings(
      boundarySettings(
        allowedProjects = Seq("scalajs-ember-core", "scalajs-ember-rich-text"),
        forbiddenImports = forbiddenJfxImports ++ forbiddenUpwardImports ++
          Seq("ember.editor.html", "ember.editor.markdown", "ember.editor.json"),
        forbiddenModules = forbiddenArtifacts :+ "scalajs-dom"
      )
    )

// §6: LinkNode, Link-Commands und Link-URL-Policy. Wie `list` auf dem Rich-Text-Profil, weil
// ein Link Inline-Inhalte enthaelt (§8.2) -- Textlaeufe, spaeter auch Inline-Atome.
lazy val emberLink =
  Project(id = "scalajs-ember-link", base = file("ember-link"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore, emberRichText)
    .settings(
      name        := "scalajs-ember-link",
      moduleName  := "scalajs-ember-link",
      description := "Typed inline links with a validated URL policy for the Ember editor."
    )
    .settings(testSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    .settings(
      boundarySettings(
        allowedProjects = Seq("scalajs-ember-core", "scalajs-ember-rich-text"),
        forbiddenImports = forbiddenJfxImports ++ forbiddenUpwardImports ++
          Seq("ember.editor.html", "ember.editor.markdown", "ember.editor.json"),
        forbiddenModules = forbiddenArtifacts :+ "scalajs-dom"
      )
    )

// §6: Scala-Syntaxparser, Writer, SourceMap und typisierte AST-Adapter-SPI. Haengt allein am
// Kern, und das ist die Aussage: der Parser baut einen Syntaxbaum, kein Dokument. Welcher
// NodeType aus einem Heading wird, entscheidet `standard` (§18.1) -- deshalb kennt dieses
// Modul weder `rich-text` noch `list`, `link`, `code` oder `image`.
//
// Enthaelt eine Portierung von commonmark.js (BSD-2-Clause, Copyright (c) 2014 John
// MacFarlane); der Lizenztext steht in `ember-markdown/NOTICE`.
lazy val emberMarkdown =
  Project(id = "scalajs-ember-markdown", base = file("ember-markdown"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore)
    .settings(
      name        := "scalajs-ember-markdown",
      moduleName  := "scalajs-ember-markdown",
      description := "CommonMark block parser, syntax AST and source map for the Ember editor."
    )
    .settings(testSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    // Die Konformitaetsfixtures werden zu Scala-Quelltext erzeugt, statt zur Laufzeit gelesen:
    // ein Scala.js-Test hat kein Dateisystem und keinen Classpath (siehe `SpecFixtures`).
    .settings(
      Test / sourceGenerators += Def.task {
        MarkdownSpecFixtures.generate(
          specFile = (Test / resourceDirectory).value / "markdown" / "spec-0.31.2.txt",
          target = (Test / sourceManaged).value,
          log = streams.value.log
        )
      }.taskValue
    )
    .settings(
      boundarySettings(
        allowedProjects = Seq("scalajs-ember-core"),
        forbiddenImports = forbiddenJfxImports ++ forbiddenUpwardImports ++
          Seq(
            "ember.editor.richtext",
            "ember.editor.list",
            "ember.editor.link",
            "ember.editor.code",
            "ember.editor.image",
            "ember.editor.html",
            "ember.editor.json"
          ),
        forbiddenModules = forbiddenArtifacts :+ "scalajs-dom"
      )
    )

// §6: Input, SelectionPort, Composition, Mutationen, Fokus und Hydration-Aktivierung.
// P20 baut davon die Hydration; Eingabe und Selection sind P21 bis P23.
//
// §6 haelt ausdruecklich fest, dass es keine Rueckkante `browser → forms` gibt: das
// Formularmodul konsumiert die Hydration, nicht umgekehrt. Deshalb kennt dieses Modul weder
// `ember.editor.forms` noch `ember.editor.ui`, und der Lint haelt beides nach.
lazy val emberBrowser =
  Project(id = "scalajs-ember-browser", base = file("ember-browser"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore, emberRichText, emberHtml, emberUi)
    .settings(
      name        := "scalajs-ember-browser",
      moduleName  := "scalajs-ember-browser",
      description := "Hydration activation, input and selection for the Ember editor."
    )
    .settings(testSettings)
    .settings(domSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    .settings(
      boundarySettings(
        allowedProjects = Seq(
          "scalajs-ember-core",
          "scalajs-ember-rich-text",
          "scalajs-ember-html",
          "scalajs-ember-ui"
        ),
        // Der Browser ist hier der Sinn der Sache, die Projektion ebenfalls. Verboten bleiben
        // `forms` und `ui` -- die liegen darueber (§6).
        forbiddenImports = forbiddenUpwardImports
          .filterNot(name => name == "ember.editor.ui" || name == "ember.editor.browser"),
        allowedModules = Seq("scalajs-ui-core")
      )
    )

// §6: Die konkrete Verdrahtung der Features an die Browsereingabe. P22.
//
// Getrennt von `browser`, weil `browser` die Features nicht kennen darf (§7): eine
// Eingabeabsicht ist Browservokabular, welches Command daraus wird, weiss erst dieses Modul.
// Damit bleibt `browser` in einem Profil ohne Listen genauso brauchbar -- es gibt dann einfach
// keine Bindung fuer `insertUnorderedList`, und das Ereignis bleibt nativ.
lazy val emberBrowserSupport =
  Project(id = "scalajs-ember-browser-support", base = file("ember-browser-support"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(
      emberCore,
      emberRichText,
      emberList,
      emberLink,
      emberCode,
      emberHistory,
      emberUi,
      emberBrowser
    )
    .settings(
      name        := "scalajs-ember-browser-support",
      moduleName  := "scalajs-ember-browser-support",
      description := "Concrete input and keyboard bindings for the Ember editor features."
    )
    .settings(testSettings)
    .settings(domSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    // Wie bei `standard`: dieses Modul liegt *ueber* `browser` und ist genau der Ort, an dem
    // Browserabsichten auf Feature-Commands treffen. `forms` und `ui` bleiben verboten.
    .settings(
      boundarySettings(
        allowedProjects = Seq(
          "scalajs-ember-core",
          "scalajs-ember-rich-text",
          "scalajs-ember-list",
          "scalajs-ember-link",
          "scalajs-ember-code",
          "scalajs-ember-history",
          "scalajs-ember-ui",
          "scalajs-ember-browser"
        ),
        forbiddenImports = forbiddenUpwardImports
          .filterNot(name => name == "ember.editor.ui" || name == "ember.editor.browser"),
        allowedModules = Seq("scalajs-ui-core")
      )
    )

// §6: Markdown-/JSON-Feld, Textarea-Fallback, Submit/Reset, Media-Service-Port und
// Multipart-Vertrag. P19b baut davon den Feldteil; der Media-Service ist P26.
//
// §6 fuehrt ausserdem `browser` und `ui-forms` als Abhaengigkeiten. Beide fehlen hier mit
// Absicht: `ember-browser` gibt es erst ab P20, und die Textarea kommt aus ui-core (P19a).
// Ein Modul auf Vorrat einzubinden waere eine Abhaengigkeit ohne Nutzer.
lazy val emberForms =
  Project(id = "scalajs-ember-forms", base = file("ember-forms"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore, emberMarkdown, emberJson, emberHtml, emberUi, emberBrowser)
    .settings(
      name        := "scalajs-ember-forms",
      moduleName  := "scalajs-ember-forms",
      description := "Form fields, source drafts and the no-JavaScript fallback for Ember."
    )
    .settings(testSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    .settings(
      boundarySettings(
        allowedProjects = Seq(
          "scalajs-ember-core",
          "scalajs-ember-markdown",
          "scalajs-ember-json",
          "scalajs-ember-html",
          "scalajs-ember-ui",
          "scalajs-ember-browser"
        ),
        // `ui` und `browser` fehlen hier mit Absicht: ein Formularfeld traegt eine Vorschau
        // (eine DocumentView) und aktiviert sie per Hydration (P20). `ui` bleibt verboten.
        forbiddenImports = forbiddenUpwardImports
          .filterNot(name => name == "ember.editor.ui" || name == "ember.editor.browser"),
        allowedModules = Seq("scalajs-ui-core")
      )
    )

// §6: Wire-ADT, Node-Codecs, Schema-/Dokumentversionen, Validierung. Haengt nur am Kern --
// Persistenz ist keine Frage des Renderers, und ein Server, der Dokumente speichert, soll
// weder UI noch HTML mitlinken muessen.
lazy val emberJson =
  Project(id = "scalajs-ember-json", base = file("ember-json"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore)
    .settings(
      name        := "scalajs-ember-json",
      moduleName  := "scalajs-ember-json",
      description := "Versioned JSON wire format, node codecs and schema migration for Ember."
    )
    .settings(testSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    .settings(
      boundarySettings(
        allowedProjects = Seq("scalajs-ember-core"),
        forbiddenImports = forbiddenJfxImports ++ forbiddenUpwardImports ++
          Seq("ember.editor.richtext", "ember.editor.html", "ember.editor.markdown"),
        forbiddenModules = forbiddenArtifacts :+ "scalajs-dom"
      )
    )

// §6: Undo/Redo, Gruppierung, Limits, History-Commands. Haengt nur am Kern -- §14 fuehrt das
// Modul als "headless und optional", und eine Anwendung ohne Undo linkt es nicht mit.
lazy val emberHistory =
  Project(id = "scalajs-ember-history", base = file("ember-history"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore)
    .settings(
      name        := "scalajs-ember-history",
      moduleName  := "scalajs-ember-history",
      description := "Deterministic undo and redo with explicit grouping rules for Ember."
    )
    .settings(testSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    .settings(
      boundarySettings(
        allowedProjects = Seq("scalajs-ember-core"),
        forbiddenImports = forbiddenJfxImports ++ forbiddenUpwardImports ++
          Seq("ember.editor.html", "ember.editor.markdown", "ember.editor.json"),
        forbiddenModules = forbiddenArtifacts :+ "scalajs-dom"
      )
    )

// Zunaechst nur die Semantik-SPI. Parser und Importregeln folgen mit P24; §19.1 haelt fest,
// dass `HtmlFragment` dabei keine eigene Update-/Diff-Laufzeit bekommt -- es ist eine
// Beschreibung, kein View-Baum.
lazy val emberHtml =
  Project(id = "scalajs-ember-html", base = file("ember-html"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore)
    .settings(
      name        := "scalajs-ember-html",
      moduleName  := "scalajs-ember-html",
      description := "Semantic HTML contract and fragment representation for the Ember editor."
    )
    .settings(testSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    .settings(
      boundarySettings(
        allowedProjects = Seq("scalajs-ember-core"),
        forbiddenImports = forbiddenJfxImports ++ forbiddenUpwardImports,
        forbiddenModules = forbiddenArtifacts :+ "scalajs-dom"
      )
    )

// Das einzige publizierte Modul, das UI kennt. Es haengt an `ui-core` -- einem auf Maven
// Central veroeffentlichten Artefakt --, die Publish-Regel aus §6 ist damit gewahrt: der POM
// zeigt auf `com.anjunar:scalajs-ui-core`, nicht auf ein Verzeichnis.
lazy val emberUi =
  Project(id = "scalajs-ember-ui", base = file("ember-ui"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore, emberHtml)
    .settings(
      name        := "scalajs-ember-ui",
      moduleName  := "scalajs-ember-ui",
      description := "Keyed document projection onto the UI runtime for the Ember editor."
    )
    .settings(testSettings)
    .settings(uiCoreSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    // UI ist hier der Sinn der Sache -- aber nur der Kern. §7 gibt dieser Schicht `ui-core`
    // und sonst nichts; `ui-forms`, `ui-viewport` und der Rest bleiben verboten. Solange
    // ui-core eine Quell-Abhaengigkeit war, sagte das der Projektgraph; jetzt sagt es der Lint.
    .settings(
      boundarySettings(
        allowedProjects = Seq("scalajs-ember-core", "scalajs-ember-html"),
        forbiddenImports = forbiddenUpwardImports,
        allowedModules = Seq("scalajs-ui-core")
      )
    )

// Das Integrationsmodul aus §6: einzeln waehlbare Standardadapter. Es ist der einzige Ort, an
// dem Feature-Nodes und Renderer einander kennen -- deshalb kennen die Node-Module weder UI
// noch HTML, und die Format-SPIs keine konkreten Feature-Nodes.
lazy val emberStandard =
  Project(id = "scalajs-ember-standard", base = file("ember-standard"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(
      emberCore,
      emberRichText,
      emberList,
      emberLink,
      emberCode,
      emberImage,
      emberMarkdown,
      emberJson,
      emberHtml,
      emberUi
    )
    .settings(
      name        := "scalajs-ember-standard",
      moduleName  := "scalajs-ember-standard",
      description := "Selectable standard adapters wiring Ember node types to renderers."
    )
    .settings(testSettings)
    .settings(commonJsSettings)
    .settings(publishSettings)
    // UI ist hier zwangslaeufig sichtbar: `standard` liefert NodeViews, und deren
    // `create` gibt eine UI-Komponente zurueck -- die API von `ember-ui` besteht aus
    // ui-core-Typen. §6 fuehrt `ui` deshalb als Abhaengigkeit von `standard`.
    .settings(
      boundarySettings(
        allowedProjects = Seq(
          "scalajs-ember-core",
          "scalajs-ember-rich-text",
          "scalajs-ember-list",
          "scalajs-ember-link",
          "scalajs-ember-code",
          "scalajs-ember-image",
          "scalajs-ember-markdown",
          "scalajs-ember-json",
          "scalajs-ember-html",
          "scalajs-ember-ui"
        ),
        // `ember.editor.ui` fehlt hier mit Absicht: `standard` liegt in §6 *ueber* dem
        // UI-Modul und ist gerade der Ort, an dem NodeViews entstehen.
        forbiddenImports = forbiddenUpwardImports.filterNot(_ == "ember.editor.ui"),
        allowedModules = Seq("scalajs-ui-core")
      )
    )

// Nicht publiziert: eine Testanwendung, keine Bibliothek. Die Publish-Regel aus Architektur §6
// beruehrt es damit gar nicht erst -- und `ember-ui`, das sie sehr wohl beruehrt, haelt sie
// ein (siehe dort).
//
// Separat gelinkt ist ebenfalls zulaessig (P07, Risiken): die Test-App ist eine isolierte
// Anwendung, keine Bibliothek, und teilt sich mit niemandem eine Runtime.
lazy val emberIntegration =
  Project(id = "scalajs-ember-integration", base = file("ember-integration"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(
      emberCore,
      emberRichText,
      emberMarkdown,
      emberHtml,
      emberUi,
      emberBrowser,
      emberBrowserSupport,
      emberForms,
      emberStandard
    )
    .settings(
      name                            := "scalajs-ember-integration",
      moduleName                      := "scalajs-ember-integration",
      description                     := "Browser harness for the Ember editor. Never published.",
      scalaJSUseMainModuleInitializer := false,
      publish / skip                  := true,
      // Der Harness-Server liest genau hier. `fullLinkJS`, weil die Abnahme gegen die
      // tatsaechlich ausgelieferte Linkerausgabe laufen soll und nicht gegen einen Dev-Build.
      Compile / fullLinkJS / scalaJSLinkerOutputDirectory :=
        (LocalRootProject / baseDirectory).value / "target" / "ember-browser-tests",
      Compile / fastLinkJS / scalaJSLinkerOutputDirectory :=
        (LocalRootProject / baseDirectory).value / "target" / "ember-browser-tests-fast"
    )
    .settings(testSettings)
    .settings(domSettings)
    .settings(uiCoreSettings)
    .settings(commonJsSettings)
    // Hier ist der Browser ausdruecklich erlaubt -- das ist der Sinn des Moduls. Verboten
    // bleiben die Editor-Schichten oberhalb, die es noch gar nicht gibt, der Prototyp im
    // Nachbar-Repo und jedes UI-Modul ausser dem Kern.
    .settings(
      boundarySettings(
        allowedProjects = Seq(
          "scalajs-ember-core",
          "scalajs-ember-rich-text",
          "scalajs-ember-markdown",
          "scalajs-ember-html",
          "scalajs-ember-ui",
          "scalajs-ember-browser",
          "scalajs-ember-browser-support",
          "scalajs-ember-forms",
          "scalajs-ember-standard",
          "scalajs-ember-list",
          "scalajs-ember-link",
          "scalajs-ember-code",
          "scalajs-ember-history",
          "scalajs-ember-image"
        ),
        // Wie bei `standard` fehlt `ember.editor.ui` mit Absicht: der Harness haengt an der
        // Projektion, das ist seit P09 sein Zweck. Seit P19b faehrt er ausserdem das
        // Formularfeld -- der Harness liegt ueber allen Modulen, nicht unter ihnen.
        forbiddenImports = forbiddenUpwardImports.filterNot(name =>
          name == "ember.editor.ui" || name == "ember.editor.forms" ||
            name == "ember.editor.browser"
        ),
        allowedModules = Seq("scalajs-ui-core")
      )
    )

// Die Demo. Nicht publiziert, wie der Harness -- eine Anwendung, keine Bibliothek, und damit
// von der Publish-Regel aus §6 gar nicht erst beruehrt.
//
// Sie ist der erste Konsument, der die Module so zusammensetzt, wie eine Anwendung es taete:
// Kern, Profil, Persistenz, Semantik, Projektion und Adapter zugleich. Was dabei umstaendlich
// ist, ist ein Befund ueber die API, nicht ueber die Demo.
lazy val emberDemo =
  Project(id = "scalajs-ember-demo", base = file("ember-demo"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(
      emberCore,
      emberRichText,
      emberList,
      emberLink,
      emberCode,
      emberImage,
      emberJson,
      emberHistory,
      emberHtml,
      emberUi,
      emberBrowser,
      emberBrowserSupport,
      emberStandard
    )
    .settings(
      name                            := "scalajs-ember-demo",
      moduleName                      := "scalajs-ember-demo",
      description                     := "Runnable demo of the Ember editor. Never published.",
      scalaJSUseMainModuleInitializer := false,
      publish / skip                  := true,
      // Der Dev-Server liest genau hier. `fastLinkJS` fuer die Schleife, `fullLinkJS` fuer
      // einen Blick auf die tatsaechlich ausgelieferte Groesse.
      Compile / fastLinkJS / scalaJSLinkerOutputDirectory :=
        (LocalRootProject / baseDirectory).value / "target" / "ember-demo",
      Compile / fullLinkJS / scalaJSLinkerOutputDirectory :=
        (LocalRootProject / baseDirectory).value / "target" / "ember-demo-full"
    )
    .settings(testSettings)
    .settings(domSettings)
    .settings(uiCoreSettings)
    .settings(commonJsSettings)
    // Der Browser ist hier der Sinn der Sache, und die Demo liegt ueber allen Modulen.
    .settings(
      boundarySettings(
        allowedProjects = Seq(
          "scalajs-ember-core",
          "scalajs-ember-rich-text",
          "scalajs-ember-list",
          "scalajs-ember-link",
          "scalajs-ember-code",
          "scalajs-ember-image",
          "scalajs-ember-json",
          "scalajs-ember-history",
          "scalajs-ember-html",
          "scalajs-ember-ui",
          "scalajs-ember-browser",
          "scalajs-ember-browser-support",
          "scalajs-ember-standard"
        ),
        forbiddenImports = forbiddenUpwardImports
          .filterNot(name => name == "ember.editor.ui" || name == "ember.editor.browser"),
        allowedModules = Seq("scalajs-ui-core")
      )
    )

lazy val root = Project(id = "scalajs-ember-root", base = file("."))
  .aggregate(
    emberCore,
    emberRichText,
    emberList,
    emberLink,
    emberCode,
    emberImage,
    emberMarkdown,
    emberJson,
    emberHistory,
    emberHtml,
    emberUi,
    emberBrowser,
    emberBrowserSupport,
    emberForms,
    emberStandard,
    emberIntegration,
    emberDemo
  )
  .settings(
    name           := "scalajs-ember",
    publish / skip := true
  )
