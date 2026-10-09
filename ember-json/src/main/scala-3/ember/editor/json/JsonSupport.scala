package ember.editor.json

import ember.editor.core.*

/** Registry der Knotencodecs.
  *
  * Heterogen und ohne oeffentliches `Any`, wie [[Schema]] im Kern und `HtmlSupport` in
  * `ember-html`: der Typzeuge ist der Deskriptor des jeweiligen Eintrags.
  */
final class JsonSupport private (
    val codecs: Vector[NodeJsonCodec[?]],
    val marks: MarkSupport
):

  def ++(other: JsonSupport): JsonSupport =
    new JsonSupport(codecs ++ other.codecs, marks ++ other.marks)

  /** Ergaenzt Markierungscodecs, ohne die Knotencodecs anzufassen. */
  def withMarks(additional: MarkSupport): JsonSupport =
    new JsonSupport(codecs, marks ++ additional)

  def codecFor(node: EditorNode): Option[NodeJsonCodec[?]] =
    codecs.find(_.nodeType.project(node).isDefined)

  def codecFor(typeId: NodeTypeId): Option[NodeJsonCodec[?]] =
    codecs.find(_.nodeType.typeId == typeId)

  private[json] def encode(
      node: EditorNode,
      at: DiagnosticPath
  ): Either[EncodeError, (NodeTypeId, Int, Vector[(String, JsonValue)])] =
    codecFor(node) match
      case Some(codec) => write(codec, node, at)
      case None        =>
        Left(EncodeError.NoCodec(node.id, node.getClass.getSimpleName, at))

  private def write[N <: EditorNode](
      codec: NodeJsonCodec[N],
      node: EditorNode,
      at: DiagnosticPath
  ): Either[EncodeError, (NodeTypeId, Int, Vector[(String, JsonValue)])] =
    codec.nodeType.project(node) match
      case Some(typed) =>
        codec
          .encode(typed, EncodeContext(node.id, at, marks))
          .map((codec.nodeType.typeId, codec.codecVersion, _))
      case None =>
        // Kann nur eintreten, wenn `codecFor` und `project` sich uneins sind -- also nie.
        Left(EncodeError.NoCodec(node.id, node.getClass.getSimpleName, at))

  private[json] def decode(
      codec: NodeJsonCodec[?],
      id: NodeId,
      payload: JsonValue.Obj,
      codecVersion: Int,
      limits: DecodeLimits,
      at: DiagnosticPath
  ): Either[DecodeError, EditorNode] =
    codec.decode(id, payload, DecodeContext(codecVersion, at, limits, marks))

object JsonSupport:

  val empty: JsonSupport = new JsonSupport(Vector.empty, MarkSupport.empty)

  def of(codecs: NodeJsonCodec[?]*): JsonSupport =
    new JsonSupport(codecs.toVector, MarkSupport.empty)
