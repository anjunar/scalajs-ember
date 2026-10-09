package ember.editor.markdown

/** A parsed source, and everything that came with it. */
final case class MarkdownDocument(
    id: SyntaxId,
    span: SourceSpan,
    children: Vector[MarkdownBlock]
) extends MarkdownBlock
