package ember.editor.richtext

import ember.editor.core.TextBoundaryService

/** Graphem- und Wortgrenzen in reinem Scala.
  *
  * ==Warum das gebraucht wird==
  *
  * Der Kern rechnet in UTF-16 -- demselben Mass wie DOM-`Text` --, und fuer Speicherung und
  * Abbildung ist das genau richtig. Fuer Benutzeraktionen ist es falsch. Ein Backspace darf kein
  * Surrogatpaar halbieren, keine kombinierende Akzentmarke vom Grundzeichen trennen und keine
  * ZWJ-Emoji-Sequenz zerlegen. Ein einzelnes Emoji kann sieben UTF-16-Einheiten belegen und ist
  * trotzdem ein Tastendruck (§11).
  *
  * ==Was hier implementiert ist==
  *
  * Die Grapheme-Cluster-Regeln GB1–GB13 aus UAX #29. Nicht angenaehert, sondern die Regeln selbst
  * -- einschliesslich der beiden, die Kontext brauchen: GB11 (Emoji-ZWJ-Sequenzen) und GB12/GB13
  * (Flaggen aus Regional Indicators). Ein reiner Codepoint-Fallback wuerde genau an diesen beiden
  * scheitern, und die Risikozeile von P06 sagt das ausdruecklich.
  *
  * ==Woher die Zeicheneigenschaften kommen==
  *
  * Zweigeteilt, und das ist der ehrliche Teil der Sache:
  *
  *   - Die '''strukturellen''' Klassen -- CR, LF, ZWJ, ZWNJ, Regional Indicator,
  *     Variationsselektoren, Emoji-Modifikatoren, Hangul-Jamo, Prepend -- stehen als ausdrueckliche
  *     Bereiche in dieser Datei. Sie aendern sich zwischen Unicode-Versionen praktisch nicht.
  *   - Die '''kategoriegetriebenen''' Klassen -- Extend aus Mn/Me, SpacingMark aus Mc, Control aus
  *     Cc/Cf/Zl/Zp -- kommen aus `Character.getType`, also aus der Zeichentabelle der
  *     Scala.js-Standardbibliothek.
  *
  * ==Bekannte Luecken==
  *
  * [[unicodeVersion]] nennt den Stand, gegen den die Bereiche hier gepflegt sind. Es ist
  * '''keine''' Konformitaetszusage:
  *
  *   - GB9c (Indic Conjunct Break, Unicode 15.1) ist nicht implementiert. Devanagari-Cluster mit
  *     Virama werden daher an Stellen getrennt, an denen UAX #29 sie zusammenhaelt.
  *   - `Extended_Pictographic` ist ueber gepflegte Bereiche angenaehert, nicht aus `emoji-data.txt`
  *     erzeugt.
  *   - Die Wortgrenzen sind eine dokumentierte Vereinfachung, nicht UAX #29 §4 -- siehe
  *     [[nextWordBoundary]].
  *
  * Ein Browseradapter auf `Intl.Segmenter` kann spaeter danebentreten. Er muesste dann gegen
  * dieselben Faelle getestet sein, und `unicodeVersion` macht sichtbar, warum zwei
  * Implementierungen bei neueren Emoji verschieden urteilen koennen.
  */
