package ember.editor.core

/** Der Unterschied zweier Dokumentstaende, als [[ChangeSet]] und [[PositionMapping]].
  *
  * ==Warum es das ueberhaupt gibt==
  *
  * §10 haelt fest, dass die Projektion am ChangeSet abliest, was sie anfassen muss, "statt zwei
  * Snapshots zu vergleichen". Das gilt fuer jede gewoehnliche Aenderung: sie entsteht aus
  * Operationen, und jede Operation weiss selbst, was sie bewirkt hat.
  *
  * Fuer '''einen''' Fall gilt es nicht. §14 legt die History auf "strukturell geteilte
  * Document-Snapshots" fest -- ausdruecklich nicht auf ein Operationsprotokoll, das erst eine
  * spaetere, selektive History braeuchte. Ein Undo hat also keine Operationen, sondern zwei
  * Staende, und irgendwo muss der Unterschied zwischen ihnen berechnet werden. Hier, einmal, mit
  * klar benannten Grenzen -- und nicht in der Projektion, die sonst fuer jeden Commit einen zweiten
  * Weg bekaeme.
  *
  * ==Was die Naeherung kostet==
  *
  * Das Ergebnis ist bewusst eine '''Ueberschaetzung''': ein Knoten, dessen Kindliste sich geaendert
  * hat, steht auch in `updated`, und ein Textlauf, fuer den ein Splice entsteht, ebenso. Beides
  * fuehrt zu einem zusaetzlichen Aufruf von `NodeView.update` -- und der ist nachweislich
  * folgenlos: `SemanticElement` schreibt unveraenderte Attribute nicht, und `TextComponent.setText`
  * mit dem Wert, den der Splice gerade erzeugt hat, ist der No-op, den P08 im Browser mit einem
  * MutationObserver belegt hat.
  *
  * Genauer waere teurer und braechte nichts: um "hat sich ausser den Kindern etwas geaendert" exakt
  * zu beantworten, muesste jeder Knoten ueber seinen Deskriptor rekonstruiert werden.
  *
  * ==Textsplices==
  *
  * Fuer einen geaenderten Textlauf entsteht '''ein''' Splice ueber den Bereich zwischen dem
  * gemeinsamen Praefix und dem gemeinsamen Suffix. Bei einem rueckgaengig gemachten Tastendruck ist
  * das genau das eine Zeichen, um das es ging, und nicht der ganze Absatz -- derselbe Unterschied,
  * um den es §15.1 geht.
  */
