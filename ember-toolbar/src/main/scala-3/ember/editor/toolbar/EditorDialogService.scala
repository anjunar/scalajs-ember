package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.link.*
import ember.editor.image.*
import ember.editor.clipboard.ClipboardCommands

/** Opaque, session-owned, one-use dialog target. */
final class DialogTarget private[toolbar] (
    private[toolbar] val owner: AnyRef,
    private[toolbar] val generation: Long,
    val range: RangeSelection,
    val revision: Revision,
    val image: Option[ImageNode]
):
  private[toolbar] var applied = false

/** Document operations only; opening a window and choosing files belong to the caller. */
final class EditorDialogService(
    val session: EditorSession,
    generator: NodeIdGenerator,
    editable: () => Boolean,
    linkPolicy: LinkUrlPolicy = LinkUrlPolicy.default,
    mediaPolicy: MediaUrlPolicy = MediaUrlPolicy.default
):
  private val owner      = new Object()
  private var generation = 0L
  private var active     = Option.empty[DialogTarget]
  private var disposed   = false
  private val commits    = session.onCommit { commit =>
    if commit.changes.documentReplaced || commit.meta.origin == Origin.Import ||
      commit.meta.origin == Origin.History
    then invalidate()
  }

  def capture(): Either[EditorError, DialogTarget] =
    if disposed || session.isDisposed || !editable() then
      Left(ToolbarFailure("Editor ist nicht bearbeitbar."))
    else
      captureRange match
        case Some(range: RangeSelection) =>
          val target =
            new DialogTarget(owner, generation, range, session.state.revision, selectedImage)
          active = Some(target)
          Right(target)
        case _ => Left(ToolbarFailure("Bitte zuerst eine Textposition auswählen."))

  def selectedImage: Option[ImageNode] =
    val id = session.selection match
      case Some(NodeSelection(nodes)) if nodes.size == 1    => nodes.headOption
      case Some(range: RangeSelection) if range.isCollapsed =>
        range.focus match
          case Point.Children(parent, index, _) => session.document.childrenOf(parent).lift(index)
          case _                                => None
      case Some(range: RangeSelection) =>
        range.ordered(session.document) match
          case (Point.Children(parent, from, _), Point.Children(other, to, _))
              if parent == other && to == from + 1 =>
            session.document.childrenOf(parent).lift(from)
          case _ => None
      case _ => None
    id.flatMap(session.document.node).collect { case image: ImageNode => image }

  private def captureRange: Option[RangeSelection] = session.selection match
    case Some(range: RangeSelection) => Some(range)
    case Some(_: NodeSelection)      =>
      selectedImage.flatMap { image =>
        for
          parent <- session.document.parentOf(image.id)
          index  <- session.document.indexOfChild(image.id)
        yield RangeSelection.caret(Point.childrenBefore(parent, index))
      }
    case _ => None

  def resolve(target: DialogTarget): Either[EditorError, RangeSelection] =
    if disposed || session.isDisposed || !active.contains(target) ||
      (target.owner ne owner) || target.generation != generation ||
      target.image.exists(image =>
        !session.document.node(image.id).exists(_.isInstanceOf[ImageNode])
      )
    then
      Left(ToolbarFailure("Die Dialogposition ist nicht mehr gültig. Bitte den Dialog neu öffnen."))
    else
      for
        mapping <- session.mappingSince(target.revision)
        anchor  <- Bookmark(target.range.anchor, target.revision).resolve(mapping)
        focus   <- Bookmark(target.range.focus, target.revision).resolve(mapping)
      yield RangeSelection(anchor, focus)

  def run(target: DialogTarget)(command: Transaction => CommandResult): Either[EditorError, Unit] =
    for
      range <- resolve(target)
      _ <- if editable() then Right(()) else Left(ToolbarFailure("Editor ist nicht bearbeitbar."))
      _ <- session.update(
        TransactionMeta.labelled("editor-dialog").withHistory(HistoryPolicy.Push)
      ) { tx =>
        tx.select(range): Unit
        if command(tx) != CommandResult.Handled then
          tx.reject(ToolbarFailure("Aktion ist an dieser Position nicht verfügbar.")): Unit
      }
    yield
      target.applied = true
      cancel(target)

  def setLink(target: DialogTarget, url: String, title: String): Either[EditorError, Unit] =
    linkPolicy.parse(url).flatMap { parsed =>
      run(target)(
        _.dispatch(LinkCommands.SetLink, LinkTarget(parsed, Option(title).filter(_.nonEmpty)))
      )
    }

  def removeLink(target: DialogTarget): Either[EditorError, Unit] =
    run(target)(_.dispatch(LinkCommands.RemoveLink))

  def insertImage(target: DialogTarget, url: String, alt: String): Either[EditorError, Unit] =
    mediaPolicy.parse(url).flatMap { parsed =>
      run(target) { tx =>
        target.image match
          case Some(image) =>
            tx.select(NodeSelection(Set(image.id))): Unit
            tx.dispatch(
              ImageCommands.UpdateImage,
              current =>
                current.copy(
                  source = if current.src == parsed then current.source else MediaReference(parsed),
                  alt = alt
                )
            )
          case None =>
            tx.selection match
              case Some(range: RangeSelection) if !range.isCollapsed =>
                if tx.dispatch(ClipboardCommands.DeleteSelection) != CommandResult.Handled then
                  tx.reject(ToolbarFailure("Bereich konnte nicht ersetzt werden.")): Unit
              case _ => ()
            tx.dispatch(
              ImageCommands.InsertImage,
              ImageNode(generator.nextFor(tx.document), MediaReference(parsed), alt)
            )
      }
    }

  def cancel(target: DialogTarget): Unit = if active.contains(target) then active = None
  def invalidate(): Unit                 =
    generation += 1
    active = None
  def dispose(): Unit =
    disposed = true
    invalidate()
    commits.dispose()
