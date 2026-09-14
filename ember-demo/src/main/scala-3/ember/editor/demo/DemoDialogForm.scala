package ember.editor.demo

import ember.editor.core.EditorError
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.{Button, TextComponent}
import ui.core.render.{Cursor, DomNodes}
import ui.core.state.Property
import org.scalajs.dom

/** Ordinary UI content mounted directly by Viewport.WindowConf. */
final class DemoDialogForm(
    title: String,
    fields: Vector[(String, String)],
    submit: Vector[String] => Either[EditorError, Unit],
    extra: Option[(String, Vector[String] => Either[EditorError, Unit])],
    close: () => Unit,
    description: String = ""
) extends AbstractComponent {
  val tagName                                = "form"
  private val error                          = Property("")
  private var inputs                         = Vector.empty[AbstractComponent]
  private var cancelButton: Button           = null
  override def compose(cursor: Cursor): Unit = {
    addClass("demo-dialog")
    setAttribute("role", "dialog")
    setAttribute("aria-label", title)
    if (description.nonEmpty) Runtime.mount(TextComponent(description), cursor, Some(this))
    inputs = fields.map { (caption, initial) =>
      var input: AbstractComponent = null
      val label                    = new AbstractComponent {
        val tagName                                = "label"
        override def compose(cursor: Cursor): Unit = {
          Runtime.mount(TextComponent(caption), cursor, Some(this))
          input = new AbstractComponent {
            val tagName                                = "input"
            override def compose(cursor: Cursor): Unit = {
              setAttribute("type", "text")
              setAttribute("value", initial)
            }
          }
          Runtime.mount(input, cursor, Some(this))
        }
      }
      Runtime.mount(label, cursor, Some(this))
      input
    }
    val status = new AbstractComponent {
      val tagName                                = "p"
      override def compose(cursor: Cursor): Unit = {
        setAttribute("role", "alert")
        Runtime.mount(TextComponent.bind(error), cursor, Some(this))
      }
    }
    Runtime.mount(status, cursor, Some(this))
    def button(label: String, kind: String)(action: => Unit): Unit = {
      val control = new Button()
      Runtime.mount(control, cursor, Some(this))
      control.label(label)
      control.buttonType(kind)
      if (label == "Abbrechen") cancelButton = control
      if (kind == "button") control.onClickHandler(_ => action)
    }
    button("Übernehmen", "submit")(())
    extra.foreach((label, action) => button(label, "button")(complete(action(values))))
    button("Abbrechen", "button")(close())
    onHandler("submit") { event => event.preventDefault(); complete(submit(values)) }
    onHandler("keydown") { event =>
      if (event.raw.asInstanceOf[dom.KeyboardEvent].key == "Escape") {
        event.preventDefault()
        event.stopPropagation()
        close()
      }
    }
  }
  override def afterCompose(cursor: Cursor): Unit = if (cursor.isBrowser)
    inputs.headOption
      .orElse(Option(cancelButton))
      .foreach(input => DomNodes.raw(input.host).asInstanceOf[dom.HTMLElement].focus())
  private def values =
    inputs.map(input => DomNodes.raw(input.host).asInstanceOf[dom.HTMLInputElement].value)
  private def complete(result: Either[EditorError, Unit]): Unit =
    result.fold(e => error.set(e.message), _ => close())
}
