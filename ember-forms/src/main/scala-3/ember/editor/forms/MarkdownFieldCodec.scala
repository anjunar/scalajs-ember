package ember.editor.forms

import ember.editor.core.*
import ember.editor.json.*
import ember.editor.markdown.*

/** Markdown as a form value. */
private final class MarkdownFieldCodec(
    rules: MarkdownSupport,
    generator: NodeIdGenerator,
    profile: MarkdownProfile,
    loss: LossPolicy
) extends FieldCodec:

  val name = "markdown"

  /** An empty Markdown document really is an empty string. */
  def emptyValue(schema: Schema, rootId: NodeId): String = ""

  def encode(document: Document): Either[EditorError, String] =
    MarkdownCodec.encode(document, rules, loss) match
      case Right(written)                        => Right(written.source)
      case Left(MarkdownError.WouldLose(losses)) =>
        // Der Fall, um den es §16 geht. Die Diagnosen wandern mit, damit die Meldung sagt, '''was'''
        // nicht darstellbar ist -- "geht nicht" waere fuer den Benutzer nicht handhabbar.
        Left(FieldError.NotRepresentable(name, losses.map(_.message)))
      case Left(other) => Left(FieldError.NotRepresentable(name, Vector(other.message)))

  def decode(source: String, schema: Schema, rootId: NodeId): Either[EditorError, Document] =
    MarkdownCodec.decode(source, schema, rules, generator, rootId, profile) match
      case Right(decoded) => Right(decoded.document)
      case Left(error)    => Left(FieldError.NotDecodable(name, error.message))
