package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.browser.*
import org.scalajs.dom
import scala.scalajs.js
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.{Cursor, DomCursor, DomNodes}
import ui.core.layout.{Button, TextComponent}
import ui.core.state.Property

/** Owns exactly one mounted dialog, including its return-focus context. */
final class EditorDialogHost(
    service: EditorDialogService,
    selection: SelectionPort,
    container: dom.Element,
    announce: String => Unit
):
  private var opened = Option.empty[(EditorDialog, DialogTarget, Option[dom.HTMLElement], Boolean)]
  private var disposed = false

  def open(title: String, fields: Vector[(String, String)])(
      submit: (DialogTarget, Vector[String]) => Either[EditorError, Unit],
      extra: Option[(String, (DialogTarget, Vector[String]) => Either[EditorError, Unit])] = None
  ): Either[EditorError, Unit] =
    if disposed || opened.nonEmpty then
      Left(ToolbarFailure("Ein Dialog ist bereits geöffnet oder geschlossen."))
    else
      if selection.scope.focusWithin then selection.importNative(): Unit
      service.capture().map { target =>
        val wasEditor = selection.scope.focusWithin
        val previous  = Option(container.ownerDocument.asInstanceOf[dom.HTMLDocument].activeElement)
          .map(_.asInstanceOf[dom.HTMLElement])
        val dialog = new EditorDialog(
          title,
          fields,
          values => submit(target, values),
          extra.map((label, callback) =>
            label -> ((values: Vector[String]) => callback(target, values))
          ),
          () => close(restore = true)
        )
        Runtime.mount(dialog, DomCursor.root(container))
        opened = Some((dialog, target, previous, wasEditor))
        DomNodes.raw(dialog.host).asInstanceOf[js.Dynamic].showModal()
      }

  def close(restore: Boolean = true): Unit = opened.foreach {
    (dialog, target, previous, wasEditor) =>
      opened = None
      val element = DomNodes.raw(dialog.host).asInstanceOf[dom.HTMLElement]
      val active  = Option(element.ownerDocument.activeElement).map(_.asInstanceOf[dom.HTMLElement])
      val ownedFocus = active.exists(element.contains)
      val mapped     = service.resolve(target)
      service.cancel(target)
      element.asInstanceOf[js.Dynamic].close()
      Runtime.unmount(dialog)
      if restore && ownedFocus then
        if wasEditor then
          selection.scope.focus()
          // A successful command already set the mapped selection; cancel resolves the bookmark.
          val restored = if target.applied then service.session.selection
          else mapped.toOption.orElse(service.session.selection)
          selection.write(restored, WriteIntent.Explicit): Unit
        else previous.filter(_.isConnected).foreach(_.focus())
      else active.filter(node => !element.contains(node) && node.isConnected).foreach(_.focus())
      if restore && !target.applied && mapped.isLeft then
        announce("Die ursprüngliche Textposition ist nicht mehr verfügbar.")
  }

  def dispose(): Unit =
    close(restore = false)
    disposed = true
