package ember.editor.core

import scala.collection.mutable

/** Lesender Blick auf ein Dokument.
  *
  * Eigener Typ, damit [[NodeType.validate]] und spaetere Transforms lesen koennen, ohne ein
  * konstruierbares [[Document]] in der Hand zu haben. Alle Rueckgaben sind unveraenderlich; es gibt
  * keinen Weg, ueber diese Schnittstelle eine Kindliste oder die Nodemap zu veraendern (P02,
  * Risiken).
  *
  * Alle Traversierungen sind iterativ. Ein importiertes Dokument kann beliebig tief sein, und eine
  * rekursive Traversierung wuerde daran mit einem Stackueberlauf scheitern statt mit einer Diagnose
  * (§8.3).
  */
trait DocumentRead:

  /** Das Schema, gegen das dieses Dokument gueltig ist. */
  def schema: Schema

  /** ID der Wurzel. */
  def rootId: NodeId

  /** Anzahl der Knoten einschliesslich Wurzel. */
  def size: Int

  /** Der Knoten zu dieser ID, oder `None`. */
  def node(id: NodeId): Option[EditorNode]

  /** Der Elternknoten, oder `None` fuer die Wurzel. */
  def parentOf(id: NodeId): Option[NodeId]

  /** Alle IDs in unspezifizierter Reihenfolge. Fuer Dokumentordnung [[subtreeOf]] verwenden. */
  def ids: Iterator[NodeId]

  final def contains(id: NodeId): Boolean = node(id).isDefined

  final def root: EditorNode =
    node(rootId).getOrElse(
      throw EditorContractViolation(s"Die Wurzel ${rootId.value} fehlt im Dokument.")
    )

  final def nodes: Iterator[EditorNode] = ids.flatMap(node)

  /** Die Kinder-IDs, oder leer fuer Blaetter und unbekannte IDs. */
  final def childrenOf(id: NodeId): Vector[NodeId] = node(id) match
    case Some(element: ElementNode) => element.children
    case _                          => Vector.empty

  /** Der Deskriptor dieses Knotens, sofern das Schema einen kennt. */
  final def typeOf(id: NodeId): Option[NodeType[?]] = node(id).flatMap(schema.descriptorFor)

  /** Der Teilbaum in Dokumentordnung (Praeorder, Kinder von links nach rechts), inklusive `id`.
    *
    * Iterativ mit explizitem Stapel. Setzt ein gueltiges Dokument voraus -- in einem zyklischen
    * Graphen liefe die Traversierung nicht ab. Waehrend der Validierung wird sie deshalb nicht
    * verwendet; [[DocumentValidator]] bringt eine eigene, zyklussichere Traversierung mit.
    */
  final def subtreeOf(id: NodeId): Iterator[NodeId] =
    if !contains(id) then Iterator.empty
    else
      new Iterator[NodeId]:
        private val pending  = mutable.Stack(id)
        def hasNext: Boolean = pending.nonEmpty
        def next(): NodeId   =
          val current  = pending.pop()
          val children = childrenOf(current)
          var index    = children.length - 1
          while index >= 0 do
            pending.push(children(index))
            index -= 1
          current

  /** Die Vorfahren von der unmittelbaren Eltern bis zur Wurzel. */
  final def ancestorsOf(id: NodeId): Vector[NodeId] =
    val collected = Vector.newBuilder[NodeId]
    var current   = parentOf(id)
    while current.isDefined do
      collected += current.get
      current = parentOf(current.get)
    collected.result()

  /** Die Position dieses Knotens in der Kindliste seiner Eltern.
    *
    * Linearer Scan der Geschwister. §8.2 erlaubt einen Positionsindex ausdruecklich nur als Cache
    * und nicht als zweite persistente Wahrheit; eine behauptete O(1)-Zusicherung waere hier
    * schlicht unwahr. Ein Index kommt erst bei gemessener Last (§8.3).
    */
  final def indexOfChild(id: NodeId): Option[Int] =
    parentOf(id).map(parent => childrenOf(parent).indexOf(id)).filter(_ >= 0)

  /** Der Weg von der Wurzel zu diesem Knoten als Folge von Kindpositionen.
    *
    * Die Wurzel selbst ergibt die leere Folge. Iterativ.
    */
  final def pathIndices(id: NodeId): Vector[Int] =
    val collected = Vector.newBuilder[Int]
    var current   = id
    var parent    = parentOf(current)
    while parent.isDefined do
      collected += childrenOf(parent.get).indexOf(current)
      current = parent.get
      parent = parentOf(current)
    collected.result().reverse

  /** Vergleicht zwei Punkte in Dokumentordnung: negativ, null oder positiv.
    *
    * ==Warum nicht ueber IDs==
    *
    * §11 schliesst einen lexikographischen ID-Vergleich fuer die Dokumentordnung ausdruecklich aus.
    * IDs sind undurchsichtige Bezeichner; welcher Knoten frueher steht, ergibt sich ausschliesslich
    * aus dem Baum.
    *
    * ==Wie==
    *
    * Jeder Punkt bekommt eine Adresse: der Weg von der Wurzel zu seinem Knoten, plus sein eigener
    * Offset. Verglichen wird lexikographisch, wobei die kuerzere Folge zuerst kommt. Das ist genau
    * richtig: `Children(p, k)` hat die Adresse `path(p) :+ k` und ist damit Praefix jeder Adresse
    * innerhalb des k-ten Kindes -- die Grenze vor einem Kind liegt vor jeder Position darin.
    */
  final def comparePoints(left: Point, right: Point): Int =
    compareAddresses(addressOf(left), addressOf(right))

  private def addressOf(point: Point): Vector[Int] = point match
    case Point.Text(node, offset, _)       => pathIndices(node) :+ offset
    case Point.Children(parent, offset, _) => pathIndices(parent) :+ offset

  private def compareAddresses(left: Vector[Int], right: Vector[Int]): Int =
    val shared = math.min(left.length, right.length)
    var index  = 0
    while index < shared do
      val difference = left(index) - right(index)
      if difference != 0 then return difference
      index += 1
    left.length - right.length

  /** Der Diagnosepfad von der Wurzel zu diesem Knoten, fuer Fehlermeldungen.
    *
    * Erfuellt die Forderung aus §8.2, dass ungueltige Eingaben Pfad, ID und Grund liefern. Ein
    * Knoten ohne Elternkette -- die Wurzel oder eine unbekannte ID -- ergibt einen Pfad, der nur
    * ihn selbst nennt.
    */
  final def pathTo(id: NodeId): DiagnosticPath =
    ancestorsOf(id).reverse
      .foldLeft(DiagnosticPath.Root)((path, ancestor) => path.node(ancestor.value))
      .node(id.value)