private[core] object DocumentDiff:

  /** Der Unterschied von `before` nach `after`. */
  def between(before: Document, after: Document): (ChangeSet, PositionMapping) =
    val beforeIds = before.byId.keySet
    val afterIds  = after.byId.keySet

    val created = afterIds -- beforeIds
    val removed = beforeIds -- afterIds
    val common  = beforeIds intersect afterIds

    var updated          = Set.empty[NodeId]
    var moved            = Set.empty[NodeId]
    var childListChanged = Set.empty[NodeId]
    var splices          = Map.empty[NodeId, Vector[TextSplice]]

    common.foreach { id =>
      val previous = before.byId(id)
      val next     = after.byId(id)

      if before.parentOf(id) != after.parentOf(id) then moved = moved + id

      if previous != next then
        updated = updated + id

        (previous, next) match
          case (left: ElementNode, right: ElementNode) if left.children != right.children =>
            childListChanged = childListChanged + id
          case (left: TextNode, right: TextNode) if left.text != right.text =>
            splices = splices.updated(id, Vector(spliceBetween(left.text, right.text)))
          case _ => ()
    }

    val changes = ChangeSet(
      created = created,
      updated = updated,
      removed = removed,
      moved = moved,
      childListChanged = childListChanged,
      textSplices = splices,
      // Nach einer Wiederherstellung gibt es keine blossen Pfadvorfahren: jeder Knoten, der hier
      // steht, hat sich tatsaechlich geaendert. Ein Vorfahr, der nur auf dem Weg liegt, ist in
      // beiden Staenden derselbe Wert und taucht gar nicht erst auf.
      touchedAncestors = Set.empty
    )

    (changes, mappingFor(before, after, removed, splices))

  /** Der kleinste Splice, der `previous` in `next` ueberfuehrt. */
  private def spliceBetween(previous: String, next: String): TextSplice =
    val limit  = math.min(previous.length, next.length)
    var prefix = 0
    while prefix < limit && previous.charAt(prefix) == next.charAt(prefix) do prefix += 1

    var suffix = 0
    while suffix < limit - prefix &&
      previous.charAt(previous.length - 1 - suffix) == next.charAt(next.length - 1 - suffix)
    do suffix += 1

    TextSplice(
      start = prefix,
      deleteCount = previous.length - prefix - suffix,
      inserted = next.substring(prefix, next.length - suffix)
    )

  /** Die Abbildung alter Punkte auf den wiederhergestellten Stand.
    *
    * '''Keine Identitaet.''' Ein Punkt, dessen Knoten es nicht mehr gibt, ist verschoben, und ein
    * Offset hinter dem Ende seines Laufs ebenso. Textpunkte folgen dem ermittelten Splice;
    * Kindgrenzen folgen ihrem linken oder rechten Anker. Nicht rekonstruierbare Stellen werden als
    * verloren gemeldet, statt einen unveraenderten Offset zu versprechen.
    *
    * Fuer die Auswahl spielt das keine Rolle -- ein Undo setzt sie ausdruecklich auf den
    * gespeicherten Wert. Es zaehlt fuer [[Bookmark]]s und fuer jeden, der eine gemerkte Position
    * ueber die Wiederherstellung hinweg aufloesen will.
    */
  private def mappingFor(
      before: Document,
      after: Document,
      removed: Set[NodeId],
      splices: Map[NodeId, Vector[TextSplice]]
  ): PositionMapping =
    PositionMapping.of(removedNodes = removed) { point =>
      def lost = MappedPoint.Displaced(Point.Children(after.rootId, 0, point.affinity))
      point match
        case Point.Text(node, offset, affinity) =>
          (before.node(node), after.node(node)) match
            case (Some(old: TextNode), Some(text: TextNode)) if offset <= old.text.length =>
              splices
                .getOrElse(node, Vector.empty)
                .foldLeft[MappedPoint](MappedPoint.Preserved(point)) { (mapped, splice) =>
                  mapped.flatMap { at =>
                    PositionMapping
                      .deleteText(node, splice.start, splice.start + splice.deleteCount)(at)
                      .flatMap(deleted =>
                        MappedPoint.Preserved(
                          PositionMapping
                            .insertText(node, splice.start, splice.inserted.length)(deleted)
                        )
                      )
                  }
                }
            case (_, Some(text: TextNode)) =>
              MappedPoint.Displaced(Point.Text(node, math.min(offset, text.text.length), affinity))
            case _ => lost
        case Point.Children(parent, offset, affinity) =>
          (before.node(parent), after.node(parent)) match
            case (Some(old: ElementNode), Some(next: ElementNode))
                if offset <= old.children.length =>
              val anchored = affinity match
                case Affinity.Before if offset == 0                  => Some(0)
                case Affinity.After if offset == old.children.length => Some(next.children.length)
                case Affinity.Before                                 =>
                  val index = next.children.indexOf(old.children(offset - 1))
                  Option.when(index >= 0)(index + 1)
                case Affinity.After =>
                  val index = next.children.indexOf(old.children(offset))
                  Option.when(index >= 0)(index)
              anchored match
                case Some(index) => MappedPoint.Preserved(Point.Children(parent, index, affinity))
                case None        =>
                  MappedPoint.Displaced(
                    Point.Children(parent, math.min(offset, next.children.length), affinity)
                  )
            case (_, Some(next: ElementNode)) =>
              MappedPoint.Displaced(
                Point.Children(parent, math.min(offset, next.children.length), affinity)
              )
            case _ => lost
    }
