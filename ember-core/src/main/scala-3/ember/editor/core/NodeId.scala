package ember.editor.core

/** Identitaet eines Nodes innerhalb genau eines Dokuments.
  *
  * IDs gelten dokumentlokal (Architektur §8.3). Zwei Dokumente duerfen dieselbe ID verwenden; wer
  * Nodes zwischen Dokumenten bewegt, remappt sie vollstaendig. Server- und Client-Snapshot
  * desselben Dokuments behalten dagegen dieselben IDs -- daran haengt die Hydration.
  *
  * `opaque type` statt `String`: eine ID ist kein Text. Sie soll nicht versehentlich mit einem
  * Node-Typnamen, einem Mark-Bezeichner oder einem DOM-Attributwert verwechselbar sein, und sie
  * darf nirgends ungeprueft als CSS-Selektor landen (§15.1). Zur Laufzeit kostet die Kapselung
  * nichts.
  */
opaque type NodeId = String

object NodeId:

  /** Erzeugt eine ID aus einem im Code bekannten Literal.
    *
    * Wirft bei ungueltiger Eingabe. Das ist Absicht: ein Literal im Quelltext ist entweder gueltig
    * oder ein Programmierfehler, und die Fehlerkonvention ordnet Programmierfehler den Exceptions
    * zu. Fuer Eingaben unbekannter Herkunft -- JSON, Clipboard, Netz -- ist [[parse]] die richtige
    * Tuer.
    */
  def apply(value: String): NodeId =
    parse(value).getOrElse(
      throw EditorContractViolation(s"Keine gueltige NodeId: ${describe(value)}")
    )

  /** Prueft eine ID unbekannter Herkunft. `None` bedeutet: nicht darstellbar. */
  def parse(value: String): Option[NodeId] =
    if isValid(value) then Some(value) else None

  /** Erlaubt ist jeder nicht leere Text ohne Whitespace und ohne Steuerzeichen.
    *
    * Bewusst weit: das Format der IDs bestimmt der injizierte [[NodeIdGenerator]], nicht der Kern.
    * Ausgeschlossen wird nur, was in Wire-Formaten, Attributen und Diagnosepfaden nachweislich
    * Aerger macht.
    */
  private def isValid(value: String): Boolean =
    value.nonEmpty && !value.exists(character => character.isWhitespace || character.isControl)

  private def describe(value: String): String =
    if value.isEmpty then "<leer>" else s"`$value`"

  /** Stabile Ordnung fuer deterministische Diagnoseausgaben und Testvergleiche.
    *
    * Ausdruecklich *keine* Dokumentordnung: welcher Node vor welchem steht, ergibt sich aus der
    * Baumstruktur, nie aus einem lexikographischen ID-Vergleich (§11).
    */
  given Ordering[NodeId] = Ordering.String

  extension (id: NodeId)
    /** Die Textdarstellung, fuer Wire-Formate und Diagnosen. */
    def value: String = id
