package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** A document built from Markdown, with the two source maps §18.2 asks for. */
final case class DecodedDocument(
    document: Document,
    sourceMap: DocumentSourceMap,
    syntax: ParseResult,
    diagnostics: Vector[MarkdownDiagnostic] = Vector.empty
)
