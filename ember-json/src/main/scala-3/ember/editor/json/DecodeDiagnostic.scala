package ember.editor.json

import ember.editor.core.*

/** Ein Hinweis, der das Dekodieren nicht verhindert hat.
  *
  * Getrennt von [[DecodeError]], weil der Unterschied fuer den Aufrufer zaehlt: ein Fehler
  * bedeutet, dass kein Dokument entstanden ist, eine Diagnose, dass eines entstand und etwas daran
  * bemerkenswert ist.
  */
final case class DecodeDiagnostic(message: String, path: DiagnosticPath):
  def render: String = s"${path.render}: $message"
