package ember.editor.html

/** Something the import did not take, said out loud.
  *
  * ==Why every loss is reported==
  *
  * Because §19.1's acceptance is "unterstuetzte Semantik bleibt erhalten, Verlust wird
  * diagnostiziert", and because a paste is the one operation where silence is indistinguishable
  * from theft. A user who pastes a table into an editor without tables has to be told that the
  * table is gone -- the text is there, the structure is not, and only a message makes the
  * difference visible.
  *
  * The same shape as `ParseDiagnostic` in the Markdown module, deliberately. Two formats, one idea
  * of what a loss report is.
  */
final case class HtmlDiagnostic(kind: HtmlLoss, detail: String):
  def render: String = s"${kind.render}: $detail"
