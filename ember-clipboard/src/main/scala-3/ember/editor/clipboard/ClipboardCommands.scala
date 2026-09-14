package ember.editor.clipboard

import ember.editor.core.*
import ember.editor.richtext.*

final case class FragmentMove(selection: Selection, destination: Point)

object ClipboardCommands:
  val Paste: EditorCommand[DocumentFragment] = EditorCommand.of("clipboard.paste")
  val DeleteSelection: EditorCommand[Unit]   = EditorCommand.unit("clipboard.delete-selection")
  val Move: EditorCommand[FragmentMove]      = EditorCommand.of("clipboard.move")
  def meta(label: String): TransactionMeta   =
    TransactionMeta.labelled(label).withHistory(HistoryPolicy.Push)

final class ClipboardExtension(generator: NodeIdGenerator) extends Extension:
  val id                                          = ExtensionId("ember.clipboard")
  override val dependsOn                          = Vector(ExtensionId("ember.rich-text"))
  override def contribute: ExtensionContributions = ExtensionContributions(commands =
    Vector(
      CommandRegistration(ClipboardCommands.Paste) { (scope, fragment) =>
        FragmentEditing.paste(scope, fragment, generator): Unit
        CommandResult.Handled
      },
      CommandRegistration(ClipboardCommands.DeleteSelection) { (scope, _) =>
        FragmentEditing.delete(scope): Unit
        CommandResult.Handled
      },
      CommandRegistration(ClipboardCommands.Move) { (scope, move) =>
        FragmentEditing.move(scope, move, generator): Unit
        CommandResult.Handled
      }
    )
  )

