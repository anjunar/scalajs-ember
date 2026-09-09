import sbt.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.jdk.CollectionConverters.*

/** Abhaengigkeitsgrenze der Editor-Module, geprueft im Build statt im Testprozess.
  *
  * Architektur §7 verlangt fuer `ember-core`: kein `org.scalajs.dom`, keine JFX-Property, kein
  * Forms-/Viewport-Import. Ein Scala.js-Test kann das nicht selbst pruefen -- er laeuft im
  * gelinkten Modul und hat weder Dateisystem noch Classpath. Also prueft es der Build.
  *
  * Zwei Ebenen, unterschiedlich stark:
  *
  *   - `forbiddenModules` / `forbiddenProjects` ist die eigentliche Garantie. Was nicht auf dem
  *     Classpath liegt, laesst sich auch voll qualifiziert nicht verwenden; der Compiler faengt
  *     es dann von selbst. Diese Pruefung existiert, damit ein versehentlich hinzugefuegtes
  *     `libraryDependencies +=` oder `dependsOn` sofort und mit klarer Meldung auffaellt,
  *     statt erst durch einen spaeteren, unverstaendlichen Compilefehler.
  *   - `scanImports` ist nur Diagnose-Komfort und faengt zusaetzlich modulinterne Grenzen
  *     (z.B. `ember.editor.browser` in `ember-core`), die auf dem Classpath noch gar nicht
  *     auftauchen koennen, weil das Zielmodul nicht existiert.
  *
  * Die Regeln stehen bewusst im Build und nicht in einer externen Datei: sie gelten pro
  * sbt-Projekt und gehoeren damit neben dessen Definition.
  */
object EditorBoundary {

  final case class ImportViolation(file: File, line: Int, statement: String, forbidden: String) {
    def render: String = s"  ${file.getAbsolutePath}:$line  import $statement  (verboten: $forbidden)"
  }

  /** Sucht `import`-Anweisungen, deren Ziel unter einem verbotenen Paketpraefix liegt.
    *
    * Gematcht wird auf Praefixgrenze, nicht auf Teilstring: `jfx.core` trifft
    * `jfx.core.render.TextNode` und `jfx.core`, aber nicht `ember.jfx.core` oder `jfx.corex`.
    *
    * Erwartet Quellverzeichnisse, nicht den `sources`-Task. Das hat zwei Gruende: der Lint
    * haengt an `Compile / sources`, ihn von dort auch zu lesen waere ein Taskzyklus; und
    * generierte Quellen gehoeren nicht in einen Lint ueber eingecheckten Code.
    */
  def scanImports(sourceDirectories: Seq[File], forbiddenPrefixes: Seq[String]): Seq[ImportViolation] =
    for {
      source <- sourceDirectories.flatMap(scalaFilesUnder)
      (rawLine, index) <- readLines(source).zipWithIndex
      statement <- importTarget(rawLine).toSeq
      prefix <- forbiddenPrefixes
      if statement == prefix || statement.startsWith(prefix + ".")
    } yield ImportViolation(source, index + 1, statement, prefix)

  private def scalaFilesUnder(directory: File): Seq[File] =
    if (!directory.isDirectory) Seq.empty
    else {
      val stream = Files.walk(directory.toPath)
      try
        stream
          .iterator()
          .asScala
          .map(_.toFile)
          .filter(file => file.isFile && file.getName.endsWith(".scala"))
          .toVector
      finally stream.close()
    }

  /** Liefert das Importziel einer Zeile, also `a.b.{C, D}` aus `import a.b.{C, D}`. */
  private def importTarget(rawLine: String): Option[String] = {
    val line = rawLine.trim
    if (!line.startsWith("import ")) None
    else Some(line.stripPrefix("import ").trim)
  }

  private def readLines(source: File): Seq[String] =
    Files.readAllLines(source.toPath, StandardCharsets.UTF_8).asScala.toSeq

  /** Baut die Fehlermeldung, oder `None`, wenn die Grenze eingehalten ist.
    *
    * Projektabhaengigkeiten werden gegen eine Allowlist geprueft, Artefakte gegen eine
    * Blocklist. Das ist Absicht: welche *Module dieses Builds* ein Projekt haben darf, steht
    * vollstaendig in Architektur §6 und ist abschliessend aufzaehlbar; welche *externen
    * Artefakte* es nicht haben darf, ist es nicht.
    */
  def report(
      moduleName: String,
      projectDependencies: Seq[String],
      allowedProjects: Seq[String],
      resolvedModules: Seq[String],
      forbiddenModules: Seq[String],
      importViolations: Seq[ImportViolation]
  ): Option[String] = {
    val badProjects = projectDependencies.filterNot(allowedProjects.contains)
    val badModules  = resolvedModules.filter(module => forbiddenModules.exists(module.contains))

    // Zweimal flach: `section` liefert Option[Seq[String]], also Seq[Option[Seq[String]]].
    // Ein einzelnes `.flatten` liesse Seq[Seq[String]] stehen, und `mkString` haette die
    // inneren Sequenzen als "List(...)" ausgegeben statt als Zeilen.
    val sections: Seq[String] = Seq(
      section("Nicht erlaubte Projektabhaengigkeiten", badProjects.distinct.sorted.map("  " + _)),
      section("Verbotene Artefakte auf dem Classpath", badModules.distinct.sorted.map("  " + _)),
      section("Verbotene Imports", importViolations.map(_.render))
    ).flatten.flatten

    if (sections.isEmpty) None
    else
      Some(
        // Bewusst ASCII: die Meldung geht durch die Windows-Konsole, und ein "Paragraph"-Zeichen
        // kam dort als Ersetzungszeichen an.
        (s"Abhaengigkeitsgrenze von $moduleName verletzt (JFX_EDITOR_ARCHITECTURE.md, Abschnitt 7):" +: sections)
          .mkString("\n")
      )
  }

  private def section(title: String, entries: Seq[String]): Option[Seq[String]] =
    if (entries.isEmpty) None else Some(s"$title:" +: entries)
}
