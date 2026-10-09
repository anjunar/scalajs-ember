package ember.editor.browser

import ember.editor.core.*
import ember.editor.html.{HtmlSupport, RenderProfile}
import ember.editor.ui.DocumentView

/** What a repair came to. */
enum RecoveryOutcome:

  /** The view already says what the document says. Nothing to do. */
  case Clean

  /** The view was rebuilt from the document and now matches.
    *
    * @param attempt
    *   which try this was. §15.4 allows a limited number, and saying which one it took is the
    *   difference between "it worked" and "it worked, barely".
    */
  case Repaired(nodes: Vector[NodeId], attempt: Int)

  /** It did not take, and no further try is allowed.
    *
    * §15.4: "Bei erneuter Abweichung bleibt ein bedienbarer Source-Fallback mit verstaendlicher
    * Statusmeldung." What is left is the text, and someone has to be told.
    */
  case Exhausted(problems: Vector[HydrationProblem])

  def render: String = this match
    case Clean                    => "Die Ansicht stimmt mit dem Dokument ueberein."
    case Repaired(nodes, attempt) =>
      s"Ansicht in Versuch $attempt neu aufgebaut: ${nodes.map(_.value).mkString(", ")}"
    case Exhausted(problems) =>
      problems.map(_.render).mkString("Die Ansicht laesst sich nicht reparieren: ", "; ", "")
