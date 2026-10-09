package ember.editor.core

/** Wie viel Arbeit the Normalisierung hoechstens kosten darf.
  *
  * §10: a Arbeitsbudget verhindert Endlosschleifen, is but '''no stilles Abschneiden'''. Is it
  * erschoepft, scheitert the Transaktion with a Diagnose the beteiligten Transforms -- a halb
  * normalisiertes Document to veroeffentlichen waere schlimmer als gar none.
  *
  * @param maxRounds
  *   wie oft the Fixpunktschleife hoechstens durchlaeuft
  */
final case class TransformBudget(maxRounds: Int = 32)

object TransformBudget:
  val default: TransformBudget = TransformBudget()
