package ember.editor.jfx

import ember.editor.core.*
import ember.editor.html.RenderProfile
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.render.Cursor
import jfx.core.statement.KeyedChildren

import scala.collection.mutable

/** Projiziert Dokumentaenderungen in den JFX-Komponentenbaum.
  *
  * ==Was hier bewusst nicht steht==
  *
  * Kein zweiter Renderer, kein VDOM, kein Scheduler (§2, §15.1). Diese Klasse erzeugt kein
  * einziges DOM-Element und bewegt keines. Sie ordnet Knoten-IDs Komponenten zu und ruft
  * `Runtime`-APIs -- Besitz, Einfuegen, Verschieben und Entfernen gehoeren ausschliesslich
  * JFX.
  *
  * Der Index ist "eine Zuordnung, keine zweite Ownership-Liste" (§15.1). Er sagt, welche
  * Komponente zu welcher ID gehoert; er sagt nicht, wer sie besitzt.
  *
  * ==Warum KeyedChildren==
  *
  * Jeder Container bekommt eine [[KeyedChildren]]-Gruppe, gekeyt auf [[NodeId]]. Damit ist die
  * Reihenfolgeabstimmung nicht selbst geschrieben, sondern getesteter Code aus `jfx-core`, und
  * ein Knoten, dessen Wert gleich geblieben ist, wird gar nicht erst angefasst. Ein Wechsel
  * des Elternknotens laeuft ueber `transferTo`, damit beide Schluesselindizes konsistent
  * bleiben (JFX_CORE_INTEGRATION.md).
  *
  * ==Der Aufbau ist eine einzige Rekursion==
  *
  * [[build]] erzeugt eine Komponente und haengt einem Container sofort seine Gruppe ein --
  * '''vor''' dem Mount. Die Gruppe komponiert waehrend `compose` des Containers und ruft dabei
  * wieder [[build]]. So entsteht der ganze Baum in einem Durchgang, in Dokumentreihenfolge,
  * und der Cursor steht bei jedem Kind genau dort, wo es hingehoert. Ein Nachtragen der
  * Gruppen nach dem Mount haette diese Reihenfolge nicht.
  *
  * ==Die Reihenfolge einer Anwendung==
  *
  *   1. Entfernte Knoten aus dem Index nehmen -- die Gruppen raeumen sie selbst ab.
  *   1. Verschobene Knoten zwischen Gruppen uebertragen. '''Vor''' der Neuordnung, sonst
  *      wuerde die Zielgruppe den Knoten als neu ansehen und ein zweites Mal erzeugen.
  *   1. Textsplices anwenden. Vor der Neuordnung, damit der anschliessende Wertvergleich der
  *      Gruppe keinen Unterschied mehr findet und den Text nicht ein zweites Mal schreibt --
  *      diesmal vollstaendig statt gezielt.
  *   1. Geaenderte Kindlisten neu ordnen.
  *   1. Geaenderte Knoten nachfuehren.
  *
  * Schritt 3 sieht nach Umweg aus und ist der Kern der Sache: ein `spliceText` schreibt
  * `CharacterData.replaceData` fuer den geaenderten Bereich, ein `setText` den ganzen Lauf.
  * Bei einem langen Absatz ist das der Unterschied, um den es §15.1 geht.
  */
