package ember.editor.ui

import ember.editor.core.*
import ember.editor.html.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.layout.TextComponent
import ui.core.render.{Cursor, DomNodes, HostElement, HostNode}

/** Uebersetzt eine Knotenart in eine UI-Komponente.
  *
  * ==Was ein Adapter bekommt und was nicht==
  *
  * §15.1: "Ein Adapter erhaelt immutable Node-Daten und Rendering-Kontext, nicht unbeschraenkte
  * DOM-Schreibrechte." Deshalb liefert [[create]] eine Komponente und [[update]] fuehrt sie nach --
  * beides ohne Cursor, ohne Zugriff auf Geschwister, ohne Moeglichkeit, am Baum zu montieren. Wer
  * die Kinder haelt, ist die Projektion, und wer sie bewegt, ist ausschliesslich die UI-Runtime.
  *
  * Der Regelfall braucht diesen Vertrag gar nicht selbst: [[NodeView.semantic]] leitet ihn aus
  * einer [[HtmlSemantics]] ab. Eigene Adapter sind fuer Atome gedacht, deren Inneres kein
  * Textbereich ist (§8.1) -- ein Bild mit Auswahlrahmen, ein eingebettetes Diagramm.
  *
  * @tparam N
  *   die Knotenart
  */
trait NodeView[N <: EditorNode]:

  def nodeType: NodeType[N]

  /** Erzeugt die Komponente fuer genau diesen Knoten, ohne seine Kinder. */
  def create(node: N, profile: RenderProfile): AbstractComponent

  /** Fuehrt eine bestehende Komponente auf einen neuen Knotenstand nach.
    *
    * Wird nur gerufen, wenn sich der Knoten tatsaechlich geaendert hat. Ein Adapter, der hier
    * bedingungslos alles neu schreibt, macht §15.1s "unveraenderte Nodes werden nicht erneut
    * komponiert" zunichte -- nicht durch Neubau, aber durch Schreibzugriffe ohne Anlass.
    */
  def update(component: AbstractComponent, node: N, profile: RenderProfile): Unit

  /** Whether `component` can still be brought to `node`, or has to be replaced.
    *
    * §15.1: "Ein typwechselnder Node unter gleicher ID ist eine explizite View-Ersetzung." This is
    * where that judgement is made -- the adapter knows what it built, and the projection does not.
    * Answering `false` costs a remount of exactly this node; answering `true` when it is not true
    * leaves a `<p>` standing where a heading belongs.
    *
    * No default: there are two kinds of adapter and both have a real answer. A default would be a
    * guess in the only place where guessing is visible to the reader.
    */
  def accepts(component: AbstractComponent, node: N, profile: RenderProfile): Boolean

object NodeView:

  /** Leitet einen Adapter aus der semantischen Beschreibung ab.
    *
    * Der Weg fuer alles, was sich als Tag mit Attributen oder als Textlauf darstellen laesst --
    * also fuer nahezu jeden Dokumentknoten. Dass SSR und Browser dieselbe Beschreibung benutzen,
    * ist damit keine Absprache, sondern dieselbe Zeile Code.
    */
  def semantic[N <: EditorNode](semantics: HtmlSemantics[N]): NodeView[N] =
    new NodeView[N]:
      val nodeType: NodeType[N] = semantics.nodeType

      def create(node: N, profile: RenderProfile): AbstractComponent =
        semantics.shapeOf(node, profile) match
          case HtmlShape.Element(tag, attributes, inner, textBlock) =>
            val element = new ContainerElement(tag, profile == RenderProfile.Editor && textBlock)
            element.setAttributes(attributes)
            element.setInner(inner)
            element
          case HtmlShape.TextRun(tag, value, attributes, marks) =>
            val element = new TextRunElement(tag)
            element.setAttributes(attributes)
            element.setMarkTags(marks)
            element.setText(value)
            element

      def accepts(component: AbstractComponent, node: N, profile: RenderProfile): Boolean =
        (semantics.shapeOf(node, profile), component) match
          case (HtmlShape.Element(tag, _, inner, textBlock), element: ContainerElement) =>
            tag == element.tagName && inner == element.innerTags &&
            element.editingTextBlock == (profile == RenderProfile.Editor && textBlock)
          case (HtmlShape.TextRun(tag, _, _, _), element: TextRunElement) => tag == element.tagName
          case _                                                          => false

      def update(component: AbstractComponent, node: N, profile: RenderProfile): Unit =
        (semantics.shapeOf(node, profile), component) match
          case (HtmlShape.Element(_, attributes, _, _), element: ContainerElement) =>
            element.setAttributes(attributes)
          case (HtmlShape.TextRun(_, value, attributes, marks), element: TextRunElement) =>
            element.setAttributes(attributes)
            // Before the text: rebuilding the chain replaces the text component, so a value
            // written first would be thrown away with it.
            element.setMarkTags(marks)
            // Gleicher Wert schreibt nicht -- der No-op-Vertrag von `TextComponent`, in P08 im
            // Browser mit einem MutationObserver belegt.
            element.setText(value)
          case _ =>
            // `accepts` said yes and then this did not match -- the two are inconsistent, which
            // is a bug in the adapter and not in the document.
            throw EditorContractViolation(
              s"Der Adapter fuer `${node.id.value}` widerspricht seinem eigenen `accepts`."
            )
