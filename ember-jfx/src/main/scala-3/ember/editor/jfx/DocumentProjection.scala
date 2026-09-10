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
  private val groups     = mutable.HashMap.empty[NodeId, Group]
  private var current: Document = null
  private var rootComponent: AbstractComponent = null

  /** Die Komponente zu einer Knoten-ID, sofern projiziert. */
  def componentFor(nodeId: NodeId): Option[AbstractComponent] = components.get(nodeId)

  def size: Int = components.size

  /** Baut die erste Ansicht auf. */
  private[jfx] def mount(document: Document, cursor: Cursor): AbstractComponent =
    current = document
    rootComponent = Runtime.mount(build(document.root), cursor, None)
    rootComponent

  private[jfx] def unmount(): Unit =
    if rootComponent != null then Runtime.unmount(rootComponent)
    components.clear()
    groups.clear()
    rootComponent = null
    current = null

  /** Traegt einen Commit nach. */
  private[jfx] def apply(commit: Commit): Unit =
    current = commit.current.document
    val changes = commit.changes

    forget(changes.removed)
    changes.moved.foreach(transfer)
    changes.textSplices.foreach(applySplices)
    (changes.childListChanged ++ changes.moved.flatMap(current.parentOf)).foreach(reorder)
    changes.updated.foreach(refresh)

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
      element.children.flatMap(current.node),
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
      index = current.childrenOf(parentId).indexOf(nodeId)
      if index >= 0 && !(source eq destination)
    do source.transferTo(nodeId, destination, index)

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
    do support.update(component, node, profile)
