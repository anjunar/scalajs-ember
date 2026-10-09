package ember.editor.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

/** Grenztests aus P01.
  *
  * Zwei Dinge werden hier belegt: dass der Kern in einer Umgebung ohne Browserglobals laeuft, und
  * dass die Fehlerkonvention aus `package.scala` das leistet, was die Architektur von ihr verlangt.
  *
  * Die *Abhaengigkeitsgrenze* selbst prueft dieser Test nicht -- das kann er nicht. Ein gelinktes
  * Scala.js-Modul hat weder Classpath noch Dateisystem. Diese Pruefung liegt im Build
  * (`boundaryCheck` in build.sbt, `project/EditorBoundary.scala`) und haengt an
  * `Compile / sources`, laeuft also vor jedem Compile und damit auch vor diesem Test.
  */
final class CoreEnvironmentSpec extends AnyFlatSpec with Matchers {

  // Namen des Global Scope muessen statisch stehen -- Scala.js lehnt `selectDynamic` darauf ab,
  // weil ein nicht existierender Name sonst je nach Ziel ein ReferenceError statt `undefined`
  // waere. `js.typeOf` ist die Form, die auch bei fehlendem Binding nicht wirft.
  "The core test environment" should "not provide a browser window" in {
    // Scala.js testet per Default auf Node.js. Waere hier ein DOM vorhanden, koennte ein
    // versehentlicher Browserzugriff im Kern unbemerkt gruen laufen und erst serverseitig
    // auffallen. Der Test haelt diese Annahme fest, statt sie vorauszusetzen.
    js.typeOf(js.Dynamic.global.window) shouldBe "undefined"
  }

  it should "not provide a document" in {
    js.typeOf(js.Dynamic.global.document) shouldBe "undefined"
  }

  it should "still be a JavaScript runtime" in {
    // Gegenprobe: die beiden Zusicherungen oben sind nur dann aussagekraeftig, wenn ueberhaupt
    // eine JS-Umgebung laeuft. Ohne sie wuerde ein kaputter Testlauf faelschlich gruen wirken.
    js.typeOf(js.Dynamic.global.globalThis) shouldBe "object"
  }

  "The core package" should "load without touching browser globals" in {
    // Initialisierung des Pakets im Node-Prozess: der Zugriff auf `DiagnosticPath.Root`
    // erzwingt, dass das Companion-Objekt geladen wird.
    DiagnosticPath.Root.segments shouldBe empty
  }

  "A diagnostic path" should "render the document root when empty" in {
    DiagnosticPath.Root.render shouldBe "<root>"
  }

  it should "render fields, indices and node ids in order" in {
    val path = DiagnosticPath.field("children").index(2).node("p-17").field("text")

    path.render shouldBe "<root>.children[2]#p-17.text"
  }

  it should "append without mutating the receiver" in {
    val base     = DiagnosticPath.field("children")
    val extended = base.index(0)

    base.render shouldBe "<root>.children"
    extended.render shouldBe "<root>.children[0]"
  }

  it should "compare by value" in {
    DiagnosticPath.field("children").index(1) shouldBe DiagnosticPath.field("children").index(1)
    DiagnosticPath.field("children") should not be DiagnosticPath.field("text")
  }

  "The error convention" should "allow modules outside the core to contribute errors" in {
    // Beleg fuer den offenen Trait aus §8.1: dieser Fehler ist hier lokal definiert und
    // braucht keine Aenderung an `EditorError`. Waere der Trait `sealed`, kompilierte das nicht.
    final case class UnknownNodeType(typeId: String, override val path: DiagnosticPath)
        extends EditorError:
      def message: String = s"Unbekannter Node-Typ `$typeId`."

    val error: EditorError = UnknownNodeType("caption", DiagnosticPath.node("n-4").field("type"))

    error.message shouldBe "Unbekannter Node-Typ `caption`."
    error.path.render shouldBe "<root>#n-4.type"
    error.render shouldBe "<root>#n-4.type: Unbekannter Node-Typ `caption`."
  }

  it should "default to the document root when an error has no location" in {
    final case class NestedUpdate() extends EditorError:
      def message: String = "Verschachteltes update ist nicht erlaubt."

    NestedUpdate().render shouldBe "<root>: Verschachteltes update ist nicht erlaubt."
  }

  it should "carry data rather than a stack trace" in {
    // Ein EditorError ist kein Throwable. Das ist die Zusicherung, die ihn ueber eine
    // Prozess-/Wire-Grenze transportierbar macht.
    final case class Rejected() extends EditorError:
      def message: String = "abgewiesen"

    Rejected() shouldBe a[EditorError]
    Rejected() should not be a[Throwable]
  }

  "A contract violation" should "be thrown rather than returned" in {
    // Gegenstueck zur Regel oben: Aufrufvertragsverletzungen sind keine Werte.
    val thrown = intercept[EditorContractViolation] {
      throw new EditorContractViolation("Das Transaktions-Handle ist abgelaufen.")
    }

    thrown.getMessage shouldBe "Das Transaktions-Handle ist abgelaufen."
    thrown shouldBe an[IllegalStateException]
    thrown should not be an[EditorError]
  }
}
