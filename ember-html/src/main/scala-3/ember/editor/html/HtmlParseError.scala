package ember.editor.html

import scala.collection.mutable

/** Why a fragment could not be read at all.
  *
  * The same shape the Markdown module uses for the same situation, and for the same reason: a limit
  * is not a diagnosis. A diagnosis says what was lost from a result; this says there is no result.
  */
enum HtmlParseError:

  case LimitExceeded(limit: String, allowed: Int, found: Int)

  def render: String = this match
    case LimitExceeded(limit, allowed, found) =>
      s"Grenze `$limit` ueberschritten: $allowed erlaubt, $found gefunden."