private[clipboard] object FragmentEditing:
  private def each[A](
      items: IterableOnce[A]
  )(run: A => Either[UpdateError, Unit]): Either[UpdateError, Unit] =
    items.iterator.foldLeft[Either[UpdateError, Unit]](Right(()))((result, item) =>
      result.flatMap(_ => run(item))
    )

  def delete(scope: TransformScope): Either[UpdateError, Unit] = scope.selection match
    case Some(range: RangeSelection) if range.isCollapsed => Right(())
    case Some(range: RangeSelection)                      =>
      (range.anchor, range.focus) match
        case (Point.Children(a, x, _), Point.Children(b, y, _)) if a == b =>
          val ids = scope.document.childrenOf(a).slice(math.min(x, y), math.max(x, y))
          scope
            .select(RangeSelection.caret(Point.childrenBefore(a, math.min(x, y))))
            .flatMap(_ => each(ids)(scope.remove))
        case _ => TextEditing.deleteRange(scope, range)
    case Some(selection: NodeSelection) =>
      val doc = scope.document
      val ids =
        doc.inDocumentOrder.map(_.id).filter(selection.normalized(doc).nodes.contains).toVector
      ids.headOption match
        case None     => Right(())
        case Some(id) =>
          doc.parentOf(id) match
            case None =>
              scope.reject(ClipboardError("Die Dokumentwurzel kann nicht ausgeschnitten werden."))
            case Some(parent) =>
              scope
                .select(
                  RangeSelection.caret(Point.childrenBefore(parent, doc.indexOfChild(id).get))
                )
                .flatMap(_ => each(ids)(scope.remove))
    case _ => scope.reject(ClipboardError("Keine unterstützte Auswahl."))

  def paste(
      scope: TransformScope,
      fragment: DocumentFragment,
      generator: NodeIdGenerator
  ): Either[UpdateError, Unit] =
    if fragment.isEmpty then Right(())
    else
      Document.build(
        scope.document.schema,
        fragment.document.rootId,
        fragment.document.nodes.toVector
      ) match
        case Left(errors) => scope.reject(ClipboardError(errors.map(_.render).mkString("; ")))
        case Right(_)     =>
          delete(scope).flatMap { _ =>
            scope.selection match
              case Some(range: RangeSelection) if range.isCollapsed =>
                insertAt(scope, fragment, range.focus, generator)
              case _ => scope.reject(ClipboardError("Einfügeziel fehlt."))
          }

  private def insertAt(
      scope: TransformScope,
      fragment: DocumentFragment,
      point: Point,
      generator: NodeIdGenerator
  ): Either[UpdateError, Unit] =
    val source = fragment.document
    val used   = scala.collection.mutable.Set.from(scope.document.ids)
    val remap  = source.ids.map { id =>
      val fresh = generator.next(used.contains)
      used += fresh
      id -> fresh
    }.toMap
    // Copied nodes have not entered the draft yet. Splits must reserve their IDs
    // as well, including when an injected generator reuses the first free ID.
    val reserved = new NodeIdGenerator:
      def next(isTaken: NodeId => Boolean): NodeId =
        val id = generator.next(candidate => used.contains(candidate) || isTaken(candidate))
        used += id
        id
    val copied = source.nodes.map { node =>
      val keyed  = FragmentNodes.rekey(source, node, remap(node.id))
      val result = keyed match
        case element: ElementNode =>
          FragmentNodes.children(source, element, element.children.map(remap))
        case _ => keyed
      result.id -> result
    }.toMap
    val roots                                              = fragment.roots.map(remap)
    def insertRoots(index: Int): Either[UpdateError, Unit] = each(roots.zipWithIndex) {
      (id, offset) =>
        val original    = fragment.roots(offset)
        val descendants = source.subtreeOf(original).drop(1).map(n => copied(remap(n))).toVector
        scope.insert(scope.document.rootId, index + offset, copied(id), descendants)
    }
    point match
      case Point.Children(id, offset, _) if id == scope.document.rootId =>
        insertRoots(offset).flatMap(_ => selectEnd(scope, roots.last))
      case Point.Text(id, offset, _)
          if fragment.openStart == 1 && fragment.openEnd == 1 &&
            fragment.roots.size == 1 && source
              .node(fragment.roots.head)
              .exists(_.isInstanceOf[ParagraphNode]) &&
            source
              .childrenOf(fragment.roots.head)
              .forall(child => !source.node(child).exists(_.isInstanceOf[ElementNode])) =>
        // An open run belongs to the existing inline context, including headings,
        // list items and links. It must not split those wrappers into root blocks.
        val parent   = scope.document.parentOf(id).get
        val index    = scope.document.indexOfChild(id).get
        val text     = scope.document.node(id).get.asInstanceOf[TextNode]
        val children = source.childrenOf(fragment.roots.head).map(remap)
        val split    = if offset > 0 && offset < text.text.length then
          scope.splitText(id, offset, reserved.nextFor(scope.document))
        else Right(())
        split.flatMap { _ =>
          val at = if offset == 0 then index else index + 1
          each(children.zipWithIndex)((child, n) => scope.insert(parent, at + n, copied(child)))
            .flatMap(_ => children.lastOption.map(selectEnd(scope, _)).getOrElse(Right(())))
        }
      case _ =>
        splitToRoot(scope, point, reserved).flatMap { (left, right) =>
          val at = scope.document.indexOfChild(right).get
          insertRoots(at).flatMap { _ =>
            // Mark the insertion end before joining. Primitive mappings carry it
            // through every merge, including inline wrappers and text normalization.
            selectEnd(scope, roots.last).flatMap { _ =>
              val first      = roots.head
              val last       = roots.last
              val leftEmpty  = empty(scope.document, left)
              val rightEmpty = empty(scope.document, right)
              val start      =
                if leftEmpty then scope.remove(left).map(_ => last)
                else
                  join(scope, left, first, fragment.openStart).map(merged =>
                    if merged && first == last then left else last
                  )
              start.flatMap { finalLeft =>
                if rightEmpty then scope.remove(right)
                else join(scope, finalLeft, right, fragment.openEnd).map(_ => ())
              }
            }
          }
        }

  /** Split all containing wrappers through descriptors, never through a second document renderer.
    */
  private def splitToRoot(
      scope: TransformScope,
      point: Point,
      generator: NodeIdGenerator
  ): Either[UpdateError, (NodeId, NodeId)] =
    val doc                                                    = scope.document
    val initial: Either[UpdateError, (NodeId, Vector[NodeId])] = point match
      case Point.Text(id, offset, _) =>
        val parent   = doc.parentOf(id).get
        val siblings = doc.childrenOf(parent)
        val index    = siblings.indexOf(id)
        val text     = doc.node(id).get.asInstanceOf[TextNode]
        if offset == 0 then Right(parent -> siblings.drop(index))
        else if offset == text.text.length then Right(parent -> siblings.drop(index + 1))
        else
          val fresh = generator.nextFor(doc)
          scope.splitText(id, offset, fresh).map(_ => parent -> (fresh +: siblings.drop(index + 1)))
      case Point.Children(id, offset, _) => Right(id -> doc.childrenOf(id).drop(offset))
    initial.flatMap { (start, initialTail) =>
      var parent = start
      var tail   = initialTail
      var result = Option.empty[(NodeId, NodeId)]
      var error  = Option.empty[UpdateError]
      while result.isEmpty && error.isEmpty do
        val current = scope.document
        val outer   = current.parentOf(parent).get
        val index   = current.indexOfChild(parent).get
        val fresh   = generator.nextFor(current)
        val element = current.node(parent).get.asInstanceOf[ElementNode]
        val clone   = FragmentNodes.rekey(
          current,
          FragmentNodes.children(current, element, Vector.empty),
          fresh
        )
        val step = for
          _ <- scope.insert(outer, index + 1, clone)
          _ <- each(tail)(id => scope.move(id, fresh, scope.document.childrenOf(fresh).length))
        yield ()
        error = step.left.toOption
        if outer == current.rootId then result = Some(parent -> fresh)
        else
          tail = fresh +: current.childrenOf(outer).drop(index + 1)
          parent = outer
      error.toLeft(result.getOrElse(start -> start))
    }

  private def join(
      scope: TransformScope,
      left: NodeId,
      right: NodeId,
      depth: Int
  ): Either[UpdateError, Boolean] =
    (scope.document.node(left), scope.document.node(right)) match
      case (Some(a: ElementNode), Some(b: ElementNode))
          if depth > 0 && FragmentNodes.equivalent(scope.document, a, b) =>
        val boundary = for x <- a.children.lastOption; y <- b.children.headOption yield x -> y
        for
          _ <- each(b.children)(id => scope.move(id, left, scope.document.childrenOf(left).length))
          _ <- scope.remove(right)
          _ <- boundary.map((x, y) => join(scope, x, y, depth - 1)).getOrElse(Right(false))
        yield true
      case _ => Right(false)

  private def selectEnd(scope: TransformScope, id: NodeId): Either[UpdateError, Unit] =
    var current  = id
    var children = scope.document.childrenOf(current)
    while children.nonEmpty do
      current = children.last
      children = scope.document.childrenOf(current)
    val point = scope.document.node(current) match
      case Some(text: TextNode) => Point.textBefore(current, text.text.length)
      case Some(_: ElementNode) => Point.childrenBefore(current, 0)
      case _                    =>
        Point.childrenBefore(
          scope.document.parentOf(current).get,
          scope.document.indexOfChild(current).get + 1
        )
    scope.select(RangeSelection.caret(point))

  private def empty(document: DocumentRead, id: NodeId): Boolean =
    document.subtreeOf(id).flatMap(document.node).forall {
      case text: TextNode => text.text.isEmpty
      case _: ElementNode => true
      case _              => false
    }

  def move(
      scope: TransformScope,
      move: FragmentMove,
      generator: NodeIdGenerator
  ): Either[UpdateError, Unit] =
    val document = scope.document
    val errors   = SelectionSupport.core.validate(move.selection, document) ++
      SelectionSupport.core.validate(RangeSelection.caret(move.destination), document)
    if errors.nonEmpty then return scope.reject(ClipboardError(errors.map(_.render).mkString("; ")))
    val selected = move.selection match
      case nodes: NodeSelection  => nodes.normalized(document).nodes
      case range: RangeSelection =>
        val (a, b) = range.ordered(document)
        document.inDocumentOrder
          .map(_.id)
          .filter { id =>
            document.parentOf(id).exists { parent =>
              val index = document.indexOfChild(id).get
              document.comparePoints(a, Point.childrenBefore(parent, index)) <= 0 &&
              document.comparePoints(b, Point.childrenBefore(parent, index + 1)) >= 0
            }
          }
          .toSet
      case _ => Set.empty[NodeId]
    val roots = document.inDocumentOrder
      .map(_.id)
      .filter(id => selected.contains(id) && !document.ancestorsOf(id).exists(selected.contains))
      .toVector
    val exact =
      roots.nonEmpty && DocumentFragment.extract(document, move.selection).toOption.exists { f =>
        f.document.inDocumentOrder
          .filterNot(_.id == f.document.rootId)
          .filterNot(_.isInstanceOf[ElementNode])
          .forall(n =>
            document.node(n.id).contains(n) && roots
              .exists(r => document.subtreeOf(r).contains(n.id))
          )
      }
    if roots.exists(id =>
        id == move.destination.owner || document.ancestorsOf(move.destination.owner).contains(id)
      )
    then Right(())
    else if !exact then moveSlice(scope, move, generator)
    else
      move.destination match
        case Point.Children(parent, offset, _) => moveRoots(scope, roots, parent, offset)
        case Point.Text(id, offset, _)         =>
          val inline = roots.forall(r =>
            document.node(r).exists {
              case _: InlineElementNode | _: TextNode => true
              case _: ThematicBreakNode               => false
              case _: AtomNode                        => true
              case _                                  => false
            }
          )
          if inline then
            val parent = document.parentOf(id).get
            val index  = document.indexOfChild(id).get
            val text   = document.node(id).get.asInstanceOf[TextNode]
            val split  = if offset > 0 && offset < text.text.length then
              scope.splitText(id, offset, generator.nextFor(document))
            else Right(())
            split.flatMap(_ =>
              moveRoots(scope, roots, parent, if offset == 0 then index else index + 1)
            )
          else
            splitToRoot(scope, move.destination, generator).flatMap { (left, right) =>
              for
                _ <- moveRoots(
                  scope,
                  roots,
                  scope.document.rootId,
                  scope.document.indexOfChild(right).get
                )
                _ <- if empty(scope.document, left) then scope.remove(left) else Right(())
                _ <- if empty(scope.document, right) then scope.remove(right) else Right(())
              yield ()
            }

  private def moveRoots(
      scope: TransformScope,
      roots: Vector[NodeId],
      parent: NodeId,
      offset: Int
  ): Either[UpdateError, Unit] =
    var at = offset
    each(roots) { id =>
      val old = scope.document.indexOfChild(id).get
      if scope.document.parentOf(id).contains(parent) && old < at then at -= 1
      val result = scope.move(id, parent, at)
      at += 1
      result
    }.flatMap(_ => scope.select(NodeSelection(roots.toSet)))

  private def moveSlice(
      scope: TransformScope,
      move: FragmentMove,
      generator: NodeIdGenerator
  ): Either[UpdateError, Unit] =
    val doc = scope.document
    move.selection match
      case range: RangeSelection =>
        val (a, b) = range.ordered(doc)
        if doc
            .comparePoints(a, move.destination) <= 0 && doc.comparePoints(move.destination, b) <= 0
        then return Right(())
      case _ => ()
    DocumentFragment.extract(doc, move.selection) match
      case Left(error)     => scope.reject(error)
      case Right(fragment) =>
        val destination = scope.track(move.destination)
        for
          _ <- scope.select(move.selection)
          _ <- delete(scope)
          target = destination.current
          _ <-
            if !target.isPreserved then scope.reject(ClipboardError("Drop-Ziel wurde entfernt."))
            else Right(())
          _ <- scope.select(RangeSelection.caret(target.point))
          _ <- paste(scope, fragment, generator)
        yield ()
