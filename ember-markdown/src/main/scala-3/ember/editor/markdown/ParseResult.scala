package ember.editor.markdown

import scala.collection.mutable
import scala.util.matching.Regex

/** A successful parse.
  *
  * @param document
  *   the block tree.
  * @param sourceMap
  *   source spans by block; see [[SourceMap]].
  * @param diagnostics
  *   things worth knowing about a tree that nonetheless exists.
  */
final case class ParseResult(
    document: MarkdownDocument,
    sourceMap: SourceMap,
    diagnostics: Vector[ParseDiagnostic] = Vector.empty
)
