package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** The typed bridge between Markdown syntax and document nodes (§18.1).
  *
  * ==Why rules and not a match statement==
  *
  * §18.1: "Typisierte Regeln verbinden Syntax und registrierte NodeTypes; Standardregeln liegen
  * im Integrationsmodul." A `match` over node types would have to name them, and this module is
  * not allowed to know any -- §6 gives it the core alone. So the mapping arrives from outside,
  * one rule per kind, and an application with its own block types adds a rule instead of
  * patching a match.
  *
  * ==Three kinds of rule, because the syntax has three shapes==
  *
  *   - [[MarkdownBlockRule]] -- a block becomes a node. Paragraph, heading, quote, list, code.
  *   - [[MarkdownInlineRule]] -- an inline becomes one or more nodes. Text, image, link.
  *   - [[MarkdownMarkRule]] -- an inline becomes a '''mark''', not a node. Emphasis, strong,
  *     inline code.
  *
  * The third is the one that is easy to miss and impossible to bolt on afterwards. `*a*` is not
  * a node containing a run; it is a run carrying a mark (§8.2). The codec therefore carries a
  * [[MarkSet]] down the inline tree instead of building a wrapper, and a rule that wanted a
  * wrapper would have produced a document the rich-text profile refuses.
  */
sealed trait MarkdownRule:

  /** Shown in diagnostics. Must be unique inside one [[MarkdownSupport]]. */
  def id: String

/** A rule for one kind of block. */
trait MarkdownBlockRule extends MarkdownRule:

  /** Syntax to node. `children` are the ids this block's children already produced.
    *
    * `None` means "not mine" -- the codec tries the next rule, exactly as §12's command chain
    * does. A rule that returns `None` must not have written to the sink.
    */
  def decode(block: MarkdownBlock, children: Vector[NodeId], sink: NodeSink): Option[NodeId]

  /** Whether this rule is responsible for a node.
    *
    * Separate from [[encode]] and deliberately cheap: the codec has to know whether a node's
    * children are blocks or inline content '''before''' it descends, and asking by calling
    * `encode` would mean calling it speculatively -- with a sink that then collects diagnostics
    * for a conversion that never happened.
    */
  def handles(node: EditorNode): Boolean

  /** Node to syntax.
    *
    * `children` carries both shapes because a block has one or the other: a quote holds blocks,
    * a paragraph holds inline content, and which one a node has is [[handles]]'s answer one
    * level down.
    */
  def encode(
      node: EditorNode,
      children: MarkdownChildren,
      sink: SyntaxSink
  ): Option[MarkdownBlock]

/** A rule for one kind of inline that becomes nodes. */
trait MarkdownInlineRule extends MarkdownRule:

  /** Inline to nodes, under the marks accumulated on the way down.
    *
    * Returns a '''vector''' because one inline can be several nodes -- and because a rule may
    * legitimately produce none, which is how a construct gets dropped with a diagnostic rather
    * than with an exception.
    */
  def decode(
      inline: MarkdownInline,
      marks: MarkSet,
      children: Vector[NodeId],
      sink: NodeSink
  ): Option[Vector[NodeId]]

  /** Whether this rule is responsible for a node. See [[MarkdownBlockRule.handles]]. */
  def handles(node: EditorNode): Boolean

  /** Node to inlines. */
  def encode(
      node: EditorNode,
      children: Vector[MarkdownInline],
      sink: SyntaxSink
  ): Option[Vector[MarkdownInline]]

