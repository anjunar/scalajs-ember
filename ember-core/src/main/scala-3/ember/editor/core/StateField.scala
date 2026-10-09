package ember.editor.core

/** Ein typisierter Platz im Sitzungszustand.
  *
  * ==Was hier hineingehoert==
  *
  * Werte, die zur Transaktion gehoeren und einen Commit ueberdauern sollen: der Formatierungswunsch
  * am leeren Caret (`TypingMarks`, P12), ein abgeleiteter Formularwert (`EncodedFieldValue`, P19b),
  * ein Zaehler fuer ein Zeichenlimit.
  *
  * ==Was nicht==
  *
  * §9 ist da deutlich: Upload-Jobs, DOM-Knoten und Handler sind keine StateFields. Sie sind nicht
  * rein, ueberleben keine Serialisierung und muessen aufgeraeumt werden -- ein Zustandsfeld kann
  * das alles nicht leisten.
  *
  * ==Warum ein Reducer und kein Setter==
  *
  * Ein Feld ist keine frei beschreibbare Zelle. [[reduce]] laeuft im Commit gegen den fertig
  * normalisierten Kandidaten und darf mit typisiertem Fehler '''ablehnen''' (§10, Schritt 5). Genau
  * daran haengt P19b: ein `ToggleUnderline` in einem Strict-CommonMark-Feld muss vor dem Commit
  * scheitern, nicht danach einen veralteten Formularwert hinterlassen.
  *
  * Die Identitaet eines Feldes ist das Objekt selbst, nicht sein Name. [[name]] dient
  * ausschliesslich der Diagnose.
  *
  * @tparam A
  *   der Werttyp
  */
trait StateField[A]:

  /** Nur fuer Fehlermeldungen. Kein Nachschlagschluessel. */
  def name: String

  def initial: A

  def onDocumentChange: DocumentChangePolicy = DocumentChangePolicy.Keep

  /** What undo and redo do with this field.
    *
    * §14: "StateFields deklarieren einen eigenen Restore-/Mapping-Vertrag." Most fields have
    * nothing to restore -- a derived form value is recomputed from the document that comes back
    * anyway. `TypingMarks` (P12) is the case the sentence was written for: §11 requires that
    * "Undo/Redo darf die fuer die naechste Eingabe wirksamen Marks nicht zufaellig aus der
    * DOM-Darstellung ableiten", and a field that is not restored would leave exactly that guess as
    * the only option.
    *
    * Declaring it costs the history one captured value per snapshot, so the default is not to.
    */
  def onHistoryRestore: HistoryRestorePolicy = HistoryRestorePolicy.Recompute

  /** Reiner Reducer, ausgefuehrt vor der Veroeffentlichung des Commits.
    *
    * Bekommt den Wert, wie er nach [[onDocumentChange]] und etwaigen ausdruecklichen Zuweisungen
    * der Transaktion dasteht. Darf lesen, rechnen und ablehnen -- aber nichts ausserhalb beruehren:
    * keine Netzwerkzugriffe, kein DOM, keine Seiteneffekte. Der Kern ruft ihn auch fuer verworfene
    * Kandidaten auf.
    */
  def reduce(current: A, candidate: CommitCandidate): Either[EditorError, A] = Right(current)
