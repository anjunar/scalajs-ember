package ember.editor.core

/** Eine Textaenderung an einer Stelle, in UTF-16-Koordinaten.
  *
  * Genau die Form, die `ui.core` als `TextNode.spliceText(start, deleteCount, inserted)` erwartet.
  * Die Projektion kann sie deshalb unveraendert durchreichen, statt den neuen Text vollstaendig zu
  * schreiben -- das ist der Unterschied zwischen einem gezielten `CharacterData.replaceData` und
  * einem Neuschreiben des ganzen Knotens bei jedem Tastendruck.
  */
final case class TextSplice(start: Int, deleteCount: Int, inserted: String):
  def insertedLength: Int = inserted.length
  def delta: Int          = inserted.length - deleteCount

/** Was eine Aenderung am Dokument bewirkt hat.
  *
  * Die Trennung ist der Grund, warum eine lokale Textaenderung keinen Neuaufbau ausloest: die
  * Projektion liest hier ab, welche Knoten sie anfassen muss, statt zwei Snapshots zu vergleichen
  * (§10).
  *
  * ==Warum betroffene Vorfahren getrennt stehen==
  *
  * Lexical markiert Vorfahren nur zur Traversierung dirty, behandelt sie aber nicht als
  * gleichwertige Transform-Kandidaten (§3.4). Genau das bildet [[touchedAncestors]] ab: diese
  * Knoten haben sich '''nicht''' geaendert, sie liegen nur auf dem Pfad. Wer sie mit [[updated]] in
  * einen Topf wirft, rendert bei jedem Tastendruck den Weg bis zur Wurzel neu.
  */
final case class ChangeSet(
    created: Set[NodeId] = Set.empty,
    updated: Set[NodeId] = Set.empty,
    removed: Set[NodeId] = Set.empty,
    moved: Set[NodeId] = Set.empty,
    childListChanged: Set[NodeId] = Set.empty,
    textSplices: Map[NodeId, Vector[TextSplice]] = Map.empty,
    touchedAncestors: Set[NodeId] = Set.empty
):

  /** Keine Dokumentaenderung. Erzeugt weder einen Commit noch eine History-Stufe (§10). */
  def isEmpty: Boolean =
    created.isEmpty && updated.isEmpty && removed.isEmpty && moved.isEmpty &&
      childListChanged.isEmpty && textSplices.isEmpty

  def nonEmpty: Boolean = !isEmpty

  /** Alle tatsaechlich geaenderten Knoten -- ohne die blossen Pfadvorfahren. */
  def changedNodes: Set[NodeId] =
    created ++ updated ++ removed ++ moved ++ childListChanged ++ textSplices.keySet

  /** Fasst zwei aufeinanderfolgende Aenderungen zu einer zusammen.
    *
    * Nicht kommutativ, und die Sonderfaelle sind der eigentliche Inhalt: ein Knoten, der erst
    * erzeugt und dann entfernt wird, taucht in keiner der beiden Mengen auf -- fuer die Projektion
    * hat er nie existiert. Wuerde er in `removed` stehen, versuchte sie einen Knoten abzuraeumen,
    * den sie nie gemountet hat.
    */
  def andThen(next: ChangeSet): ChangeSet =
    val createdThenRemoved = created intersect next.removed

    ChangeSet(
      created = (created ++ next.created) -- createdThenRemoved,
      updated = ((updated ++ next.updated) -- next.removed) -- created,
      removed = (removed ++ next.removed) -- createdThenRemoved,
      moved = ((moved ++ next.moved) -- next.removed) -- createdThenRemoved,
      childListChanged = (childListChanged ++ next.childListChanged) -- next.removed,
      textSplices = mergeSplices(next),
      touchedAncestors = (touchedAncestors ++ next.touchedAncestors) -- next.removed
    )

  /** Splices desselben Knotens bleiben in Reihenfolge; ihre Koordinaten sind aufeinander bezogen.
    */
  private def mergeSplices(next: ChangeSet): Map[NodeId, Vector[TextSplice]] =
    val surviving = textSplices.filterNot((nodeId, _) => next.removed.contains(nodeId))
    next.textSplices.foldLeft(surviving) { case (accumulated, (nodeId, splices)) =>
      accumulated.updated(nodeId, accumulated.getOrElse(nodeId, Vector.empty) ++ splices)
    }

object ChangeSet:

  val empty: ChangeSet = ChangeSet()

  /** Fasst eine Folge in Reihenfolge zusammen. */
  def composeAll(changes: Seq[ChangeSet]): ChangeSet = changes.foldLeft(empty)(_ andThen _)
