package ember.editor.ui

import ember.editor.core.*
import ember.editor.html.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.TextComponent
import ui.core.render.{Cursor, DomNodes, HostElement, HostNode}

/** Eine UI-Komponente mit semantischem Tag und Attributen.
  *
  * Der gemeinsame Nenner fuer alle Dokumentknoten. Ein Adapter braucht dafuer keine eigene Klasse
  * -- und bekommt damit auch keine unbeschraenkten DOM-Schreibrechte (§15.1): er beschreibt Tag und
  * Attribute, das Schreiben besorgt diese Komponente.
  */
abstract class SemanticElement(val tagName: String) extends AbstractComponent:

  private var applied = Vector.empty[HtmlAttribute]

  override def compose(cursor: Cursor): Unit = write(applied)

  /** Fuehrt die Attribute nach. Unveraenderte werden nicht erneut geschrieben. */
  def setAttributes(next: Vector[HtmlAttribute]): Unit =
    if next != applied then
      val previous = applied
      applied = next
      if isBound then
        // Entfallene zuerst, sonst bliebe ein Attribut stehen, das der neue Stand nicht kennt.
        previous
          .filterNot(attribute => next.exists(_.name == attribute.name))
          .foreach(attribute => host.removeAttribute(attribute.name))
        write(next.filterNot(previous.contains))

  private def write(attributes: Vector[HtmlAttribute]): Unit =
    attributes.foreach(attribute => host.setAttribute(attribute.name, attribute.value))
