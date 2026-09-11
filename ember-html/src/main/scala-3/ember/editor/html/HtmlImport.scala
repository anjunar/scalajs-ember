package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** Why an import produced nothing. */
enum HtmlImportError:

  case Parse(error: HtmlParseError)

  /** The nodes were built but do not form a valid document. */
  case Invalid(violations: Vector[Violation])

  def render: String = this match
    case Parse(error)        => error.render
    case Invalid(violations) => violations.map(_.render).mkString("; ")

/** A document read out of HTML, and what the reading cost. */
final case class ImportedHtml(document: Document, diagnostics: Vector[HtmlDiagnostic]):
  def lossless: Boolean = diagnostics.isEmpty

/** Reads HTML into a document (§19.1).
  *
  * ==The shape of the problem==
  *
  * HTML has no rule that says text must sit in a block, and pasted HTML routinely does not: a
  * sentence at the top level, an `<em>` with nothing around it, a `<p>` inside a `<span>`. A
  * document has exactly those rules. So the import is not a translation but a '''repair''', and
  * the repair has to be predictable.
  *
  * Two passes, one for each context:
  *
  *   - '''Blocks.''' Anything inline is collected and, when the next block arrives or the input
  *     ends, wrapped in a paragraph the profile supplies. Whitespace-only text between blocks is
  *     not content and is dropped -- that is the indentation of the page it came from.
  *   - '''Inline.''' Text becomes runs carrying whatever marks are open. A block that turns up
  *     here is unwrapped rather than nested, with a diagnosis: `<span><p>x</p></span>` is a real
  *     shape and its meaning is "x", not "a paragraph inside a word".
  *
  * ==Why it never touches a DOM==
  *
  * §19.1 twice: "Niemals Inhalte erst in den lebenden DOM einsetzen und anschliessend bereinigen",
  * and the same rules must hold on a server. Everything here is a string and a tree of values; the
  * result is a `Document` that has been through `Document.build`, which is the same validation a
  * document from any other source passes.
  */
