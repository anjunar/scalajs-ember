package ember.editor.json

import ember.editor.core.*

/** Registry der Markierungscodecs. */
final class MarkSupport private (val codecs: Vector[MarkJsonCodec[?]]):

  private val byId: Map[MarkId, MarkJsonCodec[?]] =
    codecs.map(codec => codec.markId -> codec).toMap

  def ++(other: MarkSupport): MarkSupport = new MarkSupport(codecs ++ other.codecs)

  private[json] def encode(
      mark: TextMark,
      nodeId: NodeId,
      at: DiagnosticPath
  ): Either[EncodeError, JsonValue] =
    codecs.find(_.project(mark).isDefined) match
      case Some(codec) => Right(write(codec, mark))
      case None        => Left(EncodeError.NoMarkCodec(nodeId, mark.markId, at))

  private def write[M <: TextMark](codec: MarkJsonCodec[M], mark: TextMark): JsonValue =
    val fields = codec.project(mark).map(codec.encode).getOrElse(Vector.empty)
    JsonValue.Obj(("mark" -> JsonValue.Str(codec.markId.value)) +: fields)

  private[json] def decode(
      value: JsonValue,
      at: DiagnosticPath
  ): Either[DecodeError, TextMark] =
    for
      payload <- value.asObject(at)
      name    <- payload.string("mark", at)
      markId  <- MarkId
        .parse(name)
        .toRight(DecodeError.InvalidValue(s"Keine gueltige MarkId: `$name`", at))
      codec <- byId.get(markId).toRight(DecodeError.UnknownMark(name, at))
      mark  <- codec.decode(payload, at)
    yield mark

object MarkSupport:

  val empty: MarkSupport = new MarkSupport(Vector.empty)

  def of(codecs: MarkJsonCodec[?]*): MarkSupport = new MarkSupport(codecs.toVector)
