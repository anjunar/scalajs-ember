package ember.editor.core

/** Was mit dem Wert eines Feldes geschieht, wenn sich das Dokument aendert.
  *
  * §9 verlangt, dass Felder das '''ausdruecklich''' erklaeren. Ein Feld, das schweigt, beantwortet
  * die Frage trotzdem -- nur unsichtbar, und dann meist falsch.
  */
enum DocumentChangePolicy:

  /** Der Wert ueberdauert Dokumentaenderungen unveraendert. */
  case Keep

  /** Der Wert faellt auf `initial` zurueck, sobald sich das Dokument aendert.
    *
    * Richtig fuer alles, was sich auf eine konkrete Stelle bezieht und nach einer Aenderung nicht
    * mehr stimmen kann -- etwa ein am Caret gemerkter Formatierungswunsch nach einem Cursorsprung.
    */
  case Reset
