package ember.editor.codehighlighting

import ember.editor.core.*

import scala.collection.mutable

/** Coloured HTML for output that is not edited: an export, a read-only page, an e-mail.
  *
  * Never used by the editor projection, and deliberately so. The server-rendered editor has to be
  * exactly what the browser projection builds, or hydration refuses it (§17) -- and the projection
  * builds one text node per code block. Spans are for pages that stay what they are.
  */
object StaticHighlight:

  def html(text: String, tokens: Vector[Token]): String =
    val out = new StringBuilder(text.length + tokens.length * 32)
    var at  = 0
    tokens.foreach { token =>
      if token.start >= at && token.end <= text.length then
        escape(text, at, token.start, out)
        out.append("<span class=\"").append(token.kind.className).append("\">")
        escape(text, token.start, token.end, out)
        out.append("</span>")
        at = token.end
    }
    escape(text, at, text.length, out)
    out.result()

  /** The whole block, lexed from scratch. Unknown languages come back escaped and uncoloured. */
  def html(
      text: String,
      language: Option[String],
      languages: HighlightLanguages = HighlightLanguages.standard,
      limits: HighlightLimits = HighlightLimits.default
  ): String =
    language.flatMap(languages.grammarFor) match
      case Some(grammar) if text.length <= limits.maxChars =>
        html(text, LexedText.lex(grammar, text, limits.maxLineChars).tokens)
      case _ => html(text, Vector.empty)

  private def escape(text: String, from: Int, until: Int, out: StringBuilder): Unit =
    var index = from
    while index < until do
      text.charAt(index) match
        case '&'   => out.append("&amp;")
        case '<'   => out.append("&lt;")
        case '>'   => out.append("&gt;")
        case other => out.append(other)
      index += 1
