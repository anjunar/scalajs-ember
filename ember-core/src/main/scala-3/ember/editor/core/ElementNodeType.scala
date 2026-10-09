package ember.editor.core

/** Deskriptor einer Node-Art mit Kindern.
  *
  * Ohne diesen Vertrag kann der Kern einen Container nicht generisch umbauen. Ein [[ElementNode]],
  * dessen Deskriptor nur ein [[NodeType]] ist, gilt deshalb als Schemafehler und wird bei der
  * Validierung als [[Violation.MissingElementDescriptor]] gemeldet (§8.1, letzter Absatz).
  */
trait ElementNodeType[N <: ElementNode] extends NodeType[N]:

  /** Erzeugt denselben Node mit anderer Kindliste, unter Erhalt aller uebrigen Felder. */
  def withChildren(node: N, children: Vector[NodeId]): N
