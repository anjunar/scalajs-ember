package ember.editor.history

import ember.editor.core.*

/** Ein Dokumentstand samt der Auswahl, die dazu gehoert.
  *
  * §14: Eintraege tragen "strukturell geteilte Document-Snapshots und Selection vor/nach der
  * Aenderung". Strukturell geteilt heisst: zwei Snapshots, zwischen denen ein Zeichen liegt,
  * unterscheiden sich in genau einem Knoten und teilen alle uebrigen -- das ist keine Zusage dieses
  * Moduls, sondern eine Eigenschaft von [[Document]] (§8.2).
  *
  * ==Warum ein paar Felder doch mitkommen==
  *
  * §14: "StateFields deklarieren einen eigenen Restore-/Mapping-Vertrag." Die allermeisten folgen
  * aus dem Dokument und werden mit ihm wieder richtig; `TypingMarks` (P12) tut es nicht, und §11
  * verbietet ausdruecklich, die fuer die naechste Eingabe wirksamen Marks nach einem Undo aus der
  * Darstellung zu erraten. Aufgenommen wird deshalb genau das, was ein Feld ueber
  * [[HistoryRestorePolicy]] anmeldet -- typisiert als [[FieldValue]], nicht als `Any`.
  *
  * '''Was hier nicht steht:''' ViewState, DOM, Uploads und die History selbst (§14). Deshalb steht
  * die History auch nicht in [[EditorState]] -- sonst enthielte jeder Snapshot alle vorherigen.
  */
final case class HistorySnapshot(
    document: Document,
    selection: Option[Selection],
    fields: Vector[FieldValue[?]] = Vector.empty
)
