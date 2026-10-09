package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** Something worth knowing about a conversion that nonetheless produced a result.
  *
  * @param loss
  *   whether information was dropped. `Strict` refuses an export with any of these; a plain
  *   diagnostic never blocks anything.
  */
final case class MarkdownDiagnostic(
    message: String,
    span: Option[SourceSpan] = None,
    loss: Boolean = false
):
  def render: String =
    val where = span.map(value => s"${value.render}: ").getOrElse("")
    s"$where$message${if loss then " (Verlust)" else ""}"
