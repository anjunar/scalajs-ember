package ember.editor.clipboard

import ember.editor.core.*
import ember.editor.richtext.*

final case class ClipboardError(message: String) extends EditorError

/** A validated, detached slice. Open depths count containers below its synthetic root along the
  * first/last branch. IDs are retained on extraction and remapped only when inserting a copy into a
  * receiving document.
  */
final class DocumentFragment private (
    val document: Document,
    val openStart: Int,
    val openEnd: Int
):
  def roots: Vector[NodeId] = document.childrenOf(document.rootId)
  def isEmpty: Boolean      = roots.isEmpty

object DocumentFragment:
  def create(
      document: Document,
      openStart: Int = 0,
      openEnd: Int = 0
  ): Either[ClipboardError, DocumentFragment] =
    def depth(first: Boolean): Int =
      var children = document.childrenOf(document.rootId)
      var result   = 0
      var more     = true
      while children.nonEmpty && more do
        document.node(if first then children.head else children.last) match
          case Some(element: ElementNode) =>
            result += 1
            children = element.children
          case _ => more = false
      result
    Either.cond(
      openStart >= 0 && openEnd >= 0 && openStart <= depth(true) && openEnd <= depth(false),
      new DocumentFragment(document, openStart, openEnd),
      ClipboardError("Ungültige offene Fragmentgrenze.")
    )

  def extract(document: Document, selection: Selection): Either[ClipboardError, DocumentFragment] =
    val errors = SelectionSupport.core.validate(selection, document)
    if errors.nonEmpty then Left(ClipboardError(errors.map(_.render).mkString("; ")))
    else
      val range = selection match
        case r: RangeSelection => Some(r.ordered(document))
        case _                 => None
      val selected = selection match
        case n: NodeSelection => n.normalized(document).nodes
        case _                => Set.empty[NodeId]
      val kept                       = scala.collection.mutable.Map.empty[NodeId, EditorNode]
      def whole(id: NodeId): Boolean =
        if selected.nonEmpty then
          selected.contains(id) || document.ancestorsOf(id).exists(selected.contains)
        else
          range.exists { (start, end) =>
            (document.parentOf(id), document.indexOfChild(id)) match
              case (Some(parent), Some(index)) =>
                document.comparePoints(start, Point.childrenBefore(parent, index)) <= 0 &&
                document.comparePoints(end, Point.childrenBefore(parent, index + 1)) >= 0
              case _ => false
          }
      document.inDocumentOrder.toVector.reverse.foreach {
        case root: RootNode =>
          kept(root.id) = root.copy(children = root.children.filter(kept.contains))
        case text: TextNode =>
          if whole(text.id) then kept(text.id) = text
          else
            range.foreach { (start, end) =>
              if document.comparePoints(end, Point.textBefore(text.id, 0)) > 0 &&
                document.comparePoints(start, Point.textBefore(text.id, text.text.length)) < 0
              then
                val from  = if start.owner == text.id then start.offset else 0
                val until = if end.owner == text.id then end.offset else text.text.length
                if until > from then
                  kept(text.id) = text.copy(text = text.text.substring(from, until))
            }
        case element: ElementNode =>
          val children = element.children.filter(kept.contains)
          if children.nonEmpty || whole(element.id) then
            kept(element.id) = FragmentNodes.children(document, element, children)
        case atom => if whole(atom.id) then kept(atom.id) = atom
      }
      def open(point: Point): Int =
        val chain = document.ancestorsOf(point.owner).filterNot(_ == document.rootId) ++
          (point match
            case Point.Children(id, _, _) if id != document.rootId => Vector(id)
            case _                                                 => Vector.empty)
        chain.count(kept.contains)
      val edges = range.map { (a, b) => (open(a), open(b)) }.getOrElse {
        val order = document.inDocumentOrder.map(_.id).filter(selected.contains).toVector
        def parents(id: NodeId) = document.ancestorsOf(id).count(_ != document.rootId)
        if order.isEmpty then (0, 0) else (parents(order.head), parents(order.last))
      }
      Document
        .build(document.schema, document.rootId, kept.values.toVector)
        .left
        .map(v => ClipboardError(v.map(_.render).mkString("; ")))
        .flatMap(create(_, edges._1, edges._2))

/** Descriptor-based reconstruction also works for application-defined nodes. */
private[clipboard] object FragmentNodes:
  def rekey(document: DocumentRead, node: EditorNode, id: NodeId): EditorNode =
    def run[N <: EditorNode](kind: NodeType[N]): EditorNode = kind.rekey(kind.project(node).get, id)
    run(document.schema.descriptorFor(node).get)

  def children(document: DocumentRead, node: ElementNode, ids: Vector[NodeId]): ElementNode =
    def run[N <: ElementNode](kind: ElementNodeType[N]): ElementNode =
      kind.withChildren(kind.project(node).get, ids)
    document.schema.descriptorFor(node).get match
      case kind: ElementNodeType[?] => run(kind)
      case _ => throw EditorContractViolation("Missing element descriptor in validated fragment")

  def equivalent(document: DocumentRead, left: ElementNode, right: ElementNode): Boolean =
    children(document, left, Vector.empty) == rekey(
      document,
      children(document, right, Vector.empty),
      left.id
    )
