package ember.editor.core

import scala.collection.mutable

/** Ein verletzter Dokumentvertrag.
  *
  * Geschlossenes ADT: Validierungsergebnisse sind eine strukturelle Kategorie und damit
  * ausdruecklich nicht erweiterbar (§8.1). Fremde Deskriptoren melden ihre fachlichen Regeln ueber
  * [[Violation.NodeRejected]] mit eigenem Grundtext -- sie brauchen dafuer keinen eigenen Typ und
  * koennen den Kern nicht um Fehlerarten erweitern, die niemand behandelt.
  *
  * Jede Verletzung nennt Pfad, ID und Grund (§8.2). Der Pfad kommt aus [[EditorError.path]].
  */
sealed trait Violation extends EditorError

object Violation:

  /** Zwei Knoten beanspruchen dieselbe ID. */
  final case class DuplicateNodeId(nodeId: NodeId) extends Violation:
    def message: String               = s"Die NodeId `${nodeId.value}` kommt mehrfach vor."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  /** Die angegebene Wurzel liegt nicht in der Knotenmenge. */
  final case class MissingRoot(rootId: NodeId) extends Violation:
    def message: String               = s"Die Wurzel `${rootId.value}` fehlt in der Knotenmenge."
    override def path: DiagnosticPath = DiagnosticPath.node(rootId.value)

  /** Die Wurzel kann keine Kinder tragen. */
  final case class RootNotAContainer(rootId: NodeId) extends Violation:
    def message: String               = s"Die Wurzel `${rootId.value}` ist kein ElementNode."
    override def path: DiagnosticPath = DiagnosticPath.node(rootId.value)

  /** Die Wurzel wird als Kind referenziert. Root-Reparenting ist ausgeschlossen (§8.2). */
  final case class RootIsChild(rootId: NodeId, parent: NodeId) extends Violation:
    def message: String =
      s"Die Wurzel `${rootId.value}` ist Kind von `${parent.value}`."
    override def path: DiagnosticPath = DiagnosticPath.node(parent.value).node(rootId.value)

  /** Eine Kindreferenz zeigt ins Leere. */
  final case class MissingChild(parent: NodeId, child: NodeId, index: Int) extends Violation:
    def message: String =
      s"`${parent.value}` verweist an Position $index auf den unbekannten Knoten `${child.value}`."
    override def path: DiagnosticPath =
      DiagnosticPath.node(parent.value).field("children").index(index)

  /** Derselbe Knoten steht mehrfach in derselben Kindliste. */
  final case class DuplicateChild(parent: NodeId, child: NodeId, index: Int) extends Violation:
    def message: String =
      s"`${parent.value}` fuehrt `${child.value}` mehrfach als Kind."
    override def path: DiagnosticPath =
      DiagnosticPath.node(parent.value).field("children").index(index)

  /** Ein Knoten haengt an zwei Eltern. Jeder Knoten ausser der Wurzel hat genau eine (§8.2). */
  final case class MultipleParents(child: NodeId, first: NodeId, second: NodeId) extends Violation:
    def message: String =
      s"`${child.value}` ist Kind von `${first.value}` und `${second.value}`."
    override def path: DiagnosticPath = DiagnosticPath.node(child.value)

  /** Die Kindbeziehungen enthalten einen Zyklus. */
  final case class Cycle(nodeId: NodeId) extends Violation:
    def message: String = s"`${nodeId.value}` ist ueber seine Kinder selbst erreichbar."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  /** Ein Knoten ist von der Wurzel aus nicht erreichbar. */
  final case class UnreachableNode(nodeId: NodeId) extends Violation:
    def message: String =
      s"`${nodeId.value}` ist von der Wurzel aus nicht erreichbar."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  /** Das Schema kennt keine Art fuer diesen Knoten. */
  final case class UnknownNodeType(nodeId: NodeId, nodeClass: String) extends Violation:
    def message: String =
      s"Das Schema kennt keinen Deskriptor fuer `${nodeId.value}` ($nodeClass)."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  /** Ein Container ist nur als [[NodeType]] registriert, nicht als [[ElementNodeType]].
    *
    * Ohne `withChildren` kann der Kern ihn nicht generisch umbauen; er darf deshalb nicht als
    * Container im Dokument stehen (§8.1).
    */
  final case class MissingElementDescriptor(nodeId: NodeId, typeId: NodeTypeId) extends Violation:
    def message: String =
      s"`${nodeId.value}` hat Kinder, aber `${typeId.value}` ist kein ElementNodeType."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  /** Ein Deskriptor hat seinen eigenen Knoten fachlich abgewiesen. */
  final case class NodeRejected(nodeId: NodeId, reason: String, override val path: DiagnosticPath)
      extends Violation:
    def message: String = reason

  /** Bequemer Konstruktor fuer [[NodeType.validate]]-Implementierungen. */
  def reject(node: EditorNode, reason: String, document: DocumentRead): NodeRejected =
    NodeRejected(node.id, reason, document.pathTo(node.id))
