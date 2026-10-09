package ember.editor.core

/** Wann a Transform relativ to the anderen runs.
  *
  * Within a Phase entscheiden Abhaengigkeitsordnung and then Registrierungsreihenfolge (§10). Drei
  * Stufen genuegen; who more braucht, has vermutlich a Abhaengigkeit, the he besser deklariert.
  */
enum TransformPhase:

  /** Vor each Normalisierung. For structural Reparatur, on the andere aufbauen. */
  case Early

  /** The Regelfall. */
  case Normalize

  /** After allen anderen. For Ergaenzungen, the a fertigen Baum voraussetzen. */
  case Late
