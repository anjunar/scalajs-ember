import sbt.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.jdk.CollectionConverters.*

/** Macht aus `spec.txt` Scala-Quelltext.
  *
  * ==Warum nicht zur Laufzeit lesen==
  *
  * Weil es zur Laufzeit nichts zu lesen gibt. Die Tests laufen als Scala.js-Modul: kein
  * Dateisystem, kein Classpath, kein `getResourceAsStream`. Ein `src/test/resources` existiert
  * fuer diesen Build ueberhaupt nur als Ablage -- der Linker sieht es nie.
  *
  * Also wird die Konformitaetssuite zur Bauzeit in eine Scala-Datei uebersetzt. Der Nebeneffekt
  * ist erwuenscht: die Fixtures sind '''versioniert''', im Wortsinn. Die Spezifikationsversion
  * steht im Dateinamen, die Beispielzahl faellt beim Erzeugen an, und beides landet als
  * Konstante im generierten Code. Die "Korpus-/Spezifikationsversion" aus P17s Abnahme ist
  * damit eine Zahl, die der Build ausrechnet, und keine Behauptung in einem Kommentar.
  *
  * ==Das Format von spec.txt==
  *
  * Beispiele stehen zwischen einer Zeile aus mindestens 32 Backticks mit `example` und einer
  * schliessenden Zeile derselben Art. Quelle und erwartete Ausgabe trennt ein `.`. Ein `→`
  * (U+2192) steht fuer ein Tabulatorzeichen -- die Spezifikation macht Tabs sichtbar, damit man
  * sie im Fliesstext sieht.
  */
object MarkdownSpecFixtures {

  private val Fence = "`" * 32

  final case class Example(number: Int, section: String, markdown: String, html: String)

  def generate(specFile: File, target: File, log: Logger): Seq[File] = {
    if (!specFile.isFile)
      sys.error(
        s"Die Konformitaetsfixtures fehlen: ${specFile.getAbsolutePath}\n" +
          "Erwartet wird eine Kopie von commonmark.js/test/spec.txt."
      )

    val examples = parse(readText(specFile))
    val out      = target / "ember" / "editor" / "markdown" / "SpecFixtures.scala"

    val rendered = render(examples, version(specFile.getName))

    // Nur schreiben, wenn sich etwas geaendert hat: sbt haengt `compile` an dieses Ergebnis,
    // und eine bei jedem Lauf neu geschriebene Datei kostet jedes Mal einen Recompile.
    val unchanged = out.isFile && readText(out) == rendered
    if (!unchanged) {
      Files.createDirectories(out.toPath.getParent)
      Files.write(out.toPath, rendered.getBytes(StandardCharsets.UTF_8))
      log.info(s"CommonMark-Fixtures: ${examples.size} Beispiele -> ${out.getName}")
    }

    Seq(out)
  }

  /** `spec-0.31.2.txt` -> `0.31.2`. Die Version steht im Dateinamen, damit ein Wechsel der
    * Spezifikation eine sichtbare Umbenennung ist und keine stille Ersetzung.
    */
  private def version(fileName: String): String =
    fileName.stripPrefix("spec-").stripSuffix(".txt")

  private def parse(spec: String): Vector[Example] = {
    val lines   = spec.split("\r\n|\n|\r", -1).toVector
    val builder = Vector.newBuilder[Example]

    var index   = 0
    var section = ""
    var number  = 0

    while (index < lines.length) {
      val line = lines(index)

      if (line.startsWith("#")) {
        section = line.dropWhile(_ == '#').trim
        index += 1
      } else if (line.startsWith(Fence) && line.contains("example")) {
        val markdown = Vector.newBuilder[String]
        val html     = Vector.newBuilder[String]

        index += 1
        while (index < lines.length && lines(index) != ".") {
          markdown += lines(index)
          index += 1
        }
        index += 1 // das trennende "."
        while (index < lines.length && !lines(index).startsWith(Fence)) {
          html += lines(index)
          index += 1
        }
        index += 1 // die schliessende Zaunzeile

        number += 1
        builder += Example(
          number,
          section,
          detab(joinLines(markdown.result())),
          detab(joinLines(html.result()))
        )
      } else index += 1
    }

    builder.result()
  }

  /** Jede Zeile endet mit `\n`, auch die letzte -- so steht es in spec.txt. */
  private def joinLines(lines: Vector[String]): String =
    if (lines.isEmpty) "" else lines.mkString("", "\n", "\n")

  /** `→` ist in spec.txt die sichtbare Schreibweise fuer einen Tabulator. */
  private def detab(text: String): String = text.replace('→', '\t')

  private def readText(file: File): String =
    new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8)

  private def render(examples: Vector[Example], specVersion: String): String = {
    val entries = examples
      .map { example =>
        "    SpecExample(" + example.number + ", " + quote(example.section) + ", " +
          quote(example.markdown) + ", " + quote(example.html) + ")"
      }
      .mkString(",\n")

    s"""package ember.editor.markdown
       |
       |// ERZEUGT -- nicht von Hand aendern.
       |// Quelle: ember-markdown/src/test/resources/markdown/spec-$specVersion.txt
       |// Erzeuger: project/MarkdownSpecFixtures.scala
       |
       |/** Ein Beispiel aus der CommonMark-Konformitaetssuite. */
       |final case class SpecExample(
       |    number: Int,
       |    section: String,
       |    markdown: String,
       |    html: String
       |)
       |
       |/** Die Konformitaetssuite als Scala-Werte -- siehe `project/MarkdownSpecFixtures.scala`. */
       |object SpecFixtures:
       |
       |  /** Die Spezifikationsversion, aus dem Dateinamen der Fixtures. */
       |  val specVersion: String = ${quote(specVersion)}
       |
       |  val all: Vector[SpecExample] = Vector(
       |$entries
       |  )
       |""".stripMargin
  }

  /** Escaping fuer ein Scala-String-Literal.
    *
    * Von Hand und nicht ueber eine Bibliothek, weil genau drei Dinge zaehlen und jedes davon
    * hier vorkommt: Backslash, Anfuehrungszeichen und Zeilenumbruch. `$` muss ebenfalls weg --
    * die erzeugte Datei enthaelt keine Interpolatoren, aber ein `s"..."` im erzeugenden Code
    * hat schon einmal jemanden erwischt.
    */
  private def quote(text: String): String = {
    val escaped = new StringBuilder("\"")
    text.foreach {
      case '\\' => escaped.append("\\\\")
      case '"'  => escaped.append("\\\"")
      case '\n' => escaped.append("\\n")
      case '\r' => escaped.append("\\r")
      case '\t' => escaped.append("\\t")
      case '$'  => escaped.append("$")
      case c if c < ' ' => escaped.append("\\u%04x".format(c.toInt))
      case c    => escaped.append(c)
    }
    escaped.append('"').toString
  }
}
