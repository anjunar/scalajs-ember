package ember.editor.jfx

import ember.editor.core.*
import ember.editor.html.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.layout.TextComponent
import jfx.core.render.Cursor

/** Eine JFX-Komponente mit semantischem Tag und Attributen.
  *
  * Der gemeinsame Nenner fuer alle Dokumentknoten. Ein Adapter braucht dafuer keine eigene
  * Klasse -- und bekommt damit auch keine unbeschraenkten DOM-Schreibrechte (§15.1): er
  * beschreibt Tag und Attribute, das Schreiben besorgt diese Komponente.
  */
sealed abstract class SemanticElement(val tagName: String) extends AbstractComponent:

  private var applied = Vector.empty[HtmlAttribute]

  override def compose(cursor: Cursor): Unit = write(applied)

  /** Fuehrt die Attribute nach. Unveraenderte werden nicht erneut geschrieben. */
  def setAttributes(next: Vector[HtmlAttribute]): Unit =
    if next != applied then
      val previous = applied
      applied = next
      if isBound then
        // Entfallene zuerst, sonst bliebe ein Attribut stehen, das der neue Stand nicht kennt.
        previous.filterNot(attribute => next.exists(_.name == attribute.name))
          .foreach(attribute => host.removeAttribute(attribute.name))
        write(next.filterNot(previous.contains))

  private def write(attributes: Vector[HtmlAttribute]): Unit =
    attributes.foreach(attribute => host.setAttribute(attribute.name, attribute.value))

/** Ein Container: seine Kinder haelt eine [[jfx.core.statement.KeyedChildren]]-Gruppe.
  *
  * Die Gruppe haengt die Projektion vor dem Mount ein ([[attach]]), montiert wird sie hier --
  * waehrend `compose`, also im selben Durchgang wie der Container selbst. Ein Nachtragen
  * hinterher waere nicht nur umstaendlich, es waere falsch: der Cursor steht dann nicht mehr
  * dort, wo die Kinder hingehoeren.
  */
final class ContainerElement(tagName: String) extends SemanticElement(tagName):

  private var group: Option[AbstractComponent] = None

  private[jfx] def attach(children: AbstractComponent): Unit =
    require(group.isEmpty && !isBound, "Die Kindergruppe steht vor dem Mount fest.")
    group = Some(children)

  override def compose(cursor: Cursor): Unit =
    super.compose(cursor)
    group.foreach(children => Runtime.mount(children, cursor, Some(this)))

/** Ein Textlauf mit stabilem Wrapper (§15.1).
  *
  * Das Textkind gehoert dieser Komponente und wird nie ausgetauscht -- ein neuer Textknoten
  * naehme Caret und Selection mit ins Grab. [[spliceText]] reicht bis zu `CharacterData
  * .replaceData` durch; das ist der Unterschied, um den es §15.1 bei langen Absaetzen geht.
  */
final class TextRunElement(tagName: String) extends SemanticElement(tagName):

  private val content = new TextComponent()

  override def compose(cursor: Cursor): Unit =
    super.compose(cursor)
    Runtime.mount(content, cursor, Some(this))

  def setText(value: String): Unit = content.setText(value)

  def spliceText(start: Int, deleteCount: Int, inserted: String): Unit =
    content.spliceText(start, deleteCount, inserted)

  def text: String = content.getText

