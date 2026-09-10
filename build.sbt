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
// Namenskonvention, entsprechend scalajs-jfx: Verzeichnis `ember-<modul>`, sbt-ID und
// Artefakt `scalajs-ember-<modul>`, Scala-Paket `ember.editor.<modul>`. Die Modultabelle
// steht in JFX_EDITOR_ARCHITECTURE.md §6; angelegt wird ein Modul erst in der Phase,
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

// Ausschliesslich fuer Module, die tatsaechlich im Browser laufen (ab `ember-jfx`, P09).
// `ember-core` waehlt diesen Block bewusst nicht.
lazy val domSettings = Seq(
  libraryDependencies += "org.scala-js" %% "scalajs-dom" % "2.8.1"
)

lazy val publishSettings = Seq(
  // Leerer Doc-Jar: Maven Central verlangt, dass das Artefakt existiert, nicht dass
  // Inhalt drin ist. Gleiche Entscheidung wie in scalajs-jfx.
  Compile / doc / sources := Seq.empty,
  publishMavenStyle       := true,
  pomIncludeRepository    := { _ => false },
  versionScheme           := Some("early-semver"),
  licenses                := Seq("MIT" -> uri("https://opensource.org/licenses/MIT")),
  homepage                := Some(uri("https://github.com/anjunar/scalajs-ember"))
)

// Direkte Quell-Abhaengigkeit auf das Nachbar-Repo scalajs-jfx, nur das Submodul
// jfx-core. `ProjectRef(file(...), id)` bindet exakt das in dessen build.sbt als
// `Project(id = "scalajs-jfx-core", ...)` definierte Modul ein -- kein
// veroeffentlichtes Artefakt, keine Version, kein Publish-Schritt noetig. Beide
// Builds laufen auf identischem sbt 2.0.8, sbt-scalajs 1.22.0 und Scala 3.3.8,
// die Metabuilds sind also kompatibel.
//
// Bewusst noch an KEIN Modul gehaengt. `ember-core` ist headless (Architektur §7,
// P01), und ein anderes Modul gibt es noch nicht. Erster Konsument wird `ember-jfx`
// in P09 -- dort dann `.dependsOn(jfxCore)`. Bis dahin laedt sbt das Nachbar-Repo
// nicht mit; die Zeile haelt die Verdrahtung dokumentiert und einzeilig verfuegbar.
lazy val jfxCore = ProjectRef(file("../scalajs-jfx"), "scalajs-jfx-core")

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
// allesamt headless oder haengen hoechstens an jfx-core -- `scalajs-lexical` gehoert zum
// abzuloesenden Prototyp im Nachbar-Repo (Architektur §25).
lazy val forbiddenArtifacts = Seq("scalajs-jfx", "scalajs-lexical")

// Pakete, die kein Modul unterhalb der JFX-Schicht importieren darf.
lazy val forbiddenJfxImports =
  Seq("org.scalajs.dom", "jfx.core", "jfx.forms", "jfx.controls", "jfx.viewport", "jfx.router",
      "jfx.json", "jfx.bridge")

/** Der Grenz-Lint fuer ein Modul.
  *
  * @param allowedProjects
  *   Projekt-IDs dieses Builds, von denen das Modul abhaengen darf. Alles andere ist ein Fehler
  *   -- eine Allowlist, weil §6 den Modulgraphen abschliessend aufzaehlt.
  * @param forbiddenImports
  *   Paketpraefixe, die nicht importiert werden duerfen. Enthaelt bewusst auch Pakete, die es
  *   noch gar nicht gibt: die Regel soll stehen, bevor jemand dagegen verstossen kann.
  */
def boundarySettings(
    allowedProjects: Seq[String],
    forbiddenImports: Seq[String],
    forbiddenModules: Seq[String] = forbiddenArtifacts
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
      )
    )

    failure.foreach(sys.error)
    log.debug(s"$moduleId: Abhaengigkeitsgrenze eingehalten.")
  },
  Compile / sources := (Compile / sources).dependsOn(boundaryCheck).value
)

// Module, die auf dem Kern aufbauen, duerfen nicht zurueck in die Schichten ueber ihnen
// greifen. Diese Pakete existieren teilweise noch nicht; die Regel steht trotzdem.
lazy val forbiddenUpwardImports =
  Seq("ember.editor.jfx", "ember.editor.browser", "ember.editor.forms", "ember.editor.ui")

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
        Seq("ember.editor.richtext", "ember.editor.html", "ember.editor.markdown", "ember.editor.json"),
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


// Nicht publiziert, und das ist der Grund, warum dieses Modul ueberhaupt an `jfx-core` haengen
// darf: die Publish-Regel aus Architektur §6 verlangt, dass ein veroeffentlichtes Modul nur auf
// veroeffentlichte Artefakte zeigt. `jfx-core` ist eine Quell-Abhaengigkeit ohne Artefakt --
// eine Integrationsanwendung darf so etwas haben, `ember-jfx` (P09) wird es nicht duerfen und
// bekommt dort einen eigenen, publizierbaren Vertrag.
//
// Separat gelinkt ist ebenfalls zulaessig (P07, Risiken): die Test-App ist eine isolierte
// Anwendung, keine Bibliothek, und teilt sich mit niemandem eine Runtime.
lazy val emberIntegration =
  Project(id = "scalajs-ember-integration", base = file("ember-integration"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(emberCore, emberRichText, jfxCore)
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
    .settings(commonJsSettings)
    // Hier ist der Browser ausdruecklich erlaubt -- das ist der Sinn des Moduls. Verboten
    // bleiben die Editor-Schichten oberhalb, die es noch gar nicht gibt, und der Prototyp im
    // Nachbar-Repo.
    .settings(
      boundarySettings(
        allowedProjects =
          Seq("scalajs-ember-core", "scalajs-ember-rich-text", "scalajs-jfx-core"),
        forbiddenImports = forbiddenUpwardImports,
        forbiddenModules = Seq("scalajs-lexical")
      )
    )

lazy val root = Project(id = "scalajs-ember-root", base = file("."))
  .aggregate(emberCore, emberRichText, emberIntegration)
  .settings(
    name           := "scalajs-ember",
    publish / skip := true
  )