/** A rule for an inline that is a mark on the text inside it, not a node around it. */
trait MarkdownMarkRule extends MarkdownRule:

  /** The mark this inline adds, or `None` if this rule does not know it. */
  def markFor(inline: MarkdownInline): Option[TextMark]

  /** Whether this rule is responsible for a mark.
    *
    * The reverse of [[markFor]], and it has to be asked separately: encoding starts from a
    * [[MarkSet]] and has no inline to offer. An earlier version handed the rule a synthetic
    * [[MarkdownInline.Text]] to identify the mark from -- which no rule ever recognised, so
    * every mark silently vanished on export. A round trip found it; nothing else would have.
    */
  def owns(mark: TextMark): Boolean

  /** Wraps already-written inlines in the syntax for this mark.
    *
    * `None` means the mark has no Markdown spelling -- underline, for instance. §18.2 lists it
    * explicitly as '''not''' a CommonMark guarantee, and the encoder turns a `None` here into a
    * loss diagnostic rather than into silently dropped formatting.
    */
  def inlineFor(mark: TextMark, children: Vector[MarkdownInline], sink: SyntaxSink): Option[MarkdownInline]

  /** The order marks nest in when several apply to one run.
    *
    * Deterministic output needs a total order: `**a**` and `*a*` around the same run can be
    * written either way round, and a writer that picked by hash would produce a different
    * document on every run. Lower sorts outermost.
    */
  def nesting: Int

/** The rules an application registers, in priority order.
  *
  * Like [[ember.editor.core.CommandRegistry]] and `JsonSupport`: a value that is combined with
  * `++`, not a global registry. §6 forbids "eager Sammelregistrierungen" -- an application that
  * wants paragraphs and nothing else registers paragraphs and nothing else.
  */
final case class MarkdownSupport(
    blocks: Vector[MarkdownBlockRule] = Vector.empty,
    inlines: Vector[MarkdownInlineRule] = Vector.empty,
    marks: Vector[MarkdownMarkRule] = Vector.empty
):

  def ++(other: MarkdownSupport): MarkdownSupport =
    MarkdownSupport(blocks ++ other.blocks, inlines ++ other.inlines, marks ++ other.marks)

  /** The rule ids that appear more than once. A support with duplicates is a configuration
    * mistake, and the codec refuses it rather than letting the earlier one silently win.
    */
  def duplicates: Vector[String] =
    val all = blocks.map(_.id) ++ inlines.map(_.id) ++ marks.map(_.id)
    all.groupBy(identity).collect { case (id, seen) if seen.length > 1 => id }.toVector.sorted

object MarkdownSupport:

  val empty: MarkdownSupport = MarkdownSupport()

  def of(rules: MarkdownRule*): MarkdownSupport =
    rules.foldLeft(empty) { (support, rule) =>
      rule match
        case block: MarkdownBlockRule   => support.copy(blocks = support.blocks :+ block)
        case inline: MarkdownInlineRule => support.copy(inlines = support.inlines :+ inline)
        case mark: MarkdownMarkRule     => support.copy(marks = support.marks :+ mark)
    }

/** What a block rule gets as the content of its node.
  *
  * One of the two is always empty. Which one is not a question a rule has to ask -- a quote
  * rule reads `blocks`, a paragraph rule reads `inlines`, and neither can be surprised.
  */
final case class MarkdownChildren(
    blocks: Vector[MarkdownBlock] = Vector.empty,
    inlines: Vector[MarkdownInline] = Vector.empty
)

/** Where a decoding rule puts the nodes it builds.
  *
  * Hands out ids and collects nodes. A rule never sees the document under construction, because
  * there is none: [[MarkdownCodec]] validates the whole node set at the end through
  * `Document.build`, which is the import path §19.2 already uses for JSON.
  */
final class NodeSink private[markdown] (generator: NodeIdGenerator):

  private val nodes  = mutable.ArrayBuffer.empty[EditorNode]
  private val taken  = mutable.HashSet.empty[NodeId]
  private val spans  = mutable.Map.empty[NodeId, SourceSpan]
  private val order  = mutable.ArrayBuffer.empty[NodeId]
  private val notes  = mutable.ArrayBuffer.empty[MarkdownDiagnostic]

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

  private[markdown] def collected: Vector[EditorNode]       = nodes.toVector
  /** Spans in the order they were recorded -- children before parents. */
  private[markdown] def sourceSpans: Vector[(NodeId, SourceSpan)] = order.toVector.flatMap(id => spans.get(id).map(id -> _))
  private[markdown] def diagnostics: Vector[MarkdownDiagnostic] = notes.toVector

