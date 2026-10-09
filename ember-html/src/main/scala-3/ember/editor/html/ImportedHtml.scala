package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** A document read out of HTML, and what the reading cost. */
final case class ImportedHtml(document: Document, diagnostics: Vector[HtmlDiagnostic]):
  def lossless: Boolean = diagnostics.isEmpty
