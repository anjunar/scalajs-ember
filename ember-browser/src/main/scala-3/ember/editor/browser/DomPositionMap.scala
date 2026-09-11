package ember.editor.browser

import ember.editor.core.*
import ember.editor.jfx.{ContainerElement, DocumentView, TextRunElement}
import jfx.core.render.DomNodes
import org.scalajs.dom

/** A DOM position: a container node and an offset in it -- the shape a `Range` endpoint has. */
final case class DomPosition(node: dom.Node, offset: Int)

/** Why a position could not be mapped. */
enum PositionProblem:

  /** The DOM node is not inside the editing host (§11: the port reads only its own host). */
  case OutsideHost

  /** No document node owns this DOM node -- something someone else put into the host. */
  case Unowned

  /** The document node has no mounted component; the projection has not caught up. */
  case NotProjected(node: NodeId)

  /** The point names a node the document does not have. */
  case NoSuchNode(node: NodeId)

  /** The position is inside an atom, whose inside is not editor text (§8.1, §11). */
  case InsideAtom(node: NodeId)

  def render: String = this match
    case OutsideHost      => "Die Position liegt ausserhalb des Editing-Hosts."
    case Unowned          => "Zu dieser DOM-Position gehoert kein Dokumentknoten."
    case NotProjected(id) => s"`${id.value}` ist nicht projiziert."
    case NoSuchNode(id)   => s"`${id.value}` gibt es im Dokument nicht."
    case InsideAtom(id)   => s"`${id.value}` ist atomar; sein Inneres ist kein Textbereich."

/** The explicit mapping table between model points and DOM positions (§11).
  *
  * ==Why a table and not a count==
  *
  * §11 names the problem and the remedy in one sentence: "Bei DOM-Elementoffsets zaehlen
  * DOM-Kinder einschliesslich Renderhilfen anders als Dokumentkinder; die explizite
  * Mapping-Tabelle loest dies auf." The DOM below a container holds things the document has no
  * word for -- the runtime's keyed-group comment anchors, the inner `<code>` of a code block, a
  * placeholder `<br>`. Counting DOM children and calling the result a child offset is wrong by a
  * different amount for every node.
  *
  * So nothing here counts DOM children. Every offset is derived from where the hosts of the
  * document's own children actually are, and every lookup goes through the projection -- the one
  * thing that knows.
  *
  * ==Why it asks the projection and not an attribute==
  *
  * `data-ember-node` is a decision of the render profile (§19.1) and belongs to whoever wrote the
  * semantics. A port that parsed it would work for those semantics only, and would keep working,
  * wrongly, when a profile stopped emitting it. [[ember.editor.jfx.DocumentView.componentFor]] is
  * the same index the projection uses to move and update nodes; there is no second one.
  */