/** Uebersetzt eine Knotenart in eine JFX-Komponente.
  *
  * ==Was ein Adapter bekommt und was nicht==
  *
  * §15.1: "Ein Adapter erhaelt immutable Node-Daten und Rendering-Kontext, nicht
  * unbeschraenkte DOM-Schreibrechte." Deshalb liefert [[create]] eine Komponente und
  * [[update]] fuehrt sie nach -- beides ohne Cursor, ohne Zugriff auf Geschwister, ohne
  * Moeglichkeit, am Baum zu montieren. Wer die Kinder haelt, ist die Projektion, und wer sie
  * bewegt, ist ausschliesslich die JFX-Runtime.
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

object NodeView:

  /** Leitet einen Adapter aus der semantischen Beschreibung ab.
    *
    * Der Weg fuer alles, was sich als Tag mit Attributen oder als Textlauf darstellen laesst --
    * also fuer nahezu jeden Dokumentknoten. Dass SSR und Browser dieselbe Beschreibung
    * benutzen, ist damit keine Absprache, sondern dieselbe Zeile Code.
    */
  def semantic[N <: EditorNode](semantics: HtmlSemantics[N]): NodeView[N] =
    new NodeView[N]:
      val nodeType: NodeType[N] = semantics.nodeType

      def create(node: N, profile: RenderProfile): AbstractComponent =
        semantics.shapeOf(node, profile) match
          case HtmlShape.Element(tag, attributes) =>
            val element = new ContainerElement(tag)
            element.setAttributes(attributes)
            element
          case HtmlShape.TextRun(tag, value, attributes) =>
            val element = new TextRunElement(tag)
            element.setAttributes(attributes)
            element.setText(value)
            element

      def update(component: AbstractComponent, node: N, profile: RenderProfile): Unit =
        (semantics.shapeOf(node, profile), component) match
          case (HtmlShape.Element(tag, attributes), element: ContainerElement) =>
            checkTag(node, tag, element)
            element.setAttributes(attributes)
          case (HtmlShape.TextRun(tag, value, attributes), element: TextRunElement) =>
            checkTag(node, tag, element)
            element.setAttributes(attributes)
            // Gleicher Wert schreibt nicht -- der No-op-Vertrag von `TextComponent`, in P08 im
            // Browser mit einem MutationObserver belegt.
            element.setText(value)
          case _ => throw replacement(node)

      /** §15.1: "Ein typwechselnder Node unter gleicher ID ist eine explizite
        * View-Ersetzung." Sie gibt es noch nicht, und ein still stehengebliebenes `<p>` unter
        * einer Ueberschrift waere die schlechtere Auskunft.
        */
      private def checkTag(node: N, tag: String, element: SemanticElement): Unit =
        if tag != element.tagName then throw replacement(node)

      private def replacement(node: N): EditorContractViolation =
        EditorContractViolation(
          s"Die Gestalt von `${node.id.value}` passt nicht zur bestehenden Komponente. " +
            "Ein Typwechsel unter gleicher ID ist eine ausdrueckliche Ersetzung (§15.1)."
        )

/** Registry der Adapter. Heterogen, mit dem Deskriptor als Typzeugen. */
final class ViewSupport private (val views: Vector[NodeView[?]]):

  def viewFor(node: EditorNode): Option[NodeView[?]] =
    views.find(_.nodeType.project(node).isDefined)

  def ++(other: ViewSupport): ViewSupport = new ViewSupport(views ++ other.views)

  private[jfx] def create(node: EditorNode, profile: RenderProfile): AbstractComponent =
    viewFor(node) match
      case Some(view) => build(view, node, profile)
      case None =>
        throw EditorContractViolation(
          s"Keine NodeView fuer `${node.id.value}` (${node.getClass.getSimpleName}) registriert."
        )

  private[jfx] def update(
      component: AbstractComponent,
      node: EditorNode,
      profile: RenderProfile
  ): Unit =
    viewFor(node).foreach(refresh(_, component, node, profile))

  private def build[N <: EditorNode](
      view: NodeView[N],
      node: EditorNode,
      profile: RenderProfile
  ): AbstractComponent =
    view.nodeType.project(node).map(view.create(_, profile)).getOrElse(
      throw EditorContractViolation(s"Der Adapter weist `${node.id.value}` zurueck.")
    )

  private def refresh[N <: EditorNode](
      view: NodeView[N],
      component: AbstractComponent,
      node: EditorNode,
      profile: RenderProfile
  ): Unit =
    view.nodeType.project(node).foreach(view.update(component, _, profile))

object ViewSupport:

  val empty: ViewSupport = new ViewSupport(Vector.empty)

  def of(views: NodeView[?]*): ViewSupport = new ViewSupport(views.toVector)

  /** Leitet die Adapter aus einer Menge semantischer Beschreibungen ab. */
  def semantic(support: HtmlSupport): ViewSupport =
    new ViewSupport(support.entries.map(fromSemantics))

  private def fromSemantics[N <: EditorNode](semantics: HtmlSemantics[N]): NodeView[?] =
    NodeView.semantic(semantics)
