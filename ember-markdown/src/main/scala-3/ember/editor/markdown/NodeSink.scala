package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** Where a decoding rule puts the nodes it builds.
  *
  * Hands out ids and collects nodes. A rule never sees the document under construction, because
  * there is none: [[MarkdownCodec]] validates the whole node set at the end through
  * `Document.build`, which is the import path §19.2 already uses for JSON.
  */
final class NodeSink private[markdown] (generator: NodeIdGenerator):

  private val nodes = mutable.ArrayBuffer.empty[EditorNode]
  private val taken = mutable.HashSet.empty[NodeId]
  private val spans = mutable.Map.empty[NodeId, SourceSpan]
  private val order = mutable.ArrayBuffer.empty[NodeId]
  private val notes = mutable.ArrayBuffer.empty[MarkdownDiagnostic]

  /** Builds a node under a fresh id and records where it came from. */
  def add(span: SourceSpan)(make: NodeId => EditorNode): NodeId =
    val id = generator.next(taken.contains)
    taken.add(id): Unit
    nodes += make(id)
    spans += (id -> span)
    order += id
    id

  /** Drops nodes a rule decided not to use, and everything below them.
    *
    * The codec decodes a rule's children before the rule runs, so a rule that turns them into
    * something else -- an image folding its alt text into a string -- leaves them behind. They
    * would sit in the node set unreachable from the root, and `Document.build` would refuse the
    * whole import with "nicht erreichbar".
    *
    * Saying so explicitly rather than pruning unreachable nodes at the end: a node that nobody
    * attached is usually a bug, and silently sweeping it away would hide the next one.
    */
  def discard(ids: Vector[NodeId]): Unit =
    val doomed = mutable.Queue.from(ids)
    while doomed.nonEmpty do
      val id = doomed.dequeue()
      nodes.indexWhere(_.id == id) match
        case -1 => ()
        case at =>
          nodes(at) match
            case element: ElementNode => doomed.enqueueAll(element.children)
            case _                    => ()
          nodes.remove(at): Unit
          spans.remove(id): Unit

  /** Something worth saying about a decode that nonetheless produced nodes. */
  def note(message: String, span: SourceSpan, loss: Boolean = false): Unit =
    notes += MarkdownDiagnostic(message, Some(span), loss)

  private[markdown] def collected: Vector[EditorNode] = nodes.toVector

  /** Spans in the order they were recorded -- children before parents. */
  private[markdown] def sourceSpans: Vector[(NodeId, SourceSpan)] =
    order.toVector.flatMap(id => spans.get(id).map(id -> _))
  private[markdown] def diagnostics: Vector[MarkdownDiagnostic] = notes.toVector
