package ember.editor.forms

import ember.editor.core.*
import ember.editor.clipboard.*
import ember.editor.image.*
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

/** Async effects retain only live intent state. Every insertion uses a fresh transaction. */
final class MediaCoordinator[F](
    session: EditorSession,
    service: MediaService[F],
    generator: NodeIdGenerator,
    policy: MediaUrlPolicy = MediaUrlPolicy.default,
    availability: () => MediaAvailability = () => MediaAvailability.Ready,
    previews: MediaPreviews[F] = MediaPreviews.none[F],
    changed: Vector[MediaStatus] => Unit = _ => (),
    maxPending: Int = 8,
    retainedResults: Int = 32
)(using ec: ExecutionContext):
  require(maxPending > 0 && retainedResults >= 0)
  private val identity   = new Object()
  private var live       = true
  private var generation = 0L
  private var nextId     = 0L
  private var active     = Map.empty[Long, Job]
  private var results    = Vector.empty[MediaStatus]
  private final class Job(
      val id: Long,
      var range: RangeSelection,
      val alt: String,
      val original: Option[Set[EditorNode]],
      var status: MediaStatus
  ):
    var reference    = Option.empty[MediaReference]
    var finished     = false
    val cancellation = new MediaCancellation(value =>
      if active.contains(id) then
        status = status.copy(progress = status.progress.max(value))
        publish()
    )

  private val subscription = session.onCommit { commit =>
    if commit.changes.documentReplaced || commit.meta.origin == Origin.Import || commit.meta.origin == Origin.History
    then invalidate("Dokument ersetzt oder History verwendet.")
    else
      active.values.toVector.foreach { job =>
        val a = commit.mapping.map(job.range.anchor)
        val b = commit.mapping.map(job.range.focus)
        if !a.isPreserved || !b.isPreserved then
          finish(job, MediaPhase.Discarded("Einfügeziel entfernt."), abort = true)
        else job.range = RangeSelection(a.point, b.point)
      }
  }

  def statuses: Vector[MediaStatus] = results ++ active.values.toVector.sortBy(_.id).map(_.status)
  def availableSlots: Int = if live && !session.isDisposed then maxPending - active.size else 0
  def capture(selection: RangeSelection): Either[MediaFailure, MediaTarget] =
    if !live || session.isDisposed then Left(MediaFailure("Medienkoordination ist beendet."))
    else
      val errors = SelectionSupport.core.validate(selection, session.document)
      if errors.nonEmpty then Left(MediaFailure(errors.map(_.render).mkString("; ")))
      else
        val (a, b)  = selection.ordered(session.document)
        val ordered = RangeSelection(a, b)
        content(ordered).map(original =>
          new MediaTarget(identity, ordered, session.state.revision, generation, original)
        )
  def capture(bookmark: Bookmark): Either[MediaFailure, MediaTarget] =
    session
      .mappingSince(bookmark.revision)
      .flatMap(bookmark.resolve)
      .left
      .map(error => MediaFailure(error.render))
      .flatMap(point => capture(RangeSelection.caret(point)))

  def upload(file: F, target: MediaTarget, alt: String): Either[MediaFailure, Long] =
    prepare(target, alt).map { job =>
      try
        job.status = job.status.copy(preview = previews.create(file))
        publish()
        service.upload(file, job.cancellation).onComplete {
          case Success(reference) => complete(job, reference)
          case Failure(error)     =>
            if active.contains(job.id) then
              finish(job, MediaPhase.Failed(error.getMessage), abort = true)
        }
      catch case NonFatal(error) => finish(job, MediaPhase.Failed(error.getMessage), abort = true)
      job.id
    }

  /** External URLs use the same validation and insertion path without uploading. */
  def insert(
      reference: MediaReference,
      target: MediaTarget,
      alt: String
  ): Either[MediaFailure, Long] =
    prepare(target, alt).map { job => complete(job, reference); job.id }

  def cancel(id: Long): Unit = active.get(id).foreach(finish(_, MediaPhase.Cancelled, abort = true))
  def resume(): Unit         = active.values.toVector.sortBy(_.id).foreach { job =>
    if active.contains(job.id) && job.reference.nonEmpty then attempt(job)
  }
  def invalidate(reason: String = "Dokumentgeneration geändert."): Unit =
    generation += 1
    active.values.toVector.foreach(finish(_, MediaPhase.Discarded(reason), abort = true))
  def dispose(): Unit =
    if live then
      live = false
      subscription.dispose()
      active.values.toVector.foreach(finish(_, MediaPhase.Cancelled, abort = true))

  private def prepare(target: MediaTarget, alt: String): Either[MediaFailure, Job] =
    if !live || session.isDisposed then Left(MediaFailure("Medienkoordination ist beendet."))
    else if !(target.owner eq identity) || target.generation != generation then
      Left(MediaFailure("Veraltetes oder fremdes Medienziel."))
    else if active.size >= maxPending then Left(MediaFailure("Zu viele parallele Uploads."))
    else if availability() == MediaAvailability.ReadOnly then
      Left(MediaFailure("Editor ist schreibgeschützt."))
    else
      (for
        mapping <- session.mappingSince(target.revision)
        a       <- Bookmark(target.selection.anchor, target.revision).resolve(mapping)
        b       <- Bookmark(target.selection.focus, target.revision).resolve(mapping)
      yield RangeSelection(a, b)).left.map(error => MediaFailure(error.render)).flatMap { range =>
        content(range).flatMap { original =>
          if original != target.original then
            Left(MediaFailure("Auswahl seit dem Öffnen der Dateiauswahl geändert."))
          else
            nextId += 1
            val job = new Job(
              nextId,
              range,
              alt,
              original,
              MediaStatus(nextId, MediaPhase.Uploading, 0, None)
            )
            active += job.id -> job
            Right(job)
        }
      }

  private def content(range: RangeSelection): Either[MediaFailure, Option[Set[EditorNode]]] =
    if range.isCollapsed then Right(None)
    else
      DocumentFragment
        .extract(session.document, range)
        .left
        .map(e => MediaFailure(e.message))
        .map(fragment => Some(fragment.document.nodes.toSet))

  private def complete(job: Job, reference: MediaReference): Unit =
    if active.contains(job.id) then
      policy.parse(reference.src.value) match
        case Left(error) => finish(job, MediaPhase.Failed(error.render), abort = true)
        case Right(url)  =>
          job.reference = Some(reference.copy(src = url))
          attempt(job)

  private def attempt(job: Job): Unit =
    try applyReady(job)
    catch case NonFatal(error) => finish(job, MediaPhase.Failed(error.getMessage), abort = true)

  private def applyReady(job: Job): Unit =
    if !active.contains(job.id) then ()
    else if !live || session.isDisposed then
      finish(job, MediaPhase.Discarded("Sitzung beendet."), abort = true)
    else if content(job.range) != Right(job.original) then
      finish(job, MediaPhase.Discarded("Ausgewählter Inhalt wurde geändert."), abort = true)
    else
      availability() match
        case MediaAvailability.Ready =>
          // Remove from pending before the commit callback maps other uploads.
          active -= job.id
          val node   = ImageNode(generator.nextFor(session.document), job.reference.get, job.alt)
          val result = session.update(
            TransactionMeta.labelled("media-insert", Origin.System).withHistory(HistoryPolicy.Push)
          ) { tx =>
            tx.select(job.range): Unit
            if !job.range.isCollapsed && tx.dispatch(
                ClipboardCommands.DeleteSelection
              ) != CommandResult.Handled
            then tx.reject(MediaFailure("ClipboardExtension für Bereichsersetzung fehlt.")): Unit
            if tx.dispatch(ImageCommands.InsertImage, node) != CommandResult.Handled then
              tx.reject(MediaFailure("Bild konnte am Ziel nicht eingefügt werden.")): Unit
          }
          finish(
            job,
            result.fold(
              error => MediaPhase.Failed(error.render),
              commit =>
                commit.changes.created
                  .find(id => commit.current.document.node(id).exists(_.isInstanceOf[ImageNode]))
                  .map(MediaPhase.Inserted.apply)
                  .getOrElse(MediaPhase.Failed("Kein Bild eingefügt."))
            ),
            abort = result.isLeft
          )
        case busy =>
          job.status = job.status.copy(phase = MediaPhase.Waiting(busy), progress = 1)
          publish()

  private def finish(job: Job, phase: MediaPhase, abort: Boolean): Unit =
    if !job.finished then
      job.finished = true
      active -= job.id
      if abort then job.cancellation.cancel() else job.cancellation.release()
      job.status.preview.foreach(url =>
        try previews.revoke(url)
        catch case NonFatal(_) => ()
      )
      job.reference = None
      val progress = phase match
        case MediaPhase.Inserted(_) => 1.0
        case _                      => job.status.progress
      job.status = job.status.copy(phase = phase, preview = None, progress = progress)
      results = (results :+ job.status).takeRight(retainedResults)
      publish()
  private def publish(): Unit =
    try changed(statuses)
    catch case NonFatal(_) => ()
