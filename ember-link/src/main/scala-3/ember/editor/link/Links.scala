package ember.editor.link

import ember.editor.core.*
import ember.editor.richtext.*

/** Reading a document in link terms, and the rules that keep links legal. */
object Links:

  /** The link a point sits in, if any.
    *
    * Walks upwards, not downwards: a point names a run, and the link -- if there is one -- is
    * one of its ancestors. Stopping at the first block keeps the search finite and honest, since
    * a link is inline and cannot be above one (§8.2).
    */
  def linkAt(document: DocumentRead, point: Point): Option[LinkNode] =
    ancestorsOf(document, point.owner)
      .flatMap(document.node)
      .collectFirst { case link: LinkNode => link }

  private def ancestorsOf(document: DocumentRead, node: NodeId): Vector[NodeId] =
    Iterator
      .iterate(document.parentOf(node))(_.flatMap(document.parentOf))
      .takeWhile(_.isDefined)
      .flatten
      .toVector

  /** Whether a point stands inside a link. */
  def isLinked(document: DocumentRead, point: Point): Boolean = linkAt(document, point).isDefined

  /** Moves a link's children out and removes the link.
    *
    * The children keep their ids -- `move` preserves the node (§11), so marks, text and any
    * point inside them survive. What happens afterwards is P12's business: adjacent runs with
    * equal marks grow back together, so unlinking in the middle of a sentence leaves one run.
    */
  def unwrap(scope: TransformScope, link: LinkNode): Unit =
    (for
      parent <- scope.document.parentOf(link.id)
      at     <- scope.document.indexOfChild(link.id)
    yield (parent, at)).foreach { (parent, at) =>
      link.children.zipWithIndex.foreach { (child, offset) =>
        scope.move(child, parent, at + offset): Unit
      }
      scope.remove(link.id): Unit
    }

/** Link normalisation.
  *
  * The same shape as the list rules, for the same reason (§3.2): the commands could each be
  * careful enough, and would then each be careful separately for as long as anyone remembers.
  * A paste, a move or a later feature would not be.
  */
private[link] object LinkNormalization:

  /** A link inside a link loses its wrapper.
    *
    * §8.2: "Ein Link enthaelt keine anderen Links." Unwrapping the '''inner''' one keeps the
    * stretch the author most likely meant -- the outer link covers more, so its target is the
    * one that survives, and no content is lost either way.
    *
    * The rule hangs on the inner node, which is the one whose parent changed and therefore the
    * one that is dirty. A rule on the outer link would look at children that a move made no
    * claim about (§3.4).
    */
  val noNestedLinks: Transform[LinkNode] = new Transform[LinkNode]:
    val name           = "link.no-nested-links"
    val nodeType       = LinkNode
    override val phase = TransformPhase.Early

    def transform(node: LinkNode, scope: TransformScope): Unit =
      val insideAnother = scope.document
        .parentOf(node.id)
        .flatMap(scope.document.node)
        .exists(_.isInstanceOf[LinkNode])

      if insideAnother then Links.unwrap(scope, node)

  /** A link with nothing in it is not a link.
    *
    * What is left when its last child is deleted or moved away. An empty `<a>` is invisible,
    * unclickable, and survives into every export.
    */
  val emptyLinkGoes: Transform[LinkNode] = new Transform[LinkNode]:
    val name           = "link.empty-link-goes"
    val nodeType       = LinkNode
    override val phase = TransformPhase.Late

    def transform(node: LinkNode, scope: TransformScope): Unit =
      if node.children.isEmpty then scope.remove(node.id): Unit

  /** Two neighbouring links to the same place become one.
    *
    * Linking two adjacent stretches separately leaves two anchors where a reader sees one
    * clickable region -- and where Markdown would write two identical link definitions.
    *
    * Backwards, like the list rule and for the same reason: the newly created link is the dirty
    * one, and a rule looking forward would only ever be asked on the node with nothing after it
    * (§3.4). That mistake has now been made twice; the third time it will be recognised on
    * sight.
    */
  val adjacentLinksJoin: Transform[LinkNode] = new Transform[LinkNode]:
    val name           = "link.adjacent-links-join"
    val nodeType       = LinkNode
    override val phase = TransformPhase.Late

    def transform(node: LinkNode, scope: TransformScope): Unit =
      val document = scope.document

      val predecessor = for
        parent <- document.parentOf(node.id)
        index  <- document.indexOfChild(node.id)
        if index > 0
        before <- document.childrenOf(parent).lift(index - 1)
        link   <- document.node(before).collect { case value: LinkNode if value.target == node.target => value }
      yield link

      predecessor.foreach { earlier =>
        val target = earlier.children.length
        node.children.zipWithIndex.foreach { (child, offset) =>
          scope.move(child, earlier.id, target + offset): Unit
        }
      }

  val all: Vector[Transform[?]] = Vector(noNestedLinks, adjacentLinksJoin, emptyLinkGoes)

/** Links as an extension.
  *
  * ==Why no dialog is needed==
  *
  * P14's acceptance: "Linkdialog nicht noetig, headless nutzbar." Everything here takes a
  * [[LinkTarget]] and returns a document change; where that target comes from -- a dialog, a
  * paste, a Markdown import, a test -- is somebody else's question. `ember-ui` (P27) will put a
  * dialog in front of it, and nothing in this module will have to change.
  *
  * @param policy
  *   which targets this profile accepts. Injected, because "erlaubt" is an application's
  *   decision and not a library's: an intranet editor and a public one disagree about `http`.
  */
final class LinkExtension private (
    generator: NodeIdGenerator,
    val policy: LinkUrlPolicy
) extends Extension:

  val id: ExtensionId = ExtensionId("ember.link")

  override val dependsOn: Vector[ExtensionId] = Vector(ExtensionId("ember.rich-text"))

  override def contribute: ExtensionContributions =
    ExtensionContributions(
      nodeTypes = Vector(LinkNode),
      transforms = LinkNormalization.all,
      commands = Vector(
        CommandRegistration(LinkCommands.SetLink) { (scope, target) =>
          LinkEditing.setLink(scope, generator, target)
        },
        CommandRegistration(LinkCommands.RemoveLink) { (scope, _) =>
          LinkEditing.removeLink(scope)
        }
      )
    )

object LinkExtension:

  def apply(
      generator: NodeIdGenerator,
      policy: LinkUrlPolicy = LinkUrlPolicy.default
  ): LinkExtension = new LinkExtension(generator, policy)