object UnicodeTextBoundaries extends TextBoundaryService:

  /** Stand, gegen den die ausdruecklichen Bereiche gepflegt sind.
    *
    * Die kategoriegetriebenen Klassen stammen aus der Tabelle der Scala.js-Standardbibliothek und
    * koennen aelter sein. Der Wert dient dem Vergleich von Fixtures, nicht als Konformitaetsangabe.
    */
  val unicodeVersion: String = "16.0.0 (Teilmenge, ohne GB9c)"

  // -----------------------------------------------------------------------------------------
  // Oeffentliche Schnittstelle
  // -----------------------------------------------------------------------------------------

  def isGraphemeBoundary(text: String, offset: Int): Boolean =
    offset >= 0 && offset <= text.length && boundariesOf(text).contains(offset)

  def previousGraphemeBoundary(text: String, offset: Int): Option[Int] =
    if offset <= 0 then None
    else boundariesOf(text).filter(_ < math.min(offset, text.length)).lastOption

  def nextGraphemeBoundary(text: String, offset: Int): Option[Int] =
    if offset >= text.length then None
    else boundariesOf(text).find(_ > math.max(offset, 0))

  /** Alle Graphemgrenzen einschliesslich 0 und `text.length`.
    *
    * Linear in der Textlaenge, und jeder Aufruf rechnet neu. Fuer Textlaeufe -- die ueblicherweise
    * kurz sind, weil jede Markaenderung einen neuen erzeugt -- ist das in Ordnung. Ein Cache je
    * Knotenversion waere leicht nachzuruesten und kommt, wenn eine Messung ihn rechtfertigt (§8.3),
    * nicht auf Verdacht.
    */
  def boundariesOf(text: String): Vector[Int] =
    if text.isEmpty then Vector(0)
    else
      val boundaries = Vector.newBuilder[Int]
      boundaries += 0

      var index = 0
      while index < text.length do
        val left  = text.codePointAt(index)
        val after = index + Character.charCount(left)
        if after < text.length then
          val right = text.codePointAt(after)
          if breaksBetween(text, index, left, right) then boundaries += after
        index = after

      boundaries += text.length
      boundaries.result()

  /** Ein Wortzeichen im Sinne dieser vereinfachten Segmentierung. */
  def isWordCodePoint(codePoint: Int): Boolean =
    Character.isLetterOrDigit(codePoint) || codePoint == '_'.toInt || isMark(codePoint)

  /** Die naechste Wortgrenze rechts von `offset`.
    *
    * '''Dokumentierte Vereinfachung, nicht UAX #29 §4.''' Die Regel lautet: Nicht-Wortzeichen
    * ueberspringen, dann Wortzeichen ueberspringen, dort halten. Das ist das Verhalten, das
    * Strg+Rechts erwarten laesst.
    *
    * Was UAX #29 zusaetzlich koennte und diese Regel nicht kann: Apostrophe innerhalb eines Wortes
    * zusammenhalten (`don't`), Zahlengruppen mit Trennzeichen (`1,000`), und Wortgrenzen in
    * Schriften ohne Leerzeichen wie Thai oder Japanisch. Wer das braucht, setzt einen
    * `Intl.Segmenter`-Adapter davor -- der Vertrag laesst das ausdruecklich zu.
    */
  def nextWordBoundary(text: String, offset: Int): Option[Int] =
    if offset >= text.length then None
    else
      var index = math.max(offset, 0)
      while index < text.length && !isWordCodePoint(text.codePointAt(index)) do
        index += Character.charCount(text.codePointAt(index))
      while index < text.length && isWordCodePoint(text.codePointAt(index)) do
        index += Character.charCount(text.codePointAt(index))
      Some(index)

  /** Die naechste Wortgrenze links von `offset`. Spiegelbild zu [[nextWordBoundary]]. */
  def previousWordBoundary(text: String, offset: Int): Option[Int] =
    if offset <= 0 then None
    else
      var index = math.min(offset, text.length)
      while index > 0 && !isWordCodePoint(text.codePointBefore(index)) do
        index -= Character.charCount(text.codePointBefore(index))
      while index > 0 && isWordCodePoint(text.codePointBefore(index)) do
        index -= Character.charCount(text.codePointBefore(index))
      Some(index)

  // -----------------------------------------------------------------------------------------
  // UAX #29, Grapheme Cluster Boundaries
  // -----------------------------------------------------------------------------------------

  /** Die Grapheme-Cluster-Break-Eigenschaft eines Codepunkts. */
  private enum Property:
    case Other, CR, LF, Control, Extend, ZWJ, RegionalIndicator, Prepend, SpacingMark
    case L, V, T, LV, LVT, ExtendedPictographic

  import Property.*

  /** Bricht der Cluster zwischen `left` und `right`?
    *
    * Die Regeln in der Reihenfolge von UAX #29. `leftStart` wird fuer die beiden kontextabhaengigen
    * Regeln gebraucht.
    */
  private def breaksBetween(text: String, leftStart: Int, left: Int, right: Int): Boolean =
    val l = propertyOf(left)
    val r = propertyOf(right)

    if l == CR && r == LF then false                                       // GB3
    else if l == Control || l == CR || l == LF then true                   // GB4
    else if r == Control || r == CR || r == LF then true                   // GB5
    else if l == L && (r == L || r == V || r == LV || r == LVT) then false // GB6
    else if (l == LV || l == V) && (r == V || r == T) then false           // GB7
    else if (l == LVT || l == T) && r == T then false                      // GB8
    else if r == Extend || r == ZWJ then false                             // GB9
    else if r == SpacingMark then false                                    // GB9a
    else if l == Prepend then false                                        // GB9b
    else if l == ZWJ && r == ExtendedPictographic && startsPictographicSequence(text, leftStart)
    then false // GB11
    else if l == RegionalIndicator && r == RegionalIndicator && startsFlagPair(text, leftStart)
    then false // GB12, GB13
    else true  // GB999

  /** GB11: steht vor diesem ZWJ eine `ExtPict Extend*`-Folge?
    *
    * Ohne diese Rueckschau wuerde jede Emoji-ZWJ-Sequenz -- Familie, Beruf, Herzfarbe -- in ihre
    * Bestandteile zerfallen, und ein Backspace loeschte nur den letzten davon.
    */
  private def startsPictographicSequence(text: String, zwjStart: Int): Boolean =
    var index = zwjStart
    while index > 0 && propertyOf(text.codePointBefore(index)) == Extend do
      index -= Character.charCount(text.codePointBefore(index))
    index > 0 && propertyOf(text.codePointBefore(index)) == ExtendedPictographic

  /** GB12/GB13: ist die Zahl der unmittelbar vorangehenden Regional Indicators gerade?
    *
    * Flaggen bestehen aus genau zwei. Bei gerader Anzahl beginnt hier ein neues Paar und darf nicht
    * getrennt werden; bei ungerader ist das vorige Paar bereits vollstaendig.
    */
  private def startsFlagPair(text: String, riStart: Int): Boolean =
    var index = riStart
    var count = 0
    while index > 0 && propertyOf(text.codePointBefore(index)) == RegionalIndicator do
      index -= Character.charCount(text.codePointBefore(index))
      count += 1
    count % 2 == 0

  // -----------------------------------------------------------------------------------------
  // Zeicheneigenschaften
  // -----------------------------------------------------------------------------------------

  /** Reihenfolge ist hier Programm.
    *
    * ZWJ, ZWNJ und die Prepend-Zeichen sind samt und sonders Format-Zeichen (Cf). Wuerde die
    * Kategorieabfrage zuerst laufen, faenden sie sich alle unter `Control` wieder -- und GB4
    * traennte jede Emoji-Sequenz auf. Die ausdruecklichen Bereiche muessen deshalb vor der
    * Kategorie stehen.
    */
  private def propertyOf(codePoint: Int): Property =
    if codePoint == 0x0a then LF
    else if codePoint == 0x0d then CR
    else if codePoint == 0x200d then ZWJ
    else if codePoint == 0x200c then Extend                          // ZWNJ
    else if codePoint >= 0xfe00 && codePoint <= 0xfe0f then Extend   // Variationsselektoren
    else if codePoint >= 0x1f3fb && codePoint <= 0x1f3ff then Extend // Hautton-Modifikatoren
    else if codePoint >= 0x1f1e6 && codePoint <= 0x1f1ff then RegionalIndicator
    else if isPrepend(codePoint) then Prepend
    else if hangulOf(codePoint).isDefined then hangulOf(codePoint).get
    else
      val category = Character.getType(codePoint)
      if isMarkCategory(category) then Extend
      else if category == Character.COMBINING_SPACING_MARK.toInt && !isSpacingMarkException(
          codePoint
        )
      then SpacingMark
      else if isControlCategory(category) then Control
      else if isExtendedPictographic(codePoint) then ExtendedPictographic
      else Other

  private def isMark(codePoint: Int): Boolean =
    val category = Character.getType(codePoint)
    isMarkCategory(category) || category == Character.COMBINING_SPACING_MARK.toInt

  private def isMarkCategory(category: Int): Boolean =
    category == Character.NON_SPACING_MARK.toInt || category == Character.ENCLOSING_MARK.toInt

  private def isControlCategory(category: Int): Boolean =
    category == Character.CONTROL.toInt ||
      category == Character.FORMAT.toInt ||
      category == Character.LINE_SEPARATOR.toInt ||
      category == Character.PARAGRAPH_SEPARATOR.toInt ||
      category == Character.SURROGATE.toInt

  /** Hangul-Jamo. Die Silbenbloecke unterscheiden LV von LVT rechnerisch. */
  private def hangulOf(codePoint: Int): Option[Property] =
    if (codePoint >= 0x1100 && codePoint <= 0x115f) || (codePoint >= 0xa960 && codePoint <= 0xa97c)
    then Some(L)
    else if (codePoint >= 0x1160 && codePoint <= 0x11a7) ||
      (codePoint >= 0xd7b0 && codePoint <= 0xd7c6)
    then Some(V)
    else if (codePoint >= 0x11a8 && codePoint <= 0x11ff) ||
      (codePoint >= 0xd7cb && codePoint <= 0xd7fb)
    then Some(T)
    else if codePoint >= 0xac00 && codePoint <= 0xd7a3 then
      Some(if (codePoint - 0xac00) % 28 == 0 then LV else LVT)
    else None

  private def isPrepend(codePoint: Int): Boolean =
    (codePoint >= 0x0600 && codePoint <= 0x0605) || codePoint == 0x06dd || codePoint == 0x070f ||
      (codePoint >= 0x0890 && codePoint <= 0x0891) || codePoint == 0x08e2 ||
      codePoint == 0x0d4e || codePoint == 0x110bd || codePoint == 0x110cd ||
      (codePoint >= 0x111c2 && codePoint <= 0x111c3) || codePoint == 0x1193f ||
      codePoint == 0x11941 || codePoint == 0x11a3a ||
      (codePoint >= 0x11a84 && codePoint <= 0x11a89) || codePoint == 0x11d46 ||
      codePoint == 0x11f02

  /** Mc-Zeichen, die UAX #29 ausdruecklich nicht als SpacingMark fuehrt. */
  private def isSpacingMarkException(codePoint: Int): Boolean =
    codePoint == 0x102b || codePoint == 0x102c || codePoint == 0x1038 ||
      (codePoint >= 0x1062 && codePoint <= 0x1064) ||
      (codePoint >= 0x1067 && codePoint <= 0x106d) || codePoint == 0x1083 ||
      (codePoint >= 0x1087 && codePoint <= 0x108c) || codePoint == 0x108f ||
      (codePoint >= 0x109a && codePoint <= 0x109c) || codePoint == 0x1a61 ||
      codePoint == 0x1a63 || codePoint == 0x1a64 || codePoint == 0xaa7b ||
      codePoint == 0xaa7d || codePoint == 0x11720 || codePoint == 0x11721

  /** Angenaehert ueber gepflegte Bereiche, nicht aus `emoji-data.txt` erzeugt. */
  private def isExtendedPictographic(codePoint: Int): Boolean =
    codePoint == 0x00a9 || codePoint == 0x00ae || codePoint == 0x203c || codePoint == 0x2049 ||
      codePoint == 0x2122 || codePoint == 0x2139 ||
      (codePoint >= 0x2194 && codePoint <= 0x21aa) ||
      (codePoint >= 0x231a && codePoint <= 0x231b) || codePoint == 0x2328 ||
      codePoint == 0x23cf || (codePoint >= 0x23e9 && codePoint <= 0x23f3) ||
      (codePoint >= 0x23f8 && codePoint <= 0x23fa) || codePoint == 0x24c2 ||
      (codePoint >= 0x25aa && codePoint <= 0x25ab) || codePoint == 0x25b6 ||
      codePoint == 0x25c0 || (codePoint >= 0x25fb && codePoint <= 0x25fe) ||
      (codePoint >= 0x2600 && codePoint <= 0x27bf) ||
      (codePoint >= 0x2934 && codePoint <= 0x2935) ||
      (codePoint >= 0x2b00 && codePoint <= 0x2bff) || codePoint == 0x3030 ||
      codePoint == 0x303d || codePoint == 0x3297 || codePoint == 0x3299 ||
      (codePoint >= 0x1f000 && codePoint <= 0x1faff) ||
      (codePoint >= 0x1fc00 && codePoint <= 0x1fffd)
