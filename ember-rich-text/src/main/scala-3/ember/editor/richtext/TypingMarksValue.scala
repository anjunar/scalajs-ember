package ember.editor.richtext

import ember.editor.core.*

/** What the next keystroke will be marked with.
  *
  * §11 defines it precisely: "`TypingMarks` ist ein transaktionales StateField des
  * Rich-Text-Moduls, mit `Inherit` oder explizitem `MarkSet` an einer gemappten Caretposition."
  */
enum TypingMarksValue:

  /** Take whatever the run at the caret already has. The state after every caret jump. */
  case Inherit

  /** A choice the user made, at the position they made it.
    *
    * The [[at]] point is not decoration. Without it the field could not tell "the caret moved
    * itself because I typed" from "the user clicked somewhere else" -- and §11 requires exactly
    * that distinction: "Ein expliziter Caretsprung oder Range-Wechsel setzt wieder auf
    * kontextabhaengiges Inherit; blosses Mapping desselben Carets durch eigene Eingabe erhaelt die
    * explizite Wahl."
    */
  case Explicit(marks: MarkSet, at: Point)
