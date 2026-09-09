package ember.editor.foreign

import ember.editor.core.*

/** Node-Arten eines fingierten Fremdmoduls.
  *
  * Bewusst in `ember.editor.foreign` und nicht in `ember.editor.core`: P02 verlangt den Nachweis,
  * dass fremde Feature-Nodes '''keine Core-Aenderung''' erfordern. Ein Fixture im selben Paket
  * koennte sich unbemerkt auf `private[core]` stuetzen und den Nachweis wertlos machen. Alles hier
  * benutzt ausschliesslich die oeffentliche API.
  */

/** Ein Container mit fachlichen Zusatzfeldern.
  *
  * Die Felder `language` und `visible` existieren nur, um zu pruefen, dass `rekey` und
  * `withChildren` sie erhalten. Der Kern kennt die `copy`-Signatur dieser Klasse nicht und darf sie
  * nicht erraten -- deshalb der Rekonstruktionsvertrag im Deskriptor (§8.1).
  */
final case class CaptionNode(
    id: NodeId,
    children: Vector[NodeId],
    language: String,
    visible: Boolean
) extends ElementNode

object CaptionNode extends ElementNodeType[CaptionNode]:

  val typeId: NodeTypeId = NodeTypeId("foreign.caption/1")

  def project(node: EditorNode): Option[CaptionNode] = node match
    case caption: CaptionNode => Some(caption)
    case _                    => None

  def rekey(node: CaptionNode, id: NodeId): CaptionNode = node.copy(id = id)

  def withChildren(node: CaptionNode, children: Vector[NodeId]): CaptionNode =
    node.copy(children = children)

  /** Fachliche Regel des Fremdmoduls, ueber die offene Escape-Luke gemeldet. */
  override def validate(node: CaptionNode, document: DocumentRead): Vector[Violation] =
    if node.language.isEmpty then
      Vector(Violation.reject(node, "Eine Caption braucht eine Sprachangabe.", document))
    else Vector.empty

  def of(id: NodeId, children: Vector[NodeId] = Vector.empty): CaptionNode =
    CaptionNode(id, children, language = "de", visible = true)

/** Ein atomarer Fremdknoten. Sein Inneres ist kein Textbereich. */
final case class StickerNode(id: NodeId, emoji: String) extends AtomNode

object StickerNode extends NodeType[StickerNode]:

  val typeId: NodeTypeId = NodeTypeId("foreign.sticker/1")

  def project(node: EditorNode): Option[StickerNode] = node match
    case sticker: StickerNode => Some(sticker)
    case _                    => None

  def rekey(node: StickerNode, id: NodeId): StickerNode = node.copy(id = id)

/** Ein fehlerhaft registrierter Container.
  *
  * Traegt Kinder, ist aber nur als [[NodeType]] beschrieben, nicht als [[ElementNodeType]] -- ohne
  * `withChildren` kann der Kern ihn nicht generisch umbauen. Existiert ausschliesslich, um
  * [[Violation.MissingElementDescriptor]] ausloesen zu koennen.
  */
final case class UndescribedContainer(id: NodeId, children: Vector[NodeId]) extends ElementNode

object UndescribedContainer extends NodeType[UndescribedContainer]:

  val typeId: NodeTypeId = NodeTypeId("foreign.undescribed/1")

  def project(node: EditorNode): Option[UndescribedContainer] = node match
    case container: UndescribedContainer => Some(container)
    case _                               => None

  def rekey(node: UndescribedContainer, id: NodeId): UndescribedContainer = node.copy(id = id)

/** Eine Art, die absichtlich in keinem Testschema registriert wird. */
final case class UnregisteredNode(id: NodeId) extends EditorNode

/** Markierungen des Fremdmoduls. Der Kern bringt keine mit (§8.2). */
final case class NamedMark(name: String) extends TextMark:
  def markId: MarkId = MarkId(name)
