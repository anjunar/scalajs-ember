package ember.editor.richtext

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Graphem- und Wortgrenzen (P06, Architektur §11).
  *
  * Der Risikosatz der Phase lautet: "ein reiner Codepoint-Fallback erfuellt Graphemtests nicht."
  * Die Faelle hier sind genau die, an denen er scheitern wuerde.
  */
final class UnicodeBoundarySpec extends AnyFlatSpec with Matchers {

  private val boundaries = UnicodeTextBoundaries

  private def clustersOf(text: String): Vector[String] =
    val offsets = boundaries.boundariesOf(text)
    offsets.zip(offsets.tail).map((from, to) => text.substring(from, to))

  // ---------------------------------------------------------------------------------------
  // Der einfache Fall
  // ---------------------------------------------------------------------------------------

  "ASCII text" should "break at every offset" in {
    boundaries.boundariesOf("abc") shouldBe Vector(0, 1, 2, 3)
  }

  "Empty text" should "have exactly one boundary" in {
    boundaries.boundariesOf("") shouldBe Vector(0)
  }

  // ---------------------------------------------------------------------------------------
  // Surrogatpaare
  // ---------------------------------------------------------------------------------------

  "A surrogate pair" should "be a single cluster" in {
    // 😀 belegt zwei UTF-16-Einheiten. Ein Backspace, der eine davon entfernt, hinterlaesst
    // einen halben Codepunkt -- einen String, den kein Wire-Format ueberlebt.
    val grin = "😀"

    boundaries.boundariesOf(grin) shouldBe Vector(0, 2)
    boundaries.isGraphemeBoundary(grin, 1) shouldBe false
  }

  it should "be skipped as a whole in both directions" in {
    val text = "a😀b"

    boundaries.previousGraphemeBoundary(text, 3) shouldBe Some(1)
    boundaries.nextGraphemeBoundary(text, 1) shouldBe Some(3)
  }

  // ---------------------------------------------------------------------------------------
  // Kombinierende Zeichen
  // ---------------------------------------------------------------------------------------

  "A combining mark" should "stay with its base character" in {
    // e + COMBINING ACUTE ACCENT. Sieht aus wie ein Zeichen, ist aber zwei Codepunkte.
    val decomposed = "é"

    clustersOf(decomposed) shouldBe Vector("é")
  }

  it should "also hold for a stack of marks" in {
    val stacked = "á̧̈"

    boundaries.boundariesOf(stacked) shouldBe Vector(0, 4)
  }

  it should "not swallow the following character" in {
    clustersOf("éx") shouldBe Vector("é", "x")
  }

  // ---------------------------------------------------------------------------------------
  // ZWJ-Sequenzen -- GB11
  // ---------------------------------------------------------------------------------------

  "A ZWJ emoji sequence" should "be a single cluster" in {
    // Familie: Mann + ZWJ + Frau + ZWJ + Maedchen. Ohne GB11 zerfiele sie in fuenf Teile, und
    // ein Backspace loeschte nur das Maedchen.
    val family = "👨‍👩‍👧"

    boundaries.boundariesOf(family) shouldBe Vector(0, family.length)
  }

  it should "still break between two separate sequences" in {
    val family = "👨‍👩"
    val grin   = "😀"

    clustersOf(family + grin) shouldBe Vector(family, grin)
  }

  it should "not join across a ZWJ that follows something non-pictographic" in {
    // GB11 verlangt `ExtPict Extend* ZWJ x ExtPict`. Steht links kein Piktogramm, gilt die
    // Regel nicht -- der ZWJ haengt dann nur nach GB9 am Vorgaenger.
    val text = "a‍😀"

    clustersOf(text) shouldBe Vector("a‍", "😀")
  }

  "A skin tone modifier" should "stay with its emoji" in {
    val thumb = "👍🏽"

    boundaries.boundariesOf(thumb) shouldBe Vector(0, 4)
  }

  // ---------------------------------------------------------------------------------------
  // Regional Indicators -- GB12, GB13
  // ---------------------------------------------------------------------------------------

  "A flag" should "be a single cluster" in {
    val germany = "🇩🇪"

    boundaries.boundariesOf(germany) shouldBe Vector(0, 4)
  }

