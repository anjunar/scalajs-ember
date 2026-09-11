package ember.editor.standard

import ember.editor.core.*
import ember.editor.image.*
import ember.editor.json.*

/** The JSON codec for images.
  *
  * ==Why it lives here and not in `ember-image`==
  *
  * §6 puts `image` on the core alone -- it knows nothing about a wire format, exactly as it knows
  * nothing about HTML. And `ember-json` knows nothing about images. `standard` is the one place
  * where both are on the classpath, and §6 names it for that: "der einzige Ort, an dem
  * Feature-Nodes und Renderer einander kennen".
  *
  * ==Why decoding takes a policy==
  *
  * Because a source that arrives in a JSON payload is exactly as untrusted as one typed into a
  * dialog, and §20 does not distinguish. Passing the policy in means the two paths run the same
  * check -- and there is no other way to build a [[MediaUrl]], so they cannot drift.
  *
  * A document whose image sources the profile refuses does not decode. That is the point: an `img`
  * with a `javascript:` source is not a slightly wrong document, it is one that must never reach a
  * renderer.
  */
object ImageJsonSupport:

  def codec(policy: MediaUrlPolicy = MediaUrlPolicy.default): NodeJsonCodec[ImageNode] =
    new NodeJsonCodec[ImageNode]:
      val nodeType: NodeType[ImageNode] = ImageNode

      def encode(
          node: ImageNode,
          context: EncodeContext
      ): Either[EncodeError, Vector[(String, JsonValue)]] =
        Right(
          Vector("src" -> JsonValue.Str(node.src.value), "alt" -> JsonValue.Str(node.alt)) ++
            node.source.mediaId.map(id => "mediaId" -> JsonValue.Str(id.value)).toVector ++
            node.title.map(title => "title" -> JsonValue.Str(title)).toVector ++
            node.width.map(pixels => "width" -> JsonValue.num(pixels.value)).toVector ++
            node.height.map(pixels => "height" -> JsonValue.num(pixels.value)).toVector
        )

      def decode(
          id: NodeId,
          payload: JsonValue.Obj,
          context: DecodeContext
      ): Either[DecodeError, ImageNode] =
        for
          rawSrc  <- payload.string("src", context.path)
          src     <- source(policy, rawSrc, context.path)
          mediaId <- optionalMediaId(payload, context.path)
          alt     <- payload.string("alt", context.path)
          title   <- payload.optionalString("title", context.path)
          width   <- pixels(payload, "width", context.path)
          height  <- pixels(payload, "height", context.path)
        yield ImageNode(id, MediaReference(src, mediaId), alt, title, width, height)

  private def source(
      policy: MediaUrlPolicy,
      raw: String,
      at: DiagnosticPath
  ): Either[DecodeError, MediaUrl] =
    policy
      .parse(raw, at.field("src"))
      .left
      .map(error => DecodeError.InvalidValue(error.message, at.field("src")))

  private def optionalMediaId(
      payload: JsonValue.Obj,
      at: DiagnosticPath
  ): Either[DecodeError, Option[MediaId]] =
    payload.optionalString("mediaId", at).flatMap {
      case None      => Right(None)
      case Some(raw) =>
        MediaId
          .parse(raw)
          .map(Some(_))
          .toRight(DecodeError.InvalidValue(s"Keine gueltige MediaId: `$raw`", at.field("mediaId")))
    }

  /** A measurement that is not one is a decode error, not a silently dropped field.
    *
    * §19.2 asks decoding to check "Zahlenbereiche". Dropping a `width: 0` would produce a document
    * that differs from the payload without saying so, and the next round trip would quietly lose
    * it.
    */
  private def pixels(
      payload: JsonValue.Obj,
      name: String,
      at: DiagnosticPath
  ): Either[DecodeError, Option[PositivePixels]] =
    payload.get(name) match
      case None | Some(JsonValue.Null) => Right(None)
      case Some(_)                     =>
        payload.int(name, at).flatMap { value =>
          PositivePixels
            .parse(value)
            .map(Some(_))
            .toRight(
              DecodeError.InvalidValue(
                s"`$value` ist keine gueltige Pixelangabe (1 bis ${PositivePixels.max}).",
                at.field(name)
              )
            )
        }

  /** The image codec on top of the core ones -- what an application with pictures registers. */
  def support(policy: MediaUrlPolicy = MediaUrlPolicy.default): JsonSupport =
    CoreJsonSupport.all ++ JsonSupport.of(codec(policy))
