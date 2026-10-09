package ember.editor.core

/** Bildet eine Auswahlart durch eine Dokumentaenderung hindurch ab und prueft sie.
  *
  * Spiegelt bewusst [[NodeType]]: `project` ist der Typzeuge, und alles Verhalten haengt am
  * Deskriptor statt an der Auswahlklasse.
  *
  * @tparam S
  *   die konkrete Auswahlart
  */
trait SelectionMapper[S <: Selection]:

  def project(selection: Selection): Option[S]

  /** Fuehrt die Auswahl nach. `None` heisst: es ist keine gueltige Auswahl uebrig geblieben. */
  def map(selection: S, mapping: PositionMapping, after: DocumentRead): Option[Selection]

  /** Prueft, ob die Auswahl in diesem Dokument ueberhaupt darstellbar ist. */
  def validate(selection: S, document: DocumentRead): Vector[Violation]