final class DocumentProjection private[jfx] (
    support: ViewSupport,
    profile: RenderProfile
):

  private type Group = KeyedChildren[NodeId, EditorNode, AbstractComponent]

  private val components = mutable.HashMap.empty[NodeId, AbstractComponent]

  /** Nodes arriving from another parent in the commit being applied. Empty outside one. */
  private var incoming = Set.empty[NodeId]
  private val groups     = mutable.HashMap.empty[NodeId, Group]
  private var current: Document = null
  private var rootComponent: AbstractComponent = null

  /** Die Komponente zu einer Knoten-ID, sofern projiziert. */
  def componentFor(nodeId: NodeId): Option[AbstractComponent] = components.get(nodeId)

  def size: Int = components.size

  /** Baut die erste Ansicht auf. */
  private[jfx] def mount(
      document: Document,
      cursor: Cursor,
      parent: Option[AbstractComponent]
  ): AbstractComponent =
    current = document
    rootComponent = Runtime.mount(build(document.root), cursor, parent)
    rootComponent

  private[jfx] def unmount(): Unit =
    // Der Elternknoten darf denselben Baum abgeraeumt haben -- eine Ansicht, die in einer
    // Komponente haengt, wird mit ihr entsorgt. Ein zweiter Aufruf ist deshalb kein Fehler.
    if rootComponent != null && !rootComponent.isDisposed then Runtime.unmount(rootComponent)
    components.clear()
    groups.clear()
    rootComponent = null
    current = null

  /** Traegt einen Commit nach. */
  private[jfx] def apply(commit: Commit): Unit =
    current = commit.current.document
    val changes = commit.changes

    forget(changes.removed)

    // Nodes that keep their component and change parent. Every group built during this commit
    // has to leave them alone -- see `mountNewContainers`.
    incoming = changes.moved.filter(components.contains)

    try
      mountNewContainers(changes)
      changes.moved.foreach(transfer)
      changes.textSplices.foreach(applySplices)
      (changes.childListChanged ++ changes.moved.flatMap(current.parentOf)).foreach(reorder)
      changes.updated.foreach(refresh)
    finally incoming = Set.empty

  /** Mounts containers that were created in this commit, before anything is transferred.
    *
    * ==The problem this solves==
    *
    * Wrapping a paragraph in a list is three changes at once: a list is created, an item is
    * created, and the paragraph moves into the item. `transferTo` needs the destination group to
    * exist -- and it does not, because the item is mounted by its parent's reorder, which runs
    * afterwards. Left to the plain order, the reorder drops the paragraph from the old group
    * (unmounting it) and the new item's group builds a fresh one. §15.1's "Move erhaelt
    * Node-Identitaet" would hold for every move except the most common one in a list.
    *
    * ==What happens instead==
    *
    * The new containers are mounted first, with two adjustments that make it safe:
    *
    *   - The item list handed to the parent group still contains the nodes that are on their way
    *     out. Without them the parent group would unmount their components before anything could
    *     be transferred.
    *   - The new containers' own groups are built '''without''' the arriving nodes ([[newGroup]]
    *     consults `incoming`). Otherwise the item's group would build a second paragraph a
    *     moment before the real one arrives.
    *
    * Afterwards `transfer` finds a mounted destination, and the closing reorder puts everything
    * in document order. The intermediate arrangement exists for the length of one synchronous
    * commit; §10 guarantees no observer runs inside it.
    */
  private def mountNewContainers(changes: ChangeSet): Unit =
    changes.created
      .flatMap(current.parentOf)
      .foreach { parentId =>
        groups.get(parentId).foreach { group =>
          val settled = current.childrenOf(parentId)
          val leaving = incoming.toVector
            .filter(id => group.componentFor(id).isDefined && !settled.contains(id))

          group.setItems((settled.filterNot(incoming.contains) ++ leaving).flatMap(current.node))
        }
      }

  // -----------------------------------------------------------------------------------------
  // Aufbau
  // -----------------------------------------------------------------------------------------

  /** Erzeugt die Komponente eines Knotens und, wenn er Kinder hat, ihre Gruppe.
    *
    * Montiert nichts: das besorgt der Aufrufer -- die Runtime beim Wurzelknoten, sonst die
    * Gruppe des Elternknotens. Sie tut es unmittelbar nach diesem Aufruf, und erst dabei
    * komponiert die hier eingehaengte Gruppe ihrerseits.
    */
  private def build(node: EditorNode): AbstractComponent =
    val component = support.create(node, profile)
    components.update(node.id, component)
    node match
      case element: ElementNode =>
        component match
          case container: ContainerElement => container.attach(newGroup(element))
          case _ =>
            throw EditorContractViolation(
              s"`${node.id.value}` hat Kinder, seine NodeView liefert aber keinen Container. " +
                "Ein Knoten mit Kindern braucht ein Element, an dem sie haengen koennen (§15.1)."
            )
      case _ => ()
    component

  /** Die Kindergruppe eines Containers.
    *
    * `create` und `update` greifen auf denselben Index zu wie die Projektion -- darueber findet
    * ein spaeteres `transferTo` seine Komponenten wieder.
    */
  private def newGroup(element: ElementNode): Group =
    val group = new KeyedChildren[NodeId, EditorNode, AbstractComponent](
      // Without `incoming`, a group built during a commit would create its own copy of a node
      // whose component is still mounted elsewhere, moments before `transfer` brings the real
      // one over. See `mountNewContainers`.
      element.children.filterNot(incoming.contains).flatMap(current.node),
      _.id,
      build,
      (child, node) => support.update(child, node, profile)
    )
    groups.update(element.id, group)
    group

  // -----------------------------------------------------------------------------------------
  // Nachfuehren
  // -----------------------------------------------------------------------------------------

  private def forget(removed: Set[NodeId]): Unit =
    removed.foreach { nodeId =>
      components.remove(nodeId)
      groups.remove(nodeId)
    }

  /** §15.1: Ein Move erhaelt die Instanz. `transferTo` haelt beide Schluesselindizes konsistent. */
  private def transfer(nodeId: NodeId): Unit =
    for
      parentId    <- current.parentOf(nodeId)
      destination <- groups.get(parentId)
      source      <- groups.values.find(_.componentFor(nodeId).isDefined)
      wanted = current.childrenOf(parentId).indexOf(nodeId)
      if wanted >= 0 && !(source eq destination)
    do
      // The destination may still be missing the siblings that arrive later in this commit, so
      // the document index can be past its end. The closing reorder puts everything right.
      val room = current.childrenOf(parentId).count(destination.componentFor(_).isDefined)
      source.transferTo(nodeId, destination, math.min(wanted, room))

  private def applySplices(entry: (NodeId, Vector[TextSplice])): Unit =
    val (nodeId, splices) = entry
    components.get(nodeId).collect { case run: TextRunElement => run }.foreach { run =>
      splices.foreach(splice => run.spliceText(splice.start, splice.deleteCount, splice.inserted))
    }

  private def reorder(parentId: NodeId): Unit =
    for
      group <- groups.get(parentId)
      node  <- current.node(parentId)
    do group.setItems(current.childrenOf(parentId).flatMap(current.node))

  private def refresh(nodeId: NodeId): Unit =
    for
      component <- components.get(nodeId)
      node      <- current.node(nodeId)
    do if !support.update(component, node, profile) then replaceView(nodeId, node)

  /** Rebuilds the one node whose view no longer fits.
    *
    * §15.1: "Ein typwechselnder Node unter gleicher ID ist eine explizite View-Ersetzung." A
    * heading that was a paragraph is exactly that -- the ID and the children stay, the element
    * does not, and no amount of attribute writing turns a `<p>` into an `<h2>`.
    *
    * ==Two passes over the group, and why==
    *
    * `KeyedChildren` reconciles by key, so a key it already knows is updated, never rebuilt --
    * which is the whole point of it and the reason unchanged siblings survive. There is no
    * "replace this key" on it, and inventing one in `jfx-core` for a case this rare would be
    * the wrong place to spend the API. So the node is taken out of the item list (the group
    * unmounts it and forgets the key) and put back (the group builds it afresh, at its
    * position). The siblings are moved, not rebuilt: `Runtime.move` keeps them.
    *
    * The subtree below goes with it. That is what a replacement means, and §15.1 says it needs
    * selection restoration -- the `SelectionPort` from P21.
    */
  private def replaceView(nodeId: NodeId, node: EditorNode): Unit =
    current.parentOf(nodeId).flatMap(parentId => groups.get(parentId).map(parentId -> _)) match
      case Some((parentId, group)) =>
        val children = current.childrenOf(parentId).flatMap(current.node)
        group.setItems(children.filterNot(_.id == nodeId))
        forget(subtreeOf(nodeId))
        group.setItems(children)
      case None =>
        // The root has no group above it. Replacing it is a remount of everything, which is a
        // decision for whoever owns the view -- not something a commit should do silently.
        throw EditorContractViolation(
          s"Die Wurzel `${nodeId.value}` hat ihre Art gewechselt. Das ist eine Ersetzung der " +
            "ganzen Ansicht und keine Aktualisierung (§15.1)."
        )

  /** A node and everything under it, as far as the index still knows it. */
  private def subtreeOf(nodeId: NodeId): Set[NodeId] =
    current.node(nodeId) match
      case Some(element: ElementNode) =>
        element.children.foldLeft(Set(nodeId))((all, child) => all ++ subtreeOf(child))
      case _ => Set(nodeId)
