package ember.editor.toolbar

import ember.editor.core.*
import ember.editor.browser.*
import org.scalajs.dom
import scala.scalajs.js
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.render.{Cursor, DomCursor, DomNodes}
import ui.core.layout.{Button, TextComponent}
import ui.core.state.Property

/** Native modal semantics, rendered through the same UI runtime as every other view. */
private[toolbar] final class EditorDialog(
    title: String,
    fields: Vector[(String, String)],
    submit: Vector[String] => Either[EditorError, Unit],
    extra: Option[(String, Vector[String] => Either[EditorError, Unit])],
    closed: () => Unit
) extends AbstractComponent:
  val tagName         = "dialog"
  private val error   = Property("")
  private var inputs  = Vector.empty[AbstractComponent]
  private var closing = false

  override def compose(cursor: Cursor): Unit =
    addClass("ember-dialog")
    setAttribute("aria-label", title)
    val heading = new AbstractComponent:
      val tagName                                = "h2"
      override def compose(cursor: Cursor): Unit =
        Runtime.mount(TextComponent(title), cursor, Some(this))
    Runtime.mount(heading, cursor, Some(this))
    val form = new AbstractComponent:
      val tagName                                = "form"
      override def compose(cursor: Cursor): Unit =
        inputs = fields.map { (label, initial) =>
          var input: AbstractComponent = null
          val field                    = new AbstractComponent:
            val tagName                                = "label"
            override def compose(cursor: Cursor): Unit =
              Runtime.mount(TextComponent(label), cursor, Some(this))
              input = new AbstractComponent:
                val tagName                                = "input"
                override def compose(cursor: Cursor): Unit =
                  setAttribute("type", "text")
                  setAttribute("value", initial)
              Runtime.mount(input, cursor, Some(this))
          Runtime.mount(field, cursor, Some(this))
          input
        }
        val status = new AbstractComponent:
          val tagName                                = "p"
          override def compose(cursor: Cursor): Unit =
            setAttribute("role", "alert")
            Runtime.mount(TextComponent.bind(error), cursor, Some(this))
        Runtime.mount(status, cursor, Some(this))
        def button(label: String, kind: String)(click: => Unit): Unit =
          val b = new Button()
          Runtime.mount(b, cursor, Some(this))
          b.label(label)
          b.buttonType(kind)
          if kind == "button" then b.onClickHandler(_ => click)
        button("Übernehmen", "submit")(())
        extra.foreach { (label, callback) =>
          button(label, "button")(applyResult(callback(values)))
        }
        button("Abbrechen", "button")(finish())
        onHandler("submit") { event =>
          event.preventDefault()
          applyResult(submit(values))
        }
    Runtime.mount(form, cursor, Some(this))
    onHandler("cancel") { event => event.preventDefault(); finish() }
    onHandler("close") { _ => finish() }
    onHandler("keydown") { event =>
      val key = event.raw.asInstanceOf[dom.KeyboardEvent]
      if key.key == "Tab" && !key.altKey && !key.ctrlKey && !key.metaKey then
        val element = DomNodes.raw(host).asInstanceOf[dom.HTMLElement]
        val stops   = element.querySelectorAll("input:not([disabled]), button:not([disabled])")
        if stops.length > 0 then
          val first  = stops(0).asInstanceOf[dom.HTMLElement]
          val last   = stops(stops.length - 1).asInstanceOf[dom.HTMLElement]
          val active = element.ownerDocument.activeElement
          if key.shiftKey && active == first then
            event.preventDefault()
            last.focus()
          else if !key.shiftKey && active == last then
            event.preventDefault()
            first.focus()
    }

  private def values: Vector[String] =
    inputs.map(input => DomNodes.raw(input.host).asInstanceOf[dom.HTMLInputElement].value)
  private def applyResult(result: Either[EditorError, Unit]): Unit =
    result.fold(problem => error.set(problem.message), _ => finish())
  private def finish(): Unit = if !closing then
    closing = true
    closed()

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
