package ember.editor.core

/** Benannte Prioritaetsstufen.
  *
  * Fuenf Stufen statt freier Zahlen: eine Zahl laedt dazu ein, sich mit `priority + 1` vor einen
  * anderen zu draengen, und dann entscheidet nicht mehr die Absicht, sondern wer zuletzt
  * geschrieben hat. Innerhalb einer Stufe gilt stabile Registrierungsreihenfolge.
  */
enum CommandPriority:
  case Critical, High, Normal, Low, Fallback
