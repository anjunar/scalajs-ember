package ember.editor.markdown

import ember.editor.core.*

/** Something worth saying about a parse that nonetheless succeeded.
  *
  * Separate from [[ParseError]] for the reason `DecodeDiagnostic` in `ember-json` gives: an error
  * means no tree exists, a diagnostic means one does and something about it is worth knowing.
  */
final case class ParseDiagnostic(message: String, span: SourceSpan):
  def render: String = s"${span.render}: $message"
