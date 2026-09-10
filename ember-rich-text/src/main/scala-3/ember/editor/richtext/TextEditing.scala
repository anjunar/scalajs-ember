package ember.editor.richtext

import ember.editor.core.*

/** Die Editing-Semantik des Rich-Text-Profils.
  *
  * Baut ausschliesslich auf den primitiven Operationen des Kerns auf (§10). Der Kern kennt
  * `spliceText` und `move`; dass ein Backspace am Absatzanfang zwei Absaetze zusammenfuehrt, ist
  * eine Entscheidung dieses Profils und steht deshalb hier.
  *
  * ==Dokumentform in P06==
  *
  * `root > paragraph* > text*`. Heading, Quote, Listen und Links kommen mit P12 bis P15 dazu und
  * bringen ihre eigene Semantik mit. Die Funktionen hier arbeiten deshalb mit "Block" als
  * Elternknoten eines Textlaufs, nicht mit `ParagraphNode` -- was sie erweiterbar laesst, ohne dass
  * sie jetzt schon Faelle behandeln, die es noch nicht gibt.
  *
  * ==Graphem statt UTF-16==
  *
  * Loeschbefehle fragen den [[TextBoundaryService]]. Ein Backspace, der einfach eine UTF-16-Einheit
  * entfernt, halbiert Surrogatpaare und reisst Akzentmarken von ihrem Grundzeichen (§11). Das
  * Einfuegen dagegen rechnet in UTF-16 -- was eingefuegt wird, ist vorgegeben und muss nicht
  * segmentiert werden.
  */
