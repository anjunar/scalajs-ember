package ember.editor.core

/** Warum eine Operation abgewiesen wurde.
  *
  * Getrennt von [[Violation]], weil es etwas anderes beschreibt: eine Violation sagt, dass ein
  * Dokument ungueltig ist, ein OperationError sagt, dass eine Aenderung nicht anwendbar war. Das
  * Ausgangsdokument bleibt in diesem Fall unveraendert und gueltig.
  */
sealed trait OperationError extends EditorError

object OperationError:

  final case class UnknownNode(nodeId: NodeId) extends OperationError:
    def message: String               = s"Der Knoten `${nodeId.value}` existiert nicht."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class NotAContainer(nodeId: NodeId) extends OperationError:
    def message: String               = s"`${nodeId.value}` kann keine Kinder aufnehmen."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class NotATextNode(nodeId: NodeId) extends OperationError:
    def message: String               = s"`${nodeId.value}` ist kein Textlauf."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class IndexOutOfRange(parent: NodeId, index: Int, size: Int) extends OperationError:
    def message: String =
      s"Kindposition $index liegt ausserhalb von 0..$size in `${parent.value}`."
    override def path: DiagnosticPath = DiagnosticPath.node(parent.value).field("children")

  final case class TextOffsetOutOfRange(nodeId: NodeId, offset: Int, length: Int)
      extends OperationError:
    def message: String =
      s"Textoffset $offset liegt ausserhalb von 0..$length in `${nodeId.value}`."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value).field("text")

  final case class SplitsSurrogatePair(nodeId: NodeId, offset: Int) extends OperationError:
    def message: String =
      s"Offset $offset in `${nodeId.value}` teilt ein Surrogatpaar."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value).field("text")

  final case class MalformedText(nodeId: NodeId) extends OperationError:
    def message: String =
      s"Der einzufuegende Text fuer `${nodeId.value}` enthaelt ein unpaariges Surrogat."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value).field("text")

  final case class IdAlreadyInUse(nodeId: NodeId) extends OperationError:
    def message: String               = s"Die NodeId `${nodeId.value}` ist bereits vergeben."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class RootIsImmovable(rootId: NodeId) extends OperationError:
    def message: String =
      s"Die Wurzel `${rootId.value}` laesst sich weder entfernen noch verschieben."
    override def path: DiagnosticPath = DiagnosticPath.node(rootId.value)

  final case class MoveIntoOwnSubtree(nodeId: NodeId, target: NodeId) extends OperationError:
    def message: String =
      s"`${nodeId.value}` kann nicht unter `${target.value}` verschoben werden -- das liegt in seinem eigenen Teilbaum."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class SubtreeNotSelfContained(nodeId: NodeId, reason: String) extends OperationError:
    def message: String =
      s"Der einzufuegende Teilbaum um `${nodeId.value}` ist unvollstaendig: $reason"
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class ReplacementIdMismatch(nodeId: NodeId, replacementId: NodeId)
      extends OperationError:
    def message: String =
      s"Ersatz fuer `${nodeId.value}` traegt die abweichende ID `${replacementId.value}`."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value)

  final case class ReplacementChangesChildren(nodeId: NodeId) extends OperationError:
    def message: String =
      s"Ersatz fuer `${nodeId.value}` aendert die Kindliste. Struktur aendert man mit Insert, Remove oder Move."
    override def path: DiagnosticPath = DiagnosticPath.node(nodeId.value).field("children")

  final case class NotAdjacentSiblings(left: NodeId, right: NodeId) extends OperationError:
    def message: String =
      s"`${left.value}` und `${right.value}` sind keine unmittelbar benachbarten Geschwister."
    override def path: DiagnosticPath = DiagnosticPath.node(left.value)

  final case class MarksDiffer(left: NodeId, right: NodeId) extends OperationError:
    def message: String =
      s"`${left.value}` und `${right.value}` tragen verschiedene Marks; ein Merge wuerde Formatierung verlieren."
    override def path: DiagnosticPath = DiagnosticPath.node(left.value).field("marks")
