package ember.editor.forms

import ember.editor.core.*
import ember.editor.json.*
import ember.editor.markdown.*

/** A whole document as a form value. */
private final class JsonFieldCodec(support: JsonSupport, pretty: Boolean) extends FieldCodec:

  val name = "json"

  /** An empty JSON field is not an empty string.
    *
    * A server that receives `""` cannot tell an empty document from a missing field. The empty
    * document's payload can, and it round-trips.
    */
  def emptyValue(schema: Schema, rootId: NodeId): String =
    Document
      .empty(schema, rootId)
      .toOption
      .flatMap(document => DocumentJson.encodeToString(document, support).toOption)
      .getOrElse("")

  def encode(document: Document): Either[EditorError, String] =
    val written =
      if pretty then
        DocumentJson.encode(document, support).map(value => JsonText.renderPretty(value))
      else DocumentJson.encodeToString(document, support)

    written.left.map(errors => FieldError.NotRepresentable(name, errors.map(_.message)))

  def decode(source: String, schema: Schema, rootId: NodeId): Either[EditorError, Document] =
    DocumentJson.decodeString(source, schema, support) match
      case Right(result) => Right(result.document)
      case Left(errors)  =>
        Left(FieldError.NotDecodable(name, errors.map(_.render).mkString("; ")))
