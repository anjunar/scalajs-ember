package ember.editor.core

/** Ein Knoten des Dokumentbaums.
  *
  * '''Bewusst nicht `sealed`.''' Ein ueber alle Features geschlossener Node-Vertrag wuerde
  * Erweiterungen in fremden Dateien und Modulen verhindern -- Paragraph, Heading, Liste, Link, Bild
  * und Tabelle liegen aber alle ausserhalb des Kerns (§8.1). Geschlossen sind stattdessen die
  * strukturellen Kategorien ([[ElementNode]], [[AtomNode]]), die Operationen und die
  * Validierungsergebnisse.
  *
  * Implementierungen sind unveraenderliche Case Classes. Ein gelesener Node ist genau der gelesene
  * Snapshot und loest sich nie selbst gegen einen neueren Stand auf -- Lexicals
  * `getLatest`/`getWritable` wird ausdruecklich nicht uebernommen (§3.2).
  */
trait EditorNode:

  /** Dokumentlokale Identitaet. Innerhalb eines Dokuments eindeutig. */
  def id: NodeId
