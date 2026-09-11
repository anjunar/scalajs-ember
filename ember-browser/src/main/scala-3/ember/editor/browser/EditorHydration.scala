package ember.editor.browser

import ember.editor.core.*
import ember.editor.html.*
import org.scalajs.dom

import scala.collection.mutable

/** Hydration as a state transfer with loss protection, not as tag matching (§17).
  *
  * ==The check UI cannot make==
  *
  * §17 step 5: "Ein zusaetzlicher Editor-Check validiert IDs, Textinhalt und semantisch
  * relevante Attribute gegen das erwartete Profil, '''bevor''' Binding sie verdeckt.
  * UI-Strict allein beweist dies heute nicht."
  *
  * UI's hydrating cursor checks that it finds the tag it expects where it expects it. That is
  * necessary and not sufficient: a `<p>` with the wrong `data-ember-node`, or with the right id
  * and different text, passes a structural check and then quietly becomes a document that says
  * something other than what the server sent. [[preflight]] is the check that catches it, and it
  * runs before the first claim so that a mismatch costs nothing.
  *
  * ==Why it compares against the semantics and not against a second renderer==
  *
  * The expectation comes from the same [[HtmlSupport]] that produced the SSR output. A separate
  * description of "what the server should have sent" would be a second source of truth, and the
  * two would drift -- and the drift would look exactly like a hydration mismatch.
  */
object EditorHydration:

  /** Checks a server-rendered subtree against the document it should represent.
    *
    * Throws on the first mismatch: the boundary's `preflight` is a `(HostElement, A) => Unit`,
    * and ui-core turns a throw into a scoped rebuild with the fallback intact. Returning an
    * `Either` here would mean the caller had to decide what a failure means, and §17 already
    * decided.
    *
    * @param profile
    *   the render profile the server used. A page rendered as [[RenderProfile.Content]] carries
    *   no node ids, so hydrating it as an editor is a mismatch of the '''profile''', not of the
    *   markup -- and saying so is more useful than reporting a missing attribute.
    */
  def preflight(
      root: dom.Element,
      document: Document,
      support: HtmlSupport,
      profile: RenderProfile = RenderProfile.Editor
  ): Unit =
    val problems = check(root, document, support, profile)
    if problems.nonEmpty then throw HydrationMismatch(problems)

  /** The same check for a container that '''holds''' the document root.
    *
    * A hydration boundary hands over its own host, and that host is the box around the view, not
    * the view: §17.3 puts the fallback outside the boundary, so the boundary is a wrapper by
    * construction. The document's root node is the first element inside it.
    *
    * Comparing the container against the root instead would report `<article>` against `<div>`
    * on every correct page -- a check that fails for structural reasons has no way to report a
    * real mismatch.
    */
  def preflightContent(
      container: dom.Element,
      document: Document,
      support: HtmlSupport,
      profile: RenderProfile = RenderProfile.Editor
  ): Unit =
    elementChildren(container).headOption match
      // Ein leerer Container ist kein fehlendes Attribut, sondern eine fehlende Ansicht --
      // und als solche zu melden erspart dem Leser die Suche nach dem Knoten, der fehlt.
      case None => throw HydrationMismatch(Vector(HydrationProblem.ChildCount(document.rootId, 1, 0)))
      case Some(root) => preflight(root, document, support, profile)

  /** The same check, as a value. For a test, and for a caller that wants to log before failing. */
  def check(
      root: dom.Element,
      document: Document,
      support: HtmlSupport,
      profile: RenderProfile = RenderProfile.Editor
  ): Vector[HydrationProblem] =
    val found = mutable.ArrayBuffer.empty[HydrationProblem]

    def fail(problem: HydrationProblem): Unit =
      if found.length < MaxProblems then found += problem

    def walk(node: EditorNode, element: dom.Element): Unit =
      if found.length >= MaxProblems then ()
      else
        support.shapeOf(node, profile) match
          case None        => fail(HydrationProblem.NoSemantics(node.id))
          case Some(shape) => compare(node, shape, element)

    def compare(node: EditorNode, shape: HtmlShape, element: dom.Element): Unit =
      val expectedTag = shape match
        case HtmlShape.Element(tag, _, _)    => tag
        case HtmlShape.TextRun(tag, _, _, _) => tag

      val actualTag = element.tagName.toLowerCase

      if actualTag != expectedTag.toLowerCase then
        fail(HydrationProblem.TagMismatch(node.id, expectedTag, actualTag))
      else
        val attributes = shape match
          case HtmlShape.Element(_, given_, _)    => given_
          case HtmlShape.TextRun(_, _, given_, _) => given_

        // Nur die Attribute pruefen, die die Semantik '''nennt'''. Ein Server darf eigene
        // hinzufuegen -- eine Klasse fuers Layout, ein Analytics-Attribut --, und ein Editor,
        // der daran scheiterte, waere in jeder realen Seite unbrauchbar.
        attributes.foreach { attribute =>
          val actual = Option(element.getAttribute(attribute.name))
          if !actual.contains(attribute.value) then
            fail(
              HydrationProblem.AttributeMismatch(
                node.id,
                attribute.name,
                attribute.value,
                actual
              )
            )
        }

        shape match
          case HtmlShape.TextRun(_, expected, _, _) =>
            // §17.5 nennt den Textinhalt ausdruecklich. Ein Lauf mit richtiger ID und anderem
            // Text ist der Fall, den eine reine Strukturpruefung durchlaesst.
            val actual = element.textContent
            if actual != expected then
              fail(HydrationProblem.TextMismatch(node.id, expected, actual))

          case HtmlShape.Element(_, _, _) =>
            val children  = childrenOf(node, document)
            val elements  = elementChildren(element)

            if children.length != elements.length then
              fail(HydrationProblem.ChildCount(node.id, children.length, elements.length))
            else children.zip(elements).foreach((child, host) => walk(child, host))

    document.node(document.rootId) match
      case None        => found += HydrationProblem.NoSemantics(document.rootId)
      case Some(value) => walk(value, root)

    found.toVector

  /** How many problems to collect before giving up.
    *
    * A mismatch near the top usually makes every node below it mismatch too, and a diagnosis of
    * ten thousand lines is not a diagnosis. The first few say where the divergence started,
    * which is the question a reader actually has.
    */
  private val MaxProblems = 8

  private def childrenOf(node: EditorNode, document: Document): Vector[EditorNode] =
    node match
      case element: ElementNode => element.children.flatMap(document.node)
      case _                    => Vector.empty

  /** Element children only.
    *
    * The UI runtime writes comment anchors for keyed groups (`<!--ui:KeyedChildren:start-->`),
    * and they are not document nodes. Counting them would make every keyed container mismatch.
    */
  private def elementChildren(element: dom.Element): Vector[dom.Element] =
    val out = Vector.newBuilder[dom.Element]
    var index = 0
    while index < element.childNodes.length do
      // `nodeType` and not a type test: a document in an iframe has its own `Element`, and
      // `instanceof` against this realm's would miss every node in it. See [[DomKinds]].
      DomKinds.asElement(element.childNodes(index)).foreach(child => out += child)
      index += 1
    out.result()


