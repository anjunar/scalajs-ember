package ember.editor.json

import ember.editor.core.*

/** Wie eine Knotenart als JSON aussieht.
  *
  * ==Was ein Codec beschreibt und was nicht==
  *
  * Nur die '''eigenen Felder''' des Knotens. `id`, `type`, `codecVersion` und `children` schreibt
  * und liest das Envelope ([[DocumentJson]]) fuer alle Arten gleich. Das ist keine Bequemlichkeit:
  * Kinder sind laut §8.2 ausschliesslich referenzierte IDs, und die Kindliste eines Containers ist
  * damit eine strukturelle Eigenschaft des Dokuments, keine Nutzlast einer Knotenart. Ein Codec,
  * der sie selbst schriebe, koennte sie auch selbst vergessen.
  *
  * Deshalb bekommt [[decode]] die bereits geprueft eingelesene ID und liefert einen Knoten mit
  * leerer Kindliste; das Envelope setzt sie ueber [[ElementNodeType.withChildren]] ein.
  *
  * ==Zwei Versionen, die nichts miteinander zu tun haben==
  *
  * §19.2: "Node-Codec-Version und Dokumentformat-Version sind getrennt." [[codecVersion]] gehoert
  * dieser Knotenart: sie steigt, wenn sich ihre Felder aendern, und sagt nichts ueber das Envelope.
  * Umgekehrt bleibt ein Dokument der Formatversion 1 lesbar, wenn eine einzelne Knotenart ihre
  * Codec-Version erhoeht.
  *
  * @tparam N
  *   die Knotenart
  */
trait NodeJsonCodec[N <: EditorNode]:

  def nodeType: NodeType[N]

  /** Version der Feldbelegung dieser Knotenart. Beginnt bei 1. */
  def codecVersion: Int = 1

  /** Die eigenen Felder des Knotens, in fester Reihenfolge.
    *
    * Fest, weil die Ausgabe bei gleichem Dokument byteweise gleich sein muss -- sonst sind
    * Roundtrip-Fixtures wertlos (dieselbe Ueberlegung wie bei [[MarkSet]] im Kern).
    */
  def encode(node: N, context: EncodeContext): Either[EncodeError, Vector[(String, JsonValue)]]

  /** Baut den Knoten aus seinen Feldern. Ohne Kinder -- die setzt das Envelope. */
  def decode(
      id: NodeId,
      payload: JsonValue.Obj,
      context: DecodeContext
  ): Either[DecodeError, N]
