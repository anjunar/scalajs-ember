package ember.editor.core

/** Baut einen Container mit neuer Kindliste ueber seinen registrierten Deskriptor.
  *
  * Der Umweg ueber das Schema ist der Kern von §8.1: der Kern kennt die `copy`-Signatur einer
  * fremden Case Class nicht und darf sie nicht erraten. `ElementNodeType.withChildren` ist der
  * Vertrag, der generisches Einfuegen und Verschieben auch fuer Fremdtypen moeglich macht -- und
  * der alle uebrigen fachlichen Felder erhaelt.
  */
private[core] object ElementSupport:

  def withChildren(document: Document, node: ElementNode, children: Vector[NodeId]): ElementNode =
    document.schema.descriptorFor(node) match
      case Some(descriptor: ElementNodeType[?]) => rebuild(descriptor, node, children)
      case _                                    =>
        // Unerreichbar fuer ein gueltiges Dokument: DocumentValidator meldet einen Container
        // ohne ElementNodeType als MissingElementDescriptor, ein solcher Knoten kommt also gar
        // nicht erst hinein. Bleibt als Vertragsverletzung stehen statt als stiller Datenverlust.
        throw EditorContractViolation(
          s"`${node.id.value}` ist ein Container ohne ElementNodeType. Das Dokument haette nicht gebaut werden duerfen."
        )

  private def rebuild[N <: ElementNode](
      descriptor: ElementNodeType[N],
      node: ElementNode,
      children: Vector[NodeId]
  ): ElementNode =
    descriptor
      .project(node)
      .map(descriptor.withChildren(_, children))
      .getOrElse(
        throw EditorContractViolation(
          s"Der Deskriptor `${descriptor.typeId.value}` weist seinen eigenen Knoten `${node.id.value}` zurueck."
        )
      )
