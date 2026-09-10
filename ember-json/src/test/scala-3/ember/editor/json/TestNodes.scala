package ember.editor.json

import ember.editor.core.*

/** Lokale typisierte Testknoten.
  *
  * Bewusst hier und nicht aus `ember-rich-text`: §6 stellt `json` neben die Node-Module, nicht
  * ueber sie. Ein Test, der `ParagraphNode` benutzte, wuerde eine Abhaengigkeit belegen, die es
  * nicht gibt -- und die Codecs der Feature-Module entstehen ohnehin erst in P16/P18.
  */
final case class BlockNode(id: NodeId, children: Vector[NodeId], label: String) extends ElementNode

object BlockNode extends ElementNodeType[BlockNode]:

  val typeId: NodeTypeId = NodeTypeId("test.block/1")

  def project(node: EditorNode): Option[BlockNode] = node match
    case block: BlockNode => Some(block)
    case _                => None

  def rekey(node: BlockNode, id: NodeId): BlockNode = node.copy(id = id)

  def withChildren(node: BlockNode, children: Vector[NodeId]): BlockNode =
    node.copy(children = children)

  def apply(id: NodeId, children: Vector[NodeId]): BlockNode = BlockNode(id, children, "")

/** Ein Element mit Kindern, dessen Deskriptor '''kein''' [[ElementNodeType]] ist.
  *
  * Der Fall, den §8.1 als Schemafehler fuehrt. Hier gebraucht, um zu zeigen, dass das Dekodieren
  * ihn meldet, statt die Kindliste stillschweigend fallen zu lassen.
  */
final case class BrokenNode(id: NodeId, children: Vector[NodeId]) extends ElementNode

object BrokenNode extends NodeType[BrokenNode]:

  val typeId: NodeTypeId = NodeTypeId("test.broken/1")

  def project(node: EditorNode): Option[BrokenNode] = node match
    case broken: BrokenNode => Some(broken)
    case _                  => None

  def rekey(node: BrokenNode, id: NodeId): BrokenNode = node.copy(id = id)

/** Eine Markierung mit Nutzdaten. */
final case class Highlight(color: String) extends TextMark:
  val markId: MarkId = Highlight.markId

object Highlight:
  val markId: MarkId = MarkId("test.highlight/1")

/** Eine Markierung ohne Nutzdaten. */
case object Marker extends TextMark:
  val markId: MarkId = MarkId("test.marker/1")

object TestCodecs:

  val block: NodeJsonCodec[BlockNode] = new NodeJsonCodec[BlockNode]:
    val nodeType: NodeType[BlockNode] = BlockNode

    def encode(
        node: BlockNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] =
      Right(Vector("label" -> JsonValue.Str(node.label)))

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, BlockNode] =
      payload.string("label", context.path).map(BlockNode(id, Vector.empty, _))

  val broken: NodeJsonCodec[BrokenNode] = new NodeJsonCodec[BrokenNode]:
    val nodeType: NodeType[BrokenNode] = BrokenNode

    def encode(
        node: BrokenNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] = Right(Vector.empty)

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, BrokenNode] = Right(BrokenNode(id, Vector.empty))

  /** Ein Codec in Version 2, der Version 1 weiterhin liest.
    *
    * In Version 1 hiess das Feld `caption`, seit Version 2 `label`. Damit laesst sich pruefen,
    * dass die Codec-Version tatsaechlich beim Codec ankommt -- und dass sie nichts mit der
    * Formatversion zu tun hat (§19.2).
    */
  val blockV2: NodeJsonCodec[BlockNode] = new NodeJsonCodec[BlockNode]:
    val nodeType: NodeType[BlockNode] = BlockNode
    override val codecVersion: Int    = 2

    def encode(
        node: BlockNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] =
      Right(Vector("label" -> JsonValue.Str(node.label)))

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, BlockNode] =
      val field = if context.codecVersion <= 1 then "caption" else "label"
      payload.string(field, context.path).map(BlockNode(id, Vector.empty, _))

  val highlight: MarkJsonCodec[Highlight] = new MarkJsonCodec[Highlight]:
    val markId: MarkId = Highlight.markId

    def project(mark: TextMark): Option[Highlight] = mark match
      case highlight: Highlight => Some(highlight)
      case _                    => None

    def encode(mark: Highlight): Vector[(String, JsonValue)] =
      Vector("color" -> JsonValue.Str(mark.color))

    def decode(payload: JsonValue.Obj, at: DiagnosticPath): Either[DecodeError, Highlight] =
      payload.string("color", at).map(Highlight(_))

  val marker: MarkJsonCodec[Marker.type] = new MarkJsonCodec[Marker.type]:
    val markId: MarkId = Marker.markId

    def project(mark: TextMark): Option[Marker.type] = mark match
      case Marker => Some(Marker)
      case _      => None

    def encode(mark: Marker.type): Vector[(String, JsonValue)] = Vector.empty

    def decode(payload: JsonValue.Obj, at: DiagnosticPath): Either[DecodeError, Marker.type] =
      Right(Marker)
