package ember.editor.html

import scala.collection.mutable

/** A parsed fragment and everything the safe profile took out of it. */
final case class HtmlParseResult(
    fragments: Vector[HtmlFragment],
    diagnostics: Vector[HtmlDiagnostic]
)
