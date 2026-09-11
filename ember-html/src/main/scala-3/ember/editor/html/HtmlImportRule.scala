package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** How an element's text is spaced.
  *
  * HTML collapses runs of whitespace, and clipboard HTML is full of indentation that is not content
  * -- a newline and four spaces between two `<li>` tags mean nothing. `<pre>` is the exception, and
  * it is an exception a rule has to declare, because only the rule knows.
  */
enum Whitespace:
  case Collapse, Preserve

/** Whether a node stands between blocks or inside a line.
  *
  * §8.2 makes the distinction and gives the example: "Links sind Inline-Container, keine
  * Text-Mark." A link has children and a target, so it is not a mark -- but it also does not end a
  * paragraph, so it is not a block. Without this, an imported link ended a paragraph and lost
  * itself: every `<a>` came back as bare text, which a browser test of the import duly showed.
  */
enum NodeLevel:
  case Block, Inline

/** What an element's children are.
  *
  * §19.1 lists "Verarbeitung von Kindern" among the things a rule defines, and this is why it
  * cannot be guessed: `<li>a</li>` holds a paragraph even though no `<p>` is written, and
  * `<p><span><p>x</p></span></p>` holds one paragraph even though two are. Deciding by what is
  * inside would get both wrong, and getting them wrong is how a pasted list ends up with text where
  * a block belongs.
  */
enum ChildMode:

  /** Blocks. Loose inline content is wrapped in a paragraph -- `li`, `blockquote`, `td`. */
  case Blocks

  /** Inline content. A block that turns up is flattened -- `p`, `h1`, `pre`. */
  case Inline

/** What a rule decides about one element.
  *
  * §19.1: "`HtmlImportRule[N]` definiert Tag-/Attributerkennung, Prioritaet, Node-Erzeugung sowie
  * Verarbeitung von Kindern." These five cases are that last part -- the children are handled
  * differently for each, and a rule says which by choosing one.
  */
enum HtmlImportDecision:

  /** A node whose children are this element's children.
    *
    * The children are imported '''first''' and their ids handed over, so that a rule never has to
    * know how importing works -- it receives a list and builds one node.
    */
  case Container(
      create: Vector[NodeId] => EditorNode,
      level: NodeLevel = NodeLevel.Block,
      children: ChildMode = ChildMode.Inline,
      whitespace: Whitespace = Whitespace.Collapse
  )

  /** A node with no children: an image, a break. */
  case Leaf(node: EditorNode)

  /** An inline wrapper that contributes a mark to the text inside it (§8.2). */
  case Marked(mark: TextMark)

  /** Keep the children, drop the element.
    *
    * §19.1: "unbekannte harmlose Wrapper werden mit erhaltenem Text aufgeloest." The default for
    * everything nobody claims, and the reason a paste from an unknown page still arrives as
    * readable prose.
    */
  case Unwrap

  /** Drop the element and everything in it, and say why. */
  case Discard(reason: String)

/** What a rule may ask for while it decides.
  *
  * Deliberately small: ids, the schema, the policy, a way to report a loss, and URL handling. A
  * rule that could reach further would be a rule that could make the import depend on something the
  * caller did not pass in.
  */
final class HtmlImportScope private[html] (
    val schema: Schema,
    val policy: HtmlImportPolicy,
    private val generator: NodeIdGenerator,
    private val used: mutable.Set[NodeId],
    private val sink: mutable.ArrayBuffer[HtmlDiagnostic],
    private val open: mutable.ArrayBuffer[String]
):

  /** The tags a rule is currently inside, outermost first.
    *
    * Needed because meaning in HTML is not always local. A `<code>` is an inline mark, except
    * inside a `<pre>`, where it is the conventional wrapper a highlighter puts there and means
    * nothing of its own. Without the context, either every code block gains a spurious mark or
    * inline code loses its real one.
    */
  def ancestors: Vector[String] = open.toVector

  def insideTag(tag: String): Boolean = open.contains(tag)

  private[html] def enter(tag: String): Unit = open += tag

  private[html] def leave(): Unit = if open.nonEmpty then open.remove(open.length - 1)

  /** A fresh id, distinct from everything handed out so far. */
  def nextId(): NodeId =
    val id = generator.next(used.contains)
    used += id
    id

  def note(kind: HtmlLoss, detail: String): Unit = sink += HtmlDiagnostic(kind, detail)

  /** A URL the format is willing to carry, normalised (§19.1).
    *
    * `None` means the baseline refused it -- `javascript:` and friends -- and the loss has already
    * been reported. A rule still has to ask its own link or media policy afterwards: this is the
    * floor, not the decision.
    */
  def url(raw: String): Option[String] =
    val normalised = policy.normaliseUrl(raw)
    if policy.refusesUrl(normalised) then
      note(HtmlLoss.RefusedUrl, normalised.take(60))
      None
    else Some(normalised)

