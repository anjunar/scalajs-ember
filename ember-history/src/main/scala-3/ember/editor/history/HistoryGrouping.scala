package ember.editor.history

import ember.editor.core.*

/** Die Gruppierungsregeln aus §14, an einem Ort und ohne Sitzung pruefbar.
  *
  * ==Warum abgeleitet und nicht gemeldet==
  *
  * Ein Editor koennte jeden Command mit "das war Tippen" oder "das war Backspace" beschriften.
  * Er tut es hier nicht, und zwar aus einem konkreten Grund: die Beschriftung waere eine zweite
  * Wahrheit neben dem, was tatsaechlich passiert ist, und beide liefen auseinander, sobald ein
  * Command etwas anderes tut als sein Name sagt. Was wirklich geschehen ist, steht im
  * [[ChangeSet]] -- ein Splice, ein Knoten, ein Bereich --, und der Caret davor sagt, aus
  * welcher Richtung.
  *
  * [[HistoryPolicy]] bleibt die ausdrueckliche Ausnahme: sie traegt Wissen, das im ChangeSet
  * nicht steht.
  *
  * ==Backspace und Delete sind wirklich nicht dasselbe==
  *
  * Am Ergebnis sind sie ununterscheidbar: bei Caret 5 loescht Backspace `[4,5)` und laesst den
  * Caret auf 4; bei Caret 4 loescht Delete `[4,5)` und laesst ihn auf 4. Gleicher Splice,
  * gleiche Endposition. Der Unterschied steht ausschliesslich im Caret '''davor''' -- und
  * genau deshalb liest [[classify]] ihn und nicht die Auswahl danach. §14 verlangt, dass beide
  * getrennte Gruppen bilden; ohne diesen Blick zurueck waere die Regel nicht erfuellbar.
  */
object HistoryGrouping:

  /** Was dieser Commit war. */
  def classify(commit: Commit): EditKind =
    val changes = commit.changes

    val structural =
      changes.created.nonEmpty || changes.removed.nonEmpty ||
        changes.moved.nonEmpty || changes.childListChanged.nonEmpty

    if structural || changes.textSplices.size != 1 then EditKind.Structural
    else
      val (node, splices) = changes.textSplices.head
      // Ein `updated` auf demselben Knoten begleitet jeden Splice; eines auf einem anderen
      // Knoten bedeutet, dass mehr passiert ist als dieser eine Textlauf.
      if splices.length != 1 || !changes.updated.subsetOf(Set(node)) then EditKind.Structural
      else textEdit(node, splices.head, caretOffsetIn(commit.previous.selection, node))

  private def textEdit(node: NodeId, splice: TextSplice, caretBefore: Option[Int]): EditKind =
    if splice.deleteCount == 0 && splice.inserted.nonEmpty then
      EditKind.Insert(node, splice.start, splice.start + splice.inserted.length)
    else if splice.inserted.isEmpty && splice.deleteCount > 0 then
      caretBefore match
        case Some(offset) if offset == splice.start + splice.deleteCount =>
          EditKind.DeleteBackward(node, splice.start, splice.deleteCount)
        case Some(offset) if offset == splice.start =>
          EditKind.DeleteForward(node, splice.start, splice.deleteCount)
        // Weder von vorn noch von hinten am Caret: das war eine Bereichsersetzung, und §14
        // fuehrt sie als Grenze.
        case _ => EditKind.Structural
    else EditKind.Structural

  private def caretOffsetIn(selection: Option[Selection], node: NodeId): Option[Int] =
    selection
      .collect { case range: RangeSelection if range.isCollapsed => range.focus }
      .collect { case Point.Text(owner, offset, _) if owner == node => offset }

  /** Die Markierungen des betroffenen Textlaufs, fuer die Mark-Konfigurationsregel. */
  def marksOf(kind: EditKind, document: Document): MarkSet =
    nodeOf(kind)
      .flatMap(document.node)
      .collect { case text: TextNode => text.marks }
      .getOrElse(MarkSet.empty)

  private def nodeOf(kind: EditKind): Option[NodeId] = kind match
    case EditKind.Insert(node, _, _)         => Some(node)
    case EditKind.DeleteBackward(node, _, _) => Some(node)
    case EditKind.DeleteForward(node, _, _)  => Some(node)
    case EditKind.Structural                 => None

  /** Darf `next` in die offene Gruppe `previous` hineinlaufen?
    *
    * Alle vier Bedingungen aus §14, in der Reihenfolge, in der sie dort stehen: dieselbe Art
    * am selben Knoten, gleiche Mark-Konfiguration, innerhalb des Zeitfensters, und der Caret
    * genau dort, wo die Gruppe aufgehoert hat.
    */
  def mergeable(
      previous: HistoryEntry,
      next: EditKind,
      nextMarks: MarkSet,
      at: Long,
      limits: HistoryLimits
  ): Boolean =
    at - previous.at <= limits.mergeWindowMillis &&
      previous.marks == nextMarks &&
      contiguous(previous.kind, next)

  private def contiguous(previous: EditKind, next: EditKind): Boolean =
    (previous, next) match
      // Weitertippen: das neue Zeichen beginnt, wo das letzte aufgehoert hat.
      case (EditKind.Insert(left, _, to), EditKind.Insert(right, from, _)) =>
        left == right && to == from
      // Weiter zurueckloeschen: der neue Bereich endet, wo der Caret stand.
      case (EditKind.DeleteBackward(left, at, _), EditKind.DeleteBackward(right, start, count)) =>
        left == right && start + count == at
      // Weiter vorwaertsloeschen: der Caret bewegt sich nicht, der Bereich beginnt immer dort.
      case (EditKind.DeleteForward(left, at, _), EditKind.DeleteForward(right, start, _)) =>
        left == right && start == at
      case _ => false
