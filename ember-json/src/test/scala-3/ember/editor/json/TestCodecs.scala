package ember.editor.json

import ember.editor.core.*
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
    * In Version 1 hiess das Feld `caption`, seit Version 2 `label`. Damit laesst sich pruefen, dass
    * die Codec-Version tatsaechlich beim Codec ankommt -- und dass sie nichts mit der Formatversion
    * zu tun hat (§19.2).
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