object HtmlImport:

  def imported(
      html: String,
      schema: Schema,
      support: HtmlImportSupport,
      generator: NodeIdGenerator,
      rootId: NodeId,
      policy: HtmlImportPolicy = HtmlImportPolicy.default
  ): Either[HtmlImportError, ImportedHtml] =
    HtmlFragmentParser.parse(html, policy) match
      case Left(error) => Left(HtmlImportError.Parse(error))
      case Right(parsed) =>
        val run = new Run(schema, support, generator, rootId, policy, parsed.diagnostics)
        run.build(parsed.fragments)

  /** One import, with the bookkeeping that would otherwise be threaded through every call. */
  private final class Run(
      schema: Schema,
      support: HtmlImportSupport,
      generator: NodeIdGenerator,
      rootId: NodeId,
      policy: HtmlImportPolicy,
      parseDiagnostics: Vector[HtmlDiagnostic]
  ):

    private val nodes       = mutable.LinkedHashMap.empty[NodeId, EditorNode]
    private val used        = mutable.Set(rootId)
    private val diagnostics = mutable.ArrayBuffer.from(parseDiagnostics)
    private val open        = mutable.ArrayBuffer.empty[String]
    private val scope       = new HtmlImportScope(schema, policy, generator, used, diagnostics, open)

    def build(fragments: Vector[HtmlFragment]): Either[HtmlImportError, ImportedHtml] =
      val blocks = importBlocks(fragments, Whitespace.Collapse)
      val root   = support.profile.root(blocks, rootId)

      Document.build(schema, rootId, nodes.values.toVector :+ root) match
        case Right(document) => Right(ImportedHtml(document, diagnostics.toVector))
        case Left(violations) => Left(HtmlImportError.Invalid(violations))

    // ---------------------------------------------------------------------------------------
    // Bloecke
    // ---------------------------------------------------------------------------------------

    private def importBlocks(
        fragments: Vector[HtmlFragment],
        whitespace: Whitespace
    ): Vector[NodeId] =
      val blocks  = mutable.ArrayBuffer.empty[NodeId]
      val pending = mutable.ArrayBuffer.empty[NodeId]

      def flush(): Unit =
        if pending.nonEmpty then
          blocks += emit(support.profile.paragraph(merged(pending.toVector), scope))
          pending.clear()

      fragments.foreach {
        case HtmlFragment.Text(value) =>
          // Between blocks, whitespace is the indentation of the page it came from.
          val text = normalise(value, whitespace)
          if text.exists(!_.isWhitespace) || (whitespace == Whitespace.Preserve && text.nonEmpty)
          then pending += emit(support.profile.text(text, MarkSet.empty, scope))

        case element: HtmlFragment.Element =>
          decide(element) match
            // An inline container between blocks -- a link on a line of its own. It is content,
            // not a boundary, so it joins the paragraph being collected.
            case HtmlImportDecision.Container(create, NodeLevel.Inline, mode, inner) =>
              pending += emit(create(importChildren(element, mode, inner)))

            case HtmlImportDecision.Container(create, _, mode, inner) =>
              flush()
              blocks += emit(create(importChildren(element, mode, inner)))

            case HtmlImportDecision.Leaf(node) => pending += emit(node)

            // A mark with nothing around it -- `<em>x</em>` at the top level. Its content is
            // inline, so it joins the paragraph being collected.
            case HtmlImportDecision.Marked(mark) =>
              pending ++= within(element, importInline(element.children, MarkSet.of(mark), whitespace))

            case HtmlImportDecision.Unwrap =>
              val inner = within(element, importBlocks(element.children, whitespace))
              if inner.nonEmpty then
                flush()
                blocks ++= inner

            case HtmlImportDecision.Discard(reason) =>
              diagnostics += HtmlDiagnostic(HtmlLoss.DroppedElement, s"<${element.tag}>: $reason")
      }

      flush()
      blocks.toVector

    /** A container's children, the way its rule said to read them. */
    private def importChildren(
        element: HtmlFragment.Element,
        mode: ChildMode,
        whitespace: Whitespace
    ): Vector[NodeId] =
      within(
        element,
        mode match
          case ChildMode.Blocks => importBlocks(element.children, whitespace)
          case ChildMode.Inline =>
            merged(importInline(element.children, MarkSet.empty, whitespace))
      )

    /** Runs `body` with the element on the ancestor stack, so a rule can ask where it is. */
    private def within[A](element: HtmlFragment.Element, body: => A): A =
      scope.enter(element.tag)
      try body
      finally scope.leave()

    // ---------------------------------------------------------------------------------------
    // Inline
    // ---------------------------------------------------------------------------------------

    private def importInline(
        fragments: Vector[HtmlFragment],
        marks: MarkSet,
        whitespace: Whitespace
    ): Vector[NodeId] =
      fragments.flatMap {
        case HtmlFragment.Text(value) =>
          val text = normalise(value, whitespace)
          if text.isEmpty then Vector.empty
          else Vector(emit(support.profile.text(text, marks, scope)))

        case element: HtmlFragment.Element =>
          decide(element) match
            case HtmlImportDecision.Marked(mark) =>
              within(element, importInline(element.children, marks.union(MarkSet.of(mark)), whitespace))

            case HtmlImportDecision.Leaf(node) => Vector(emit(node))

            case HtmlImportDecision.Unwrap =>
              within(element, importInline(element.children, marks, whitespace))

            case HtmlImportDecision.Container(create, NodeLevel.Inline, mode, inner) =>
              Vector(emit(create(importChildren(element, mode, inner))))

            // A block inside inline content. Real shape, and its meaning is its text.
            case HtmlImportDecision.Container(_, _, _, inner) =>
              diagnostics += HtmlDiagnostic(
                HtmlLoss.UnwrappedElement,
                s"<${element.tag}> stand in Inline-Inhalt"
              )
              within(element, importInline(element.children, marks, inner))

            case HtmlImportDecision.Discard(reason) =>
              diagnostics += HtmlDiagnostic(HtmlLoss.DroppedElement, s"<${element.tag}>: $reason")
              Vector.empty
      }

    // ---------------------------------------------------------------------------------------
    // Werkzeug
    // ---------------------------------------------------------------------------------------

    /** The rule's answer, or the default for anything nobody claimed. */
    private def decide(element: HtmlFragment.Element): HtmlImportDecision =
      support.ruleFor(element) match
        case Some(rule) => rule.decide(element, scope)
        case None       => HtmlImportDecision.Unwrap

    private def emit(node: EditorNode): NodeId =
      nodes.update(node.id, node)
      used += node.id
      node.id

    /** Grows adjacent runs with the same marks back together (§8.2).
      *
      * An imported document is canonical or it is not, and nothing will make it so afterwards: the
      * normalisation transform runs on '''changed''' nodes, and a document that has just been
      * built has none. So the two runs that a line break between two tags produces -- "Absatz" and
      * " " -- are joined here, where the information that they are adjacent still exists.
      */
    private def merged(ids: Vector[NodeId]): Vector[NodeId] =
      val out = mutable.ArrayBuffer.empty[NodeId]

      ids.foreach { id =>
        (out.lastOption.flatMap(nodes.get), nodes.get(id)) match
          case (Some(left: TextNode), Some(right: TextNode)) if left.marks == right.marks =>
            nodes.update(left.id, left.copy(text = left.text + right.text))
            nodes.remove(id): Unit
          case _ => out += id
      }

      out.toVector

    /** HTML's whitespace rule, or none of it.
      *
      * Collapsing is not cosmetic: a fragment copied from a page carries the source's line breaks
      * and indentation, and keeping them would put them into the document as text.
      */
    private def normalise(value: String, whitespace: Whitespace): String =
      whitespace match
        case Whitespace.Preserve => value
        case Whitespace.Collapse =>
          val out   = new StringBuilder(value.length)
          var space = false
          value.foreach { char =>
            if char.isWhitespace || char == ' ' && false then
              if !space then out.append(' ')
              space = true
            else
              out.append(char)
              space = false
          }
          out.result()
