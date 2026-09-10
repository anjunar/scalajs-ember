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
  * Das Textkind ueberlebt jede Textaenderung: [[spliceText]] reicht bis zu
  * `CharacterData.replaceData` durch, und ein neuer Textknoten naehme Caret und Selection mit
  * ins Grab. Das ist der Unterschied, um den es §15.1 bei langen Absaetzen geht.
  *
  * Genau eine Aenderung tauscht es doch aus, und §15.1 erlaubt sie ausdruecklich: ein Wechsel
  * der Markierungen ([[setMarkTags]]). Dort steht, warum es nicht anders geht.
  */
final class TextRunElement(tagName: String) extends SemanticElement(tagName):

  private var content = new TextComponent()
  private var tags    = Vector.empty[String]
  private var chain: AbstractComponent = null

  override def compose(cursor: Cursor): Unit =
    super.compose(cursor)
    chain = Runtime.mount(build(), cursor, Some(this))

  def setText(value: String): Unit = content.setText(value)

  def spliceText(start: Int, deleteCount: Int, inserted: String): Unit =
    content.spliceText(start, deleteCount, inserted)

  def text: String = content.getText

  def markTags: Vector[String] = tags

  /** Replaces the inner semantic tags.
    *
    * §15.1 allows this and names its price in the same sentence: "Mark-Aenderungen koennen
    * semantische Innentags ersetzen und benoetigen Selection-Restoration." The text node inside
    * really is a new one afterwards -- there is no way to turn `<em>` into `<strong>` in place --
    * so a caret standing in it has to be put back. That restoration is the `SelectionPort`'s job
    * (P21); until it exists, this is the one operation in the projection that does not preserve
    * a caret, and it is the only one §15.1 permits not to.
    *
    * The wrapper itself survives. That is the point of having one: the node ID stays on an
    * element that formatting does not touch.
    */
  def setMarkTags(next: Vector[String]): Unit =
    if next != tags then
      tags = next
      if isBound then rebuild()

  private def build(): AbstractComponent =
    tags.foldRight[AbstractComponent](content)((tag, inner) => new MarkElement(tag, inner))

  private def rebuild(): Unit =
    val carried = content.getText
    if chain != null then Runtime.unmount(chain)
    content = new TextComponent(carried)
    chain = Runtime.mount(build(), Runtime.contentCursor(this), Some(this))

/** One semantic tag around a text run: `strong`, `em`, `code`.
  *
  * Carries no attributes and no identity. A mark is not a node (§8.2) -- it has no ID, and
  * nothing outside this chain ever needs to find it again.
  */
private final class MarkElement(val tagName: String, inner: AbstractComponent)
    extends AbstractComponent:

  override def compose(cursor: Cursor): Unit = Runtime.mount(inner, cursor, Some(this)): Unit

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

  /** Whether `component` can still be brought to `node`, or has to be replaced.
    *
    * §15.1: "Ein typwechselnder Node unter gleicher ID ist eine explizite View-Ersetzung." This
    * is where that judgement is made -- the adapter knows what it built, and the projection
    * does not. Answering `false` costs a remount of exactly this node; answering `true` when it
    * is not true leaves a `<p>` standing where a heading belongs.
    *
    * No default: there are two kinds of adapter and both have a real answer. A default would
    * be a guess in the only place where guessing is visible to the reader.
    */
  def accepts(component: AbstractComponent, node: N, profile: RenderProfile): Boolean

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
          case HtmlShape.TextRun(tag, value, attributes, marks) =>
            val element = new TextRunElement(tag)
            element.setAttributes(attributes)
            element.setMarkTags(marks)
            element.setText(value)
            element

      def accepts(component: AbstractComponent, node: N, profile: RenderProfile): Boolean =
        (semantics.shapeOf(node, profile), component) match
          case (HtmlShape.Element(tag, _), element: ContainerElement)    => tag == element.tagName
          case (HtmlShape.TextRun(tag, _, _, _), element: TextRunElement) => tag == element.tagName
          case _                                                          => false

      def update(component: AbstractComponent, node: N, profile: RenderProfile): Unit =
        (semantics.shapeOf(node, profile), component) match
          case (HtmlShape.Element(_, attributes), element: ContainerElement) =>
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

  /** Brings a component to a node. `false` means it no longer fits and must be replaced. */
  private[jfx] def update(
      component: AbstractComponent,
      node: EditorNode,
      profile: RenderProfile
  ): Boolean =
    viewFor(node).exists(refresh(_, component, node, profile))

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
  ): Boolean =
    view.nodeType.project(node).exists { typed =>
      if view.accepts(component, typed, profile) then
        view.update(component, typed, profile)
        true
      else false
    }

object ViewSupport:

  val empty: ViewSupport = new ViewSupport(Vector.empty)

  def of(views: NodeView[?]*): ViewSupport = new ViewSupport(views.toVector)

  /** Leitet die Adapter aus einer Menge semantischer Beschreibungen ab. */
  def semantic(support: HtmlSupport): ViewSupport =
    new ViewSupport(support.entries.map(fromSemantics))

  private def fromSemantics[N <: EditorNode](semantics: HtmlSemantics[N]): NodeView[?] =
    NodeView.semantic(semantics)