/** Where an encoding rule puts what it cannot express.
  *
  * It hands out no ids: the syntax tree a rule builds is thrown away after it is written, so its
  * [[SyntaxId]]s and [[SourceSpan]]s mean nothing. `fresh` and `nowhere` exist so a rule can
  * fill the fields without pretending they carry information.
  */
final class SyntaxSink private[markdown] ():

  private var counter = 0
  private val notes   = mutable.ArrayBuffer.empty[MarkdownDiagnostic]

  def fresh(): SyntaxId =
    counter += 1
    SyntaxId(counter)

  /** The span for a syntax node that is on its way out. Empty, and deliberately so. */
  val nowhere: SourceSpan = SourceSpan(0, 0)

  def note(message: String, loss: Boolean = false): Unit =
    notes += MarkdownDiagnostic(message, None, loss)

  /** Records that something the document holds has no Markdown spelling.
    *
    * The distinction between this and [[note]] is the whole of `Strict` versus `AllowLossy`
    * (§18.2): a loss means the export is not reversible, and an application has to say in
    * advance that it accepts that.
    */
  def lost(what: String): Unit = note(what, loss = true)

  private[markdown] def diagnostics: Vector[MarkdownDiagnostic] = notes.toVector

/** Something worth knowing about a conversion that nonetheless produced a result.
  *
  * @param loss
  *   whether information was dropped. `Strict` refuses an export with any of these; a plain
  *   diagnostic never blocks anything.
  */
final case class MarkdownDiagnostic(
    message: String,
    span: Option[SourceSpan] = None,
    loss: Boolean = false
):
  def render: String =
    val where = span.map(value => s"${value.render}: ").getOrElse("")
    s"$where$message${if loss then " (Verlust)" else ""}"

/** Whether an export may drop what Markdown cannot express (§18.2). */
enum LossPolicy:

  /** Refuses an export that would lose information. The default -- a silent loss is the one
    * failure mode a user cannot see.
    */
  case Strict

  /** Exports anyway, and reports what went. Has to be chosen deliberately. */
  case AllowLossy

/** Where in a document a piece of source ended up, and the other way round.
  *
  * This is the second half of §18.2's source maps: "SourceMaps erfassen UTF-16-Quellbereiche
  * '''und Dokumentpositionen'''." [[SourceMap]] answers the first half about syntax; this one
  * answers the second about nodes, and it is what a source view needs to highlight the node
  * under the caret.
  */
final case class DocumentSourceMap(entries: Vector[(NodeId, SourceSpan)]):

  private lazy val byId: Map[NodeId, SourceSpan] = entries.toMap

  def spanOf(node: NodeId): Option[SourceSpan] = byId.get(node)

  def size: Int = byId.size

  /** The innermost node containing an offset.
    *
    * "Innermost" without a tree to walk: a child's span lies inside its parent's, so the one
    * that starts latest and is shortest is the deepest.
    *
    * Two spans can be identical -- a paragraph holding a single text run covers exactly
    * the same characters -- and then neither rule decides. The tie goes to whichever was
    * recorded first, and that is not arbitrary: the codec decodes children before their parent,
    * so the earlier entry is the deeper node. Hence a [[Vector]] here and not a [[Map]]; a map
    * would answer the same question differently depending on hashing.
    */
  def nodeAt(offset: Int): Option[NodeId] =
    entries
      .filter((_, span) => span.contains(offset))
      .reduceOption { (left, right) =>
        val (_, leftSpan)  = left
        val (_, rightSpan) = right
        if rightSpan.start > leftSpan.start then right
        else if rightSpan.start == leftSpan.start && rightSpan.length < leftSpan.length then right
        else left
      }
      .map(_._1)
