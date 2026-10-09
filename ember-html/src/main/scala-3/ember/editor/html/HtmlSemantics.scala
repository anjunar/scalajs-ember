package ember.editor.html

import ember.editor.core.*

/** Wie eine Knotenart als HTML aussieht.
  *
  * ==Warum ein gemeinsamer Vertrag==
  *
  * §15.1: "Ein gemeinsamer semantischer Vertrag liefert sowohl eigenstaendige HTML-Ausgabe als auch
  * die Dokumentansicht." Zwei getrennte Beschreibungen -- eine fuer SSR, eine fuer den Browser --
  * wuerden auseinanderlaufen, und der Unterschied faellt erst bei der Hydration auf, also am
  * spaetestmoeglichen Zeitpunkt. Hier gibt es nur eine.
  *
  * Spiegelt bewusst [[NodeType]]: `nodeType` ist der Typzeuge, und alles Verhalten haengt am
  * Deskriptor statt an der Knotenklasse.
  *
  * @tparam N
  *   die Knotenart
  */
trait HtmlSemantics[N <: EditorNode]:

  def nodeType: NodeType[N]

  def shapeOf(node: N, profile: RenderProfile): HtmlShape