object TextEditing:

  // -----------------------------------------------------------------------------------------
  // Einfuegen
  // -----------------------------------------------------------------------------------------

  /** Fuegt Text an der Auswahl ein. Eine nicht kollabierte Auswahl wird zuvor geloescht. */
  def insertText(
      scope: TransformScope,
      generator: NodeIdGenerator,
      text: String
  ): Either[UpdateError, Unit] =
    if text.isEmpty then Right(())
    else
      currentRange(scope) match
        case None        => Right(())
        case Some(range) =>
          for
            _ <- if range.isCollapsed then Right(()) else deleteRange(scope, range)
            _ <- insertAtCaret(scope, generator, text)
          yield ()

  private def insertAtCaret(
      scope: TransformScope,
      generator: NodeIdGenerator,
      text: String
  ): Either[UpdateError, Unit] =
    caretOf(scope) match
      case None        => Right(())
      case Some(point) =>
        val document = scope.document
        textPositionOf(document, point) match
          case Some((node, offset)) =>
            for
              _ <- scope.spliceText(node, offset, 0, text)
              // Affinitaet `Before`: der Caret klebt an dem, was er gerade getippt hat, und
              // wandert beim naechsten Zeichen mit.
              _ <- scope.select(RangeSelection.caret(Point.textBefore(node, offset + text.length)))
            yield ()

          case None =>
            // An dieser Stelle gibt es noch keinen Textlauf -- etwa in einem frisch
            // entstandenen leeren Block. Dann wird einer angelegt.
            point match
              case Point.Children(parent, index, _) =>
                val created = generator.nextFor(document)
                for
                  _ <- scope.insert(parent, index, TextNode(created, text))
                  _ <- scope.select(RangeSelection.caret(Point.textBefore(created, text.length)))
                yield ()
              case _ => Right(())

  /** Teilt den Block an der Auswahl. Das ist Enter. */
  def insertParagraph(
      scope: TransformScope,
      generator: NodeIdGenerator
  ): Either[UpdateError, Unit] =
    currentRange(scope) match
      case None        => Right(())
      case Some(range) =>
        for
          _ <- if range.isCollapsed then Right(()) else deleteRange(scope, range)
          _ <- splitBlockAtCaret(scope, generator)
        yield ()

  private def splitBlockAtCaret(
      scope: TransformScope,
      generator: NodeIdGenerator
  ): Either[UpdateError, Unit] =
    val document = scope.document

    (for
      point          <- caretOf(scope)
      (node, offset) <- textPositionOf(document, point)
      block          <- document.parentOf(node)
      container      <- document.parentOf(block)
      blockIndex     <- document.indexOfChild(block)
      nodeIndex      <- document.indexOfChild(node)
    yield (node, offset, block, container, blockIndex, nodeIndex)) match

      case None => Right(())

      case Some((node, offset, block, container, blockIndex, nodeIndex)) =>
        val siblings = document.childrenOf(block)
        val length   = textOf(document, node).length

        // Drei Faelle, und der Unterschied ist nicht Kosmetik: am Anfang oder Ende wird
        // ausdruecklich *nicht* geteilt, sonst entstuende bei jedem Enter ein leerer
        // Textlauf, den P12 spaeter wieder einsammeln muesste.
        val split: Either[UpdateError, Vector[NodeId]] =
          if offset == 0 then Right(siblings.drop(nodeIndex))
          else if offset == length then Right(siblings.drop(nodeIndex + 1))
          else
            val created = generator.nextFor(document)
            scope.splitText(node, offset, created).map(_ => created +: siblings.drop(nodeIndex + 1))

        split.flatMap { moving =>
          val paragraphId = generator.nextFor(scope.document)

          // Bleibt nichts zu verschieben, bekommt der neue Block gleich einen leeren Textlauf
          // -- sonst haette der Caret keine gueltige Position, und die Normalisierung liefe
          // erst eine Runde spaeter.
          val fresh =
            if moving.nonEmpty then
              scope.insert(container, blockIndex + 1, ParagraphNode.empty(paragraphId))
            else
              val textId = generator.nextFor(scope.document)
              scope.insert(
                container,
                blockIndex + 1,
                ParagraphNode(paragraphId, Vector(textId)),
                Vector(TextNode(textId, ""))
              )

          for
            _ <- fresh
            _ <- moveAll(scope, moving, paragraphId)
            target = moving.headOption.orElse(scope.document.childrenOf(paragraphId).headOption)
            _ <- target.fold(Right(()))(id =>
              scope.select(RangeSelection.caret(Point.textBefore(id, 0)))
            )
          yield ()
        }

  // -----------------------------------------------------------------------------------------
  // Loeschen
  // -----------------------------------------------------------------------------------------

  /** Backspace. Entfernt genau ein Graphemcluster oder fuehrt zwei Bloecke zusammen. */
  def deleteBackward(
      scope: TransformScope,
      boundaries: TextBoundaryService
  ): Either[UpdateError, Unit] =
    currentRange(scope) match
      case None                              => Right(())
      case Some(range) if !range.isCollapsed => deleteRange(scope, range)
      case Some(range)                       =>
        val document = scope.document
        textPositionOf(document, range.focus) match
          case None => Right(())

          case Some((node, offset)) if offset > 0 =>
            boundaries.previousGraphemeBoundary(textOf(document, node), offset) match
              case None        => Right(())
              case Some(start) =>
                for
                  _ <- scope.spliceText(node, start, offset - start, "")
                  _ <- scope.select(RangeSelection.caret(Point.textBefore(node, start)))
                yield ()

          case Some((node, _)) =>
            // Am Anfang des Laufs: entweder das letzte Zeichen des vorigen Laufs, oder --
            // ueber eine Blockgrenze hinweg -- die Zusammenfuehrung der beiden Bloecke.
            previousRun(document, node) match
              case None                                                  => Right(())
              case Some(previous) if sameBlock(document, previous, node) =>
                val text = textOf(document, previous)
                boundaries.previousGraphemeBoundary(text, text.length) match
                  case None        => Right(())
                  case Some(start) =>
                    for
                      _ <- scope.spliceText(previous, start, text.length - start, "")
                      _ <- scope.select(RangeSelection.caret(Point.textBefore(previous, start)))
                    yield ()
              case Some(previous) =>
                joinBlocks(scope, into = previous, from = node)

  /** Entfernen. Spiegelbild zu [[deleteBackward]]. */
  def deleteForward(
      scope: TransformScope,
      boundaries: TextBoundaryService
  ): Either[UpdateError, Unit] =
    currentRange(scope) match
      case None                              => Right(())
      case Some(range) if !range.isCollapsed => deleteRange(scope, range)
      case Some(range)                       =>
        val document = scope.document
        textPositionOf(document, range.focus) match
          case None => Right(())

          case Some((node, offset)) if offset < textOf(document, node).length =>
            boundaries.nextGraphemeBoundary(textOf(document, node), offset) match
              case None      => Right(())
              case Some(end) => scope.spliceText(node, offset, end - offset, "")

          case Some((node, _)) =>
            nextRun(document, node) match
              case None                                                    => Right(())
              case Some(following) if sameBlock(document, node, following) =>
                boundaries.nextGraphemeBoundary(textOf(document, following), 0) match
                  case None      => Right(())
                  case Some(end) => scope.spliceText(following, 0, end, "")
              case Some(following) =>
                joinBlocks(scope, into = node, from = following)

  /** Loescht einen Bereich, auch ueber mehrere Knoten und Bloecke hinweg. */
  def deleteRange(scope: TransformScope, range: RangeSelection): Either[UpdateError, Unit] =
    val document   = scope.document
    val (from, to) = range.ordered(document)

    (textPositionOf(document, from), textPositionOf(document, to)) match
      case (Some((startNode, startOffset)), Some((endNode, endOffset))) =>
        if startNode == endNode then
          if endOffset <= startOffset then Right(())
          else
            for
              _ <- scope.spliceText(startNode, startOffset, endOffset - startOffset, "")
              _ <- scope.select(RangeSelection.caret(Point.textBefore(startNode, startOffset)))
            yield ()
        else deleteAcross(scope, startNode, startOffset, endNode, endOffset)
      case _ => Right(())

  /** Der Fall ueber mehrere Knoten: sieben Schritte, jeder fuer sich klein.
    *
    * Die Reihenfolge ist nicht beliebig. Erst werden die beiden Randknoten gekuerzt, dann faellt
    * alles dazwischen weg, und erst zum Schluss werden die Bloecke zusammengefuehrt -- so bezieht
    * sich jeder Schritt auf einen Zustand, in dem die noch benoetigten Knoten alle existieren.
    */
  private def deleteAcross(
      scope: TransformScope,
      startNode: NodeId,
      startOffset: Int,
      endNode: NodeId,
      endOffset: Int
  ): Either[UpdateError, Unit] =
    val document = scope.document
    val blocks   = (document.parentOf(startNode), document.parentOf(endNode))

    blocks match
      case (Some(startBlock), Some(endBlock)) =>
        for
          _ <- trimTail(scope, startNode, startOffset)
          _ <- removeAfter(scope, startNode)
          _ <- removeBefore(scope, endNode)
          _ <- trimHead(scope, endNode, endOffset)
          _ <- removeBlocksBetween(scope, startBlock, endBlock)
          _ <-
            if startBlock == endBlock then Right(())
            else joinBlocks(scope, into = startNode, from = endNode)
          _ <- scope.select(RangeSelection.caret(Point.textBefore(startNode, startOffset)))
        yield ()
      case _ => Right(())

  private def trimTail(
      scope: TransformScope,
      node: NodeId,
      from: Int
  ): Either[UpdateError, Unit] =
    val length = textOf(scope.document, node).length
    if from >= length then Right(()) else scope.spliceText(node, from, length - from, "")

  private def trimHead(
      scope: TransformScope,
      node: NodeId,
      until: Int
  ): Either[UpdateError, Unit] =
    if until <= 0 then Right(()) else scope.spliceText(node, 0, until, "")

  /** Entfernt alle Geschwister hinter `node` in dessen Block. */
  private def removeAfter(scope: TransformScope, node: NodeId): Either[UpdateError, Unit] =
    siblingsOf(scope.document, node) match
      case Some((siblings, index)) => removeAll(scope, siblings.drop(index + 1))
      case None                    => Right(())

  /** Entfernt alle Geschwister vor `node` in dessen Block. */
  private def removeBefore(scope: TransformScope, node: NodeId): Either[UpdateError, Unit] =
    siblingsOf(scope.document, node) match
      case Some((siblings, index)) => removeAll(scope, siblings.take(index))
      case None                    => Right(())

  /** Entfernt die Bloecke, die vollstaendig zwischen den beiden Randbloecken liegen. */
  private def removeBlocksBetween(
      scope: TransformScope,
      startBlock: NodeId,
      endBlock: NodeId
  ): Either[UpdateError, Unit] =
    if startBlock == endBlock then Right(())
    else
      val document = scope.document
      document.parentOf(startBlock) match
        case None            => Right(())
        case Some(container) =>
          val blocks = document.childrenOf(container)
          val first  = blocks.indexOf(startBlock)
          val last   = blocks.indexOf(endBlock)
          if first < 0 || last < 0 || last <= first + 1 then Right(())
          else removeAll(scope, blocks.slice(first + 1, last))

  /** Haengt den Block von `from` an den Block von `into` an und entfernt den leeren Rest.
    *
    * Die beiden Textlaeufe an der Nahtstelle werden bewusst '''nicht''' verschmolzen. Das ist §8.2s
    * Normalisierung und gehoert zu P12 -- sie darf nur gleich markierte Laeufe zusammenfuehren, und
    * ueber Marks weiss dieses Modul noch nichts.
    */
  private def joinBlocks(
      scope: TransformScope,
      into: NodeId,
      from: NodeId
  ): Either[UpdateError, Unit] =
    val document = scope.document

    (document.parentOf(into), document.parentOf(from)) match
      case (Some(target), Some(source)) if target != source =>
        val moving = document.childrenOf(source)
        for
          _ <- moveAll(scope, moving, target, scope.document.childrenOf(target).length)
          _ <- scope.remove(source)
          _ <- scope.select(
            RangeSelection.caret(Point.textBefore(into, textOf(scope.document, into).length))
          )
        yield ()
      case _ => Right(())

  // -----------------------------------------------------------------------------------------
  // Kleinteiliges
  // -----------------------------------------------------------------------------------------

  private def currentRange(scope: TransformScope): Option[RangeSelection] =
    scope.selection.collect { case range: RangeSelection => range }

  private def caretOf(scope: TransformScope): Option[Point] =
    currentRange(scope).filter(_.isCollapsed).map(_.focus)

  /** Loest einen Punkt zu einer Position in einem Textlauf auf.
    *
    * Ein Kindpunkt bezeichnet eine Grenze zwischen Geschwistern und keinen Text. Fuer das Editieren
    * wird er auf den Anfang des folgenden bzw. das Ende des vorangehenden Laufs abgebildet -- was
    * der Stelle entspricht, an der ein Benutzer den Cursor sieht.
    */
  private def textPositionOf(document: DocumentRead, point: Point): Option[(NodeId, Int)] =
    point match
      case Point.Text(node, offset, _) =>
        document.node(node).collect { case text: TextNode => (node, offset.min(text.text.length)) }

      case Point.Children(parent, index, _) =>
        val children = document.childrenOf(parent)
        val ahead    = children.lift(index).flatMap(asText(document, _)).map((_, 0))
        val behind   = children
          .lift(index - 1)
          .flatMap(asText(document, _))
          .map(node => (node, textOf(document, node).length))
        ahead.orElse(behind)

  private def asText(document: DocumentRead, id: NodeId): Option[NodeId] =
    document.node(id).collect { case text: TextNode => text.id }

  private def textOf(document: DocumentRead, id: NodeId): String =
    document.node(id).collect { case text: TextNode => text.text }.getOrElse("")

  /** Alle Textlaeufe des Dokuments in Dokumentordnung. */
  private def runsOf(document: DocumentRead): Vector[NodeId] =
    document.subtreeOf(document.rootId).filter(asText(document, _).isDefined).toVector

  private def previousRun(document: DocumentRead, node: NodeId): Option[NodeId] =
    val runs = runsOf(document)
    runs.indexOf(node) match
      case index if index > 0 => Some(runs(index - 1))
      case _                  => None

  private def nextRun(document: DocumentRead, node: NodeId): Option[NodeId] =
    val runs = runsOf(document)
    runs.indexOf(node) match
      case index if index >= 0 && index + 1 < runs.length => Some(runs(index + 1))
      case _                                              => None

  private def sameBlock(document: DocumentRead, left: NodeId, right: NodeId): Boolean =
    document.parentOf(left) == document.parentOf(right)

  private def siblingsOf(
      document: DocumentRead,
      node: NodeId
  ): Option[(Vector[NodeId], Int)] =
    document
      .parentOf(node)
      .map(parent => (document.childrenOf(parent), document.childrenOf(parent).indexOf(node)))

  private def removeAll(scope: TransformScope, nodes: Vector[NodeId]): Either[UpdateError, Unit] =
    nodes.foldLeft[Either[UpdateError, Unit]](Right(()))((accumulated, node) =>
      accumulated.flatMap(_ => scope.remove(node))
    )

  /** Verschiebt in der angegebenen Reihenfolge ans Ende bzw. ab `startIndex`. */
  private def moveAll(
      scope: TransformScope,
      nodes: Vector[NodeId],
      target: NodeId,
      startIndex: Int = 0
  ): Either[UpdateError, Unit] =
    nodes.zipWithIndex.foldLeft[Either[UpdateError, Unit]](Right(())) {
      case (accumulated, (node, offset)) =>
        accumulated.flatMap(_ => scope.move(node, target, startIndex + offset))
    }
