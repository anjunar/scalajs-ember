package ember.editor.json

import ember.editor.core.*

/** Was mit einem Knoten geschieht, dessen Typ das Schema nicht kennt.
  *
  * §19.2 laesst genau zwei Moeglichkeiten zu, und die zweite muss ausdruecklich gewaehlt werden:
  * "Unbekannte Daten werden nur mit expliziter Policy erhalten."
  */
enum UnknownNodePolicy:

  /** Ablehnen, mit Pfad und TypeId. Die Voreinstellung. */
  case Strict

  /** Als [[UnsupportedNode]] erhalten.
    *
    * Der Payload ueberlebt einen Roundtrip unveraendert. Er wird '''nicht''' interpretiert: kein
    * Code, kein HTML, kein automatisch deserialisiertes Objekt -- nur der begrenzte JSON-Wert und
    * ein Textfallback (§19.2).
    */
  case Preserve