/** Recognises an element and says what to make of it.
  *
  * ==Why a rule and not a table==
  *
  * Because recognition is not always by tag. `<b>` and `<span style="font-weight:bold">` mean the
  * same thing, `<div>` means half a dozen things depending on what is in it, and a `<p>` inside a
  * `<li>` is not a paragraph. A predicate can say all of that; a `Map[String, NodeType]` cannot.
  *
  * ==Priority==
  *
  * §19.1 names it. Two rules may both recognise an element -- a specific one for `<pre><code>` and
  * a general one for `<pre>` -- and the higher number decides. Within the same number, the order
  * the support was assembled in.
  */
trait HtmlImportRule:

  def name: String

  /** Higher goes first. */
  def priority: Int = 0

  def handles(element: HtmlFragment.Element): Boolean

  def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision

object HtmlImportRule:

  /** A rule that recognises a set of tags and builds a container. */
  def container(
      ruleName: String,
      tags: Set[String],
      mode: ChildMode = ChildMode.Inline,
      level: NodeLevel = NodeLevel.Block,
      rulePriority: Int = 0,
      whitespace: Whitespace = Whitespace.Collapse
  )(build: (HtmlFragment.Element, Vector[NodeId], HtmlImportScope) => EditorNode): HtmlImportRule =
    new HtmlImportRule:
      val name                                            = ruleName
      override val priority                               = rulePriority
      def handles(element: HtmlFragment.Element): Boolean = tags.contains(element.tag)
      def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
        HtmlImportDecision.Container(
          children => build(element, children, scope),
          level,
          mode,
          whitespace
        )

  /** A rule that turns a set of tags into one mark. */
  def mark(ruleName: String, tags: Set[String], value: TextMark): HtmlImportRule =
    new HtmlImportRule:
      val name                                            = ruleName
      def handles(element: HtmlFragment.Element): Boolean = tags.contains(element.tag)
      def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
        HtmlImportDecision.Marked(value)

/** What a profile supplies that HTML cannot say for itself.
  *
  * Pasted HTML has loose text at the top level, inline elements outside any block, and blocks
  * inside inline wrappers. A document has none of those. Something has to make a paragraph out of a
  * bare sentence -- and "paragraph" is a rich-text idea (§6 keeps it out of this module), so it
  * comes in from outside.
  */
trait HtmlImportProfile:

  /** A block for content that arrived without one. */
  def paragraph(children: Vector[NodeId], scope: HtmlImportScope): EditorNode

  /** A run of text carrying the marks that were open around it. */
  def text(value: String, marks: MarkSet, scope: HtmlImportScope): EditorNode

  /** The document root. */
  def root(children: Vector[NodeId], id: NodeId): EditorNode

/** The rules an import runs with, in order. */
final class HtmlImportSupport private (
    val profile: HtmlImportProfile,
    val rules: Vector[HtmlImportRule]
):

  /** The rule with the highest priority that recognises this element. */
  def ruleFor(element: HtmlFragment.Element): Option[HtmlImportRule] =
    rules.filter(_.handles(element)).sortBy(-_.priority).headOption

  def withRules(more: HtmlImportRule*): HtmlImportSupport =
    new HtmlImportSupport(profile, rules ++ more)

  def ++(other: HtmlImportSupport): HtmlImportSupport =
    new HtmlImportSupport(profile, rules ++ other.rules)

object HtmlImportSupport:

  def of(profile: HtmlImportProfile, rules: HtmlImportRule*): HtmlImportSupport =
    new HtmlImportSupport(profile, rules.toVector)
