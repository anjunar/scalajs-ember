package ember.editor.demo

import ember.editor.browser.{SelectionPort, WriteIntent}
import ember.editor.core.*
import ember.editor.clipboard.ClipboardCommands
import ember.editor.image.*
import ember.editor.link.Links
import ember.editor.toolbar.*
import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.viewport.Viewport

/** Demo-specific forms, directly hosted in the application's UI Viewport. */
final class DemoDialogs(
    editor: DemoSession,
    selection: SelectionPort,
    editable: () => Boolean,
    owner: AbstractComponent
):
  private val service = new EditorDialogService(editor.session, editor.generator, editable)
  private var finish: () => Unit = () => ()
  private var opened             = false
  private var disposed           = false

  def link(): Either[EditorError, Unit] =
    val link = editor.session.selection
      .collect { case range: RangeSelection => range.focus }
      .flatMap(Links.linkAt(editor.session.document, _))
    capture(
      "Link bearbeiten",
      Vector(
        "Adresse"          -> link.map(_.target.url.value).getOrElse(""),
        "Titel (optional)" -> link.flatMap(_.target.title).getOrElse("")
      )
    )(
      (target, values) => service.setLink(target, values(0), values(1)),
      Option.when(link.nonEmpty)(
        "Link entfernen" -> ((target: DialogTarget, _: Vector[String]) =>
          service.removeLink(target)
        )
      )
    )

  def image(): Either[EditorError, Unit] =
    val image = service.selectedImage
    capture(
      if image.nonEmpty then "Bild bearbeiten" else "Bild einfügen",
      Vector(
        "Bildadresse"                 -> image.map(_.src.value).getOrElse("./landscape.svg"),
        "Bildbeschreibung (Alt-Text)" -> image.map(_.alt).getOrElse(""),
        "Titel (optional)"            -> image.flatMap(_.title).getOrElse(""),
        "Breite in Pixeln (optional)" -> image.flatMap(_.width).map(_.value.toString).getOrElse("")
      )
    )((target, values) =>
      for
        url   <- editor.media.parse(values(0))
        width <-
          if values(3).trim.isEmpty then Right(None)
          else
            values(3).trim.toIntOption
              .flatMap(PositivePixels.parse)
              .map(value => Right(Some(value)))
              .getOrElse(
                Left(ToolbarFailure("Die Breite muss zwischen 1 und 100000 Pixeln liegen."))
              )
        _ <- service.run(target) { tx =>
          val title = Option(values(2).trim).filter(_.nonEmpty)
          target.image match
            case Some(current) =>
              tx.select(NodeSelection(Set(current.id)))
              tx.dispatch(
                ImageCommands.UpdateImage,
                image =>
                  image.copy(
                    source = MediaReference(url),
                    alt = values(1),
                    title = title,
                    width = width
                  )
              )
            case None =>
              if !target.range.isCollapsed && tx.dispatch(
                  ClipboardCommands.DeleteSelection
                ) != CommandResult.Handled
              then tx.reject(ToolbarFailure("Die Auswahl konnte nicht ersetzt werden."))
              tx.dispatch(
                ImageCommands.InsertImage,
                ImageNode(
                  editor.generator.nextFor(tx.document),
                  MediaReference(url),
                  values(1),
                  title,
                  width
                )
              )
        }
      yield ()
    )

  private def capture(title: String, fields: Vector[(String, String)])(
      apply: (DialogTarget, Vector[String]) => Either[EditorError, Unit],
      extra: Option[(String, (DialogTarget, Vector[String]) => Either[EditorError, Unit])] = None
  ): Either[EditorError, Unit] =
    if opened then Left(ToolbarFailure("Bitte zuerst das offene Fenster schließen."))
    else
      if selection.scope.focusWithin then selection.importNative()
      service.capture().map { target =>
        var applied = false
        show(
          title,
          fields,
          values => apply(target, values).map(_ => applied = true),
          extra.map((label, action) =>
            label -> ((values: Vector[String]) => action(target, values).map(_ => applied = true))
          ),
          () => {
            val mapped = service.resolve(target).toOption
            service.cancel(target)
            if !disposed then
              selection.scope.focus()
              selection.write(
                if applied then editor.session.selection
                else mapped.orElse(editor.session.selection),
                WriteIntent.Explicit
              )
          }
        )
      }

  def confirmReset(reset: () => Unit): Unit =
    if !opened && editable() then
      show(
        "Beispiel zurücksetzen?",
        Vector.empty,
        _ => { reset(); Right(()) },
        None,
        () => (),
        "Die Änderungen dieses Beispiels werden durch den Ausgangstext ersetzt."
      )

  private def show(
      title: String,
      fields: Vector[(String, String)],
      submit: Vector[String] => Either[EditorError, Unit],
      extra: Option[(String, Vector[String] => Either[EditorError, Unit])],
      after: () => Unit,
      description: String = ""
  ): Unit =
    var done                      = false
    var conf: Viewport.WindowConf = null
    finish = () =>
      if !done then
        done = true
        opened = false
        Viewport.closeWindow(conf)
        after()
    conf = new Viewport.WindowConf(
      body = {
        DslLayer.child(
          new DemoDialogForm(title, fields, submit, extra, () => close(), description)
        ) {}
      },
      widthPx = 460,
      heightPx = if fields.size > 2 then 480 else if fields.isEmpty then 250 else 350,
      onClose = Some(_ => close())
    )
    conf.title = title
    opened = true
    Viewport.addWindow(conf)(using owner)
    conf.leftPx.set(math.max(12, (dom.window.innerWidth - 460) / 2).toDouble)
    conf.topPx.set(math.max(12, (dom.window.innerHeight - conf.heightPx) / 2).toDouble)

  def close(): Unit   = finish()
  def dispose(): Unit =
    disposed = true
    close()
    service.dispose()