/** Why a server-rendered subtree does not match the document it claims to be. */
enum HydrationProblem:

  case TagMismatch(node: NodeId, expected: String, actual: String)
  case AttributeMismatch(node: NodeId, name: String, expected: String, actual: Option[String])
  case TextMismatch(node: NodeId, expected: String, actual: String)
  case ChildCount(node: NodeId, expected: Int, actual: Int)
  case NoSemantics(node: NodeId)

  def render: String = this match
    case TagMismatch(node, expected, actual) =>
      s"`${node.value}`: erwartet `<$expected>`, gefunden `<$actual>`"
    case AttributeMismatch(node, name, expected, actual) =>
      s"`${node.value}`: `$name` sollte `$expected` sein, ist ${actual.map(value => s"`$value`").getOrElse("nicht gesetzt")}"
    case TextMismatch(node, expected, actual) =>
      s"`${node.value}`: Text sollte `$expected` sein, ist `$actual`"
    case ChildCount(node, expected, actual) =>
      s"`${node.value}`: $expected Kinder erwartet, $actual gefunden"
    case NoSemantics(node) =>
      s"`${node.value}`: keine HtmlSemantics registriert"

/** The failure a preflight throws.
  *
  * An exception and not an `Either`, because that is what `HydrationBoundary` takes: it turns a
  * throw into a scoped rebuild of exactly this boundary, with the fallback outside it intact
  * (§17). Returning a value would mean re-inventing that mechanism one level up.
  */
final class HydrationMismatch(val problems: Vector[HydrationProblem])
    extends RuntimeException(
      problems
        .map(_.render)
        .mkString("Die ausgelieferte Ansicht passt nicht zum Dokument: ", "; ", "")
    )