  "Two flags" should "break between them, not in the middle of either" in {
    // Der Fall, den nur die Paritaetsregel richtig loest: vier Regional Indicators sind zwei
    // Flaggen, nicht eine oder vier.
    val germany = "🇩🇪"
    val france  = "🇫🇷"

    clustersOf(germany + france) shouldBe Vector(germany, france)
  }

  "An odd number of regional indicators" should "leave the last one on its own" in {
    val threeIndicators = "🇩🇪🇫"

    clustersOf(threeIndicators) should have size 2
  }

  // ---------------------------------------------------------------------------------------
  // Weitere Regeln
  // ---------------------------------------------------------------------------------------

  "CRLF" should "be a single cluster" in {
    // GB3. Zwei Codepunkte, ein Zeilenumbruch -- ein Backspace darf nicht das CR stehen lassen.
    boundaries.boundariesOf("a\r\nb") shouldBe Vector(0, 1, 3, 4)
  }

  "Hangul jamo" should "compose into one cluster" in {
    // Choseong + Jungseong + Jongseong ergeben eine Silbe.
    val han = "한"

    boundaries.boundariesOf(han) shouldBe Vector(0, 3)
  }

  "A control character" should "stand alone" in {
    // GB4/GB5: Steuerzeichen binden sich an nichts.
    clustersOf("a\tb") shouldBe Vector("a", "\t", "b")
  }

  // ---------------------------------------------------------------------------------------
  // Raender und fehlerhafte Offsets
  // ---------------------------------------------------------------------------------------

  "Boundary lookup" should "report nothing beyond the ends" in {
    boundaries.previousGraphemeBoundary("abc", 0) shouldBe None
    boundaries.nextGraphemeBoundary("abc", 3) shouldBe None
    boundaries.previousGraphemeBoundary("", 0) shouldBe None
    boundaries.nextGraphemeBoundary("", 0) shouldBe None
  }

  it should "survive offsets outside the text" in {
    // Ein Aufrufer mit einem veralteten Offset soll eine Antwort bekommen, keinen Absturz.
    boundaries.previousGraphemeBoundary("abc", 99) shouldBe Some(2)
    boundaries.nextGraphemeBoundary("abc", -5) shouldBe Some(1)
    boundaries.isGraphemeBoundary("abc", 99) shouldBe false
    boundaries.isGraphemeBoundary("abc", -1) shouldBe false
  }

  // ---------------------------------------------------------------------------------------
  // Wortgrenzen
  // ---------------------------------------------------------------------------------------

  "Word navigation" should "skip separators and then the word" in {
    val text = "hallo welt"

    boundaries.nextWordBoundary(text, 0) shouldBe Some(5)
    boundaries.nextWordBoundary(text, 5) shouldBe Some(10)
    boundaries.previousWordBoundary(text, 10) shouldBe Some(6)
    boundaries.previousWordBoundary(text, 6) shouldBe Some(0)
  }

  it should "treat digits and underscores as word characters" in {
    boundaries.nextWordBoundary("ab_12 x", 0) shouldBe Some(5)
  }

  it should "keep a combining mark inside its word" in {
    // Zweimal dasselbe Wort, zwei Schreibweisen: einmal vorkomponiertes é (vier Zeichen),
    // einmal zerlegt als e + kombinierender Akzent (fuenf). Die Wortgrenze muss beide Male
    // hinter dem Wort liegen -- die Marke gehoert dazu, sie beendet es nicht.
    boundaries.nextWordBoundary("café x", 0) shouldBe Some(4)
    boundaries.nextWordBoundary("café x", 0) shouldBe Some(5)
  }

  it should "report nothing at the ends" in {
    boundaries.nextWordBoundary("abc", 3) shouldBe None
    boundaries.previousWordBoundary("abc", 0) shouldBe None
  }

  // ---------------------------------------------------------------------------------------
  // Der deklarierte Datenstand
  // ---------------------------------------------------------------------------------------

  "The service" should "declare which Unicode data it segments against" in {
    // §26 fuehrt die Unicode-Datenversion als bewusst offene Entscheidung; P06 verlangt, sie
    // vor der Abnahme festzulegen. Sie steht im Vertrag, damit zwei Implementierungen
    // vergleichbar bleiben -- und damit sichtbar ist, was nicht zugesichert wird.
    boundaries.unicodeVersion should include("16.0.0")
    boundaries.unicodeVersion should include("ohne GB9c")
  }
}
