package ember.editor.core

/** Grenzen zwischen Benutzerzeichen und Woertern in einem Textlauf.
  *
  * '''Nur der Vertrag.''' Die Implementierung mit festgelegter Unicode-Datenversion entsteht in P06
  * (`UnicodeTextBoundaries` im Rich-Text-Modul); ein Browseradapter auf `Intl.Segmenter` kann
  * spaeter danebentreten, muss dann aber gegen dieselben Faelle getestet sein.
  *
  * ==Warum das nicht der Kern selbst entscheidet==
  *
  * Der Kern rechnet in UTF-16 -- demselben Mass wie DOM-`Text` -- und das ist fuer Speicherung und
  * Abbildung genau richtig. Fuer Benutzeraktionen ist es falsch: ein Backspace darf kein
  * Surrogatpaar halbieren, keine kombinierende Akzentmarke vom Grundzeichen trennen und keine
  * ZWJ-Sequenz zerlegen. Ein Emoji kann vier UTF-16-Einheiten belegen und ist trotzdem ein
  * Tastendruck. §11 haelt ausdruecklich fest, dass gueltige UTF-16-Offsets allein keine korrekte
  * Unicode-Bearbeitung beweisen.
  *
  * Injiziert statt fest verdrahtet, weil die Unicode-Datenversion eine bewusste, versionierte
  * Entscheidung ist (§26, offene Punkte) und weil Tests deterministische Grenzen brauchen.
  *
  * Alle Offsets sind UTF-16-Offsets in `text`. `None` bedeutet: in dieser Richtung gibt es keine
  * weitere Grenze.
  */
trait TextBoundaryService:

  /** Die Unicode-Datenversion, gegen die dieser Dienst segmentiert, etwa `"16.0.0"`.
    *
    * Teil des Vertrags, nicht Zierde: zwei Implementierungen mit verschiedenen Datenstaenden
    * liefern bei neueren Emoji unterschiedliche Grenzen. Wer Fixtures vergleicht, muss wissen,
    * wogegen sie aufgezeichnet wurden.
    */
  def unicodeVersion: String

  /** Liegt an diesem Offset eine Graphemclustergrenze? */
  def isGraphemeBoundary(text: String, offset: Int): Boolean

  /** Die naechste Graphemclustergrenze links von `offset`. */
  def previousGraphemeBoundary(text: String, offset: Int): Option[Int]

  /** Die naechste Graphemclustergrenze rechts von `offset`. */
  def nextGraphemeBoundary(text: String, offset: Int): Option[Int]

  /** Die naechste Wortgrenze links von `offset`. */
  def previousWordBoundary(text: String, offset: Int): Option[Int]

  /** Die naechste Wortgrenze rechts von `offset`. */
  def nextWordBoundary(text: String, offset: Int): Option[Int]
