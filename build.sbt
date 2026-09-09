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

lazy val coreBoundarySettings = Seq(
  boundaryCheck := Def.uncached {
    val log = streams.value.log
    val moduleId = thisProject.value.id

    val failure = EditorBoundary.report(
      moduleName = moduleId,
      projectDependencies = thisProject.value.dependencies.map(_.project.project),
      // `ember-core` haengt laut Architektur §6 an nichts ausser der Standardbibliothek.
      allowedProjects = Seq.empty,
      resolvedModules = update.value.allModules.map(module => s"${module.organization}:${module.name}"),
      forbiddenModules = Seq("scalajs-dom", "scalajs-jfx", "scalajs-lexical"),
      importViolations = EditorBoundary.scanImports(
        (Compile / unmanagedSourceDirectories).value ++ (Test / unmanagedSourceDirectories).value,
        Seq(
          "org.scalajs.dom",
          "jfx.core",
          "jfx.forms",
          "jfx.controls",
          "jfx.viewport",
          "jfx.router",
          "jfx.json",
          "jfx.bridge",
          // Der Core darf nicht zurueck in die Module greifen, die auf ihm aufbauen.
          // Diese Pakete existieren noch nicht; die Regel steht trotzdem jetzt schon.
          "ember.editor.jfx",
          "ember.editor.browser",
          "ember.editor.forms",
          "ember.editor.ui",
          "ember.editor.html",
          "ember.editor.markdown",
          "ember.editor.json"
        )
      )
    )

    failure.foreach(sys.error)
    log.debug(s"$moduleId: Abhaengigkeitsgrenze eingehalten.")
  },
  Compile / sources := (Compile / sources).dependsOn(boundaryCheck).value
)

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
  .settings(coreBoundarySettings)

lazy val root = Project(id = "scalajs-ember-root", base = file("."))
  .aggregate(emberCore)
  .settings(
    name           := "scalajs-ember",
    publish / skip := true
  )