final class DomPositionMap(view: DocumentView, scope: BrowserScope):

  // -----------------------------------------------------------------------------------------
  // Model -> DOM
  // -----------------------------------------------------------------------------------------

  /** Where a model point sits in the DOM.
    *
    * | Punkt | DOM-Position |
    * | --- | --- |
    * | `Text(run, o)` | der Textknoten des Laufs, Offset `o` -- beide messen UTF-16 (§11) |
    * | `Children(p, i)`, `i < n` | im Inhaltselement von `p`, vor dem Host des `i`-ten Kindes |
    * | `Children(p, n)` | ebenda, hinter dem Host des letzten Kindes |
    * | `Children(p, 0)`, `p` leer | ebenda, Offset 0 -- kein Dokumentkind steht davor |
    */
  def toDom(point: Point, document: DocumentRead): Either[PositionProblem, DomPosition] =
    point match
      case Point.Text(id, offset, _) =>
        document.node(id) match
          case None                 => Left(PositionProblem.NoSuchNode(id))
          case Some(_: AtomNode)    => Left(PositionProblem.InsideAtom(id))
          case Some(text: TextNode) =>
            textNodeOf(id).map(node => DomPosition(node, offset.max(0).min(text.text.length)))
          case Some(_) =>
            // A text point on a node without text. The document validator rejects it, so getting
            // here means someone assembled a point by hand.
            Left(PositionProblem.Unowned)

      case Point.Children(id, offset, _) =>
        document.node(id) match
          case None              => Left(PositionProblem.NoSuchNode(id))
          case Some(_: AtomNode) => Left(PositionProblem.InsideAtom(id))
          case Some(element: ElementNode) =>
            contentElementOf(id).flatMap { container =>
              val children = element.children
              val wanted   = offset.max(0).min(children.length)

              if children.isEmpty then Right(DomPosition(container, 0))
              else if wanted < children.length then
                hostOf(children(wanted)).flatMap(child =>
                  domIndexOf(container, child).map(DomPosition(container, _))
                )
              else
                hostOf(children.last).flatMap(child =>
                  domIndexOf(container, child).map(index => DomPosition(container, index + 1))
                )
            }
          case Some(_) => Left(PositionProblem.Unowned)

  // -----------------------------------------------------------------------------------------
  // DOM -> Model
  // -----------------------------------------------------------------------------------------

  /** Which model point a DOM position means.
    *
    * A DOM position can name things the document has no word for: the inside of a mark chain, a
    * comment anchor, the `<code>` of a code block, a spot inside an atom. Each of them belongs to
    * exactly one document node, and [[nodeAt]] finds it.
    */
  def toPoint(position: DomPosition, document: DocumentRead): Either[PositionProblem, Point] =
    if !scope.contains(position.node) then Left(PositionProblem.OutsideHost)
    else
      nodeAt(position.node, document) match
        case None     => Left(PositionProblem.Unowned)
        case Some(id) =>
          document.node(id) match
            case None => Left(PositionProblem.NoSuchNode(id))

            // §11 and P21's acceptance: "Native Inputs innerhalb Atom-Views werden nicht als
            // Editortext behandelt." A position inside an atom is a position around it, in its
            // parent -- never an offset into whatever the atom happens to render.
            case Some(_: AtomNode) => boundaryAround(id, position, document)

            case Some(text: TextNode) =>
              if DomKinds.isText(position.node) then
                Right(Point.textBefore(id, position.offset.max(0).min(text.text.length)))
              // An element position in the run's wrapper or mark chain. The chain holds exactly
              // one text node, so offset 0 is before it and anything else behind it.
              else Right(Point.textBefore(id, if position.offset == 0 then 0 else text.text.length))

            case Some(element: ElementNode) =>
              if DomKinds.isText(position.node) then
                // Text sitting directly in a container is not something the projection writes.
                // It is a native insertion, and the boundary in front of it is the honest
                // answer -- P22 imports the text itself.
                childBoundaryBefore(element, position.node, id)
              else
                Right(
                  Point.childrenBefore(
                    id,
                    documentOffsetIn(element, position.node, position.offset)
                  )
                )

            case Some(_) => Left(PositionProblem.Unowned)

  /** The document node that owns a DOM node, found by descending the document, not the DOM.
    *
    * ==Why downwards==
    *
    * Walking up and asking "is this element a node host?" needs a DOM-to-node index, and that
    * index would be a second ownership list -- what §15.1 rules out for the projection, and no
    * better here. Descending asks only what the DOM already answers: `Node.contains`. Each step
    * takes the one child whose host contains the target; when none does, the target belongs to
    * the node reached, because it sits in that node's own markup. A mark chain, an inner tag and
    * a group anchor are exactly that.
    */
  def nodeAt(target: dom.Node, document: DocumentRead): Option[NodeId] =
    if !scope.contains(target) then None
    else
      hostOf(document.rootId).toOption.flatMap { root =>
        if !(root eq target) && !root.contains(target) then None
        else
          var current = document.rootId
          var settled = false
          while !settled do
            val next = childrenOf(current, document).find { child =>
              hostOf(child).toOption.exists(host => (host eq target) || host.contains(target))
            }
            next match
              case Some(child) => current = child
              case None        => settled = true
          Some(current)
      }

  /** The atom whose markup a DOM node sits in, if any.
    *
    * §15.2 requires this before any input is processed: "native Inputs/Textareas in Atom-Views,
    * unmanaged Bereiche und verschachtelte Editoren gehoeren nicht automatisch zum aeusseren
    * Editor." A caret in a control an atom renders is that control's caret. The document has a
    * position for the atom -- the boundary in its parent -- but nobody may claim the user went
    * there because a native field took focus.
    *
    * A position anchored on the atom element itself counts as inside it: a DOM position with the
    * atom as container addresses its children, not its place among siblings.
    */
  def atomAround(target: dom.Node, document: DocumentRead): Option[NodeId] =
    nodeAt(target, document).filter(id => document.node(id).exists(_.isInstanceOf[AtomNode]))

  /** The host element of a projected node. */
  def hostOf(id: NodeId): Either[PositionProblem, dom.Element] =
    view.componentFor(id) match
      case None => Left(PositionProblem.NotProjected(id))
      case Some(component) =>
        if !component.isBound then Left(PositionProblem.NotProjected(id))
        else
            DomNodes.option(component.host).flatMap(DomKinds.asElement) match
              case Some(element) => Right(element)
              case None          => Left(PositionProblem.NotProjected(id))

  // -----------------------------------------------------------------------------------------
  // Die Stellen, an denen die Renderhilfen auffallen
  // -----------------------------------------------------------------------------------------

  /** The element that really holds a container's children.
    *
    * For a code block that is the `<code>` and not the `<pre>`. The component knows; asking it is
    * the difference between a table and a guess.
    */
  private def contentElementOf(id: NodeId): Either[PositionProblem, dom.Element] =
    view.componentFor(id) match
      case Some(container: ContainerElement) if container.isBound =>
        DomNodes.option(container.contentHost).flatMap(DomKinds.asElement) match
          case Some(element) => Right(element)
          case None          => Left(PositionProblem.NotProjected(id))
      case _ => hostOf(id)

  /** The DOM text node of a run. Public because [[NativeInputReader]] compares against it. */
  def textNodeOf(id: NodeId): Either[PositionProblem, dom.Text] =
    view.componentFor(id) match
      case Some(run: TextRunElement) if run.isBound =>
        run.textHost.flatMap(DomNodes.option).flatMap(DomKinds.asText) match
          case Some(text) => Right(text)
          case None       => Left(PositionProblem.NotProjected(id))
      case _ => Left(PositionProblem.NotProjected(id))

  private def domIndexOf(parent: dom.Node, child: dom.Node): Either[PositionProblem, Int] =
    var index = 0
    var found = -1
    while index < parent.childNodes.length do
      if parent.childNodes(index) eq child then found = index
      index += 1
    if found >= 0 then Right(found) else Left(PositionProblem.Unowned)

  /** Turns a DOM child offset into a document child offset.
    *
    * Counts the document children whose hosts sit before the DOM offset. Group anchors, inner
    * tags and placeholders are not counted, because they are not children -- which is the whole
    * difference §11 warns about.
    */
  private def documentOffsetIn(element: ElementNode, container: dom.Node, domOffset: Int): Int =
    val limit  = domOffset.max(0).min(container.childNodes.length)
    val before = (0 until limit).map(index => container.childNodes(index)).toSet
    element.children.count(child => hostOf(child).toOption.exists(before.contains))

  /** A position around a node rather than inside it. The answer for atoms. */
  private def boundaryAround(
      id: NodeId,
      position: DomPosition,
      document: DocumentRead
  ): Either[PositionProblem, Point] =
    document.parentOf(id) match
      // An atom at the root has no boundary to fall back to. A valid document cannot have one --
      // the root is an ElementNode -- and reporting it beats inventing a point.
      case None => Left(PositionProblem.InsideAtom(id))
      case Some(parentId) =>
        val index = childrenOf(parentId, document).indexOf(id)
        if index < 0 then Left(PositionProblem.InsideAtom(id))
        else
          // Which side. A DOM offset inside an atom means nothing to the document, so the only
          // reading that can be defended is "before it" -- unless the position is at the very end
          // of the atom's own markup, which is the one case that clearly means "behind".
          val behind = position.offset >= DomKinds.extentOf(position.node)
          Right(Point.childrenBefore(parentId, if behind then index + 1 else index))

  private def childBoundaryBefore(
      element: ElementNode,
      target: dom.Node,
      id: NodeId
  ): Either[PositionProblem, Point] =
    val index = Option(target.parentNode)
      .map { parent =>
        element.children.count(child => hostOf(child).toOption.exists(precedes(parent, _, target)))
      }
      .getOrElse(0)
    Right(Point.childrenBefore(id, index))

  private def precedes(parent: dom.Node, host: dom.Node, target: dom.Node): Boolean =
    (domIndexOf(parent, host), domIndexOf(parent, target)) match
      case (Right(before), Right(after)) => before < after
      case _                             => false

  private def childrenOf(id: NodeId, document: DocumentRead): Vector[NodeId] =
    document.node(id) match
      case Some(element: ElementNode) => element.children
      case _                          => Vector.empty
