package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** Markdown written from a document, with what it could not carry (§18.2). */
final case class EncodedMarkdown(
    source: String,
    diagnostics: Vector[MarkdownDiagnostic] = Vector.empty
):
  /** What was dropped. Empty under [[LossPolicy.Strict]], by construction. */
  def losses: Vector[MarkdownDiagnostic] = diagnostics.filter(_.loss)
