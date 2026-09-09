package ember.editor.core

/** Eine synchrone Regel, die einen Commit-Kandidaten ablehnen kann.
  *
  * Laeuft in §10s Schritt 5, also gegen den fertig gerechneten Kandidaten und '''vor''' der
  * Veroeffentlichung. Das ist der entscheidende Zeitpunkt: eine Regel, die erst danach greift, kann
  * nur noch einen bereits gueltigen Zustand nachtraeglich bemaengeln.
  *
  * ==Wozu==
  *
  * Ein Zeichenlimit, ein Readonly-Schalter, die Darstellbarkeit im gewaehlten Formularformat (§16:
  * ein `ToggleUnderline` in einem Strict-CommonMark-Feld darf gar nicht erst durchkommen). Alles,
  * was einen sonst gueltigen Dokumentstand aus fachlichen Gruenden ausschliesst.
  *
  * ==Wozu nicht==
  *
  * Strukturinvarianten -- die sichert bereits [[DocumentValidator]] und, im laufenden Betrieb, die
  * Konstruktion der Operationen. Und keine Normalisierung: eine Regel urteilt, sie aendert nicht.
  * Wer den Kandidaten anpassen will, braucht einen Transform (P05).
  *
  * Rein und synchron. Kein Netzwerk, kein DOM, keine Seiteneffekte -- die Regel laeuft auch fuer
  * Kandidaten, die gleich darauf verworfen werden.
  */
trait PreCommitRule:

  /** Nur fuer Fehlermeldungen. */
  def name: String

  /** `None` heisst: nichts einzuwenden. */
  def check(candidate: CommitCandidate): Option[EditorError]

object PreCommitRule:

  /** Baut eine Regel aus einer Praedikatsfunktion. */
  def apply(ruleName: String)(rule: CommitCandidate => Option[EditorError]): PreCommitRule =
    new PreCommitRule:
      val name: String                                           = ruleName
      def check(candidate: CommitCandidate): Option[EditorError] = rule(candidate)
