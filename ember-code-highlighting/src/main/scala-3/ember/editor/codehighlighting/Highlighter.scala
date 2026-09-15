package ember.editor.codehighlighting

import ember.editor.core.*

import scala.collection.mutable

/** How much a highlighter is willing to colour.
  *
  * A code block is text an author can paste anything into, and a highlighter runs on every
  * keystroke. Past these limits the block stays uncoloured -- readable, editable, and not in the
  * way.
  */
final case class HighlightLimits(maxChars: Int = 200000, maxLineChars: Int = 4000)

object HighlightLimits:
  val default: HighlightLimits = HighlightLimits()

/** One block's text at one revision, and the language it claims to be. */
final case class HighlightRequest(
    block: NodeId,
    revision: Revision,
    language: Option[String],
    text: String
)

/** Why a block has no colours. */
enum HighlightSkip:
  case NoLanguage
  case UnknownLanguage(name: String)
  case TooLarge(chars: Int, limit: Int)

/** The tokens for one request.
  *
  * Carries its revision and its text back, and that is the point: §5 counts the result of an
  * external effect as valid only for the revision it was computed for. A worker that answers late
  * answers a question nobody is asking any more, and [[HighlightScheduler]] can only tell because
  * the answer says which question it was.
  */
final case class HighlightResult(
    block: NodeId,
    revision: Revision,
    text: String,
    tokens: Vector[Token],
    skipped: Option[HighlightSkip]
)

/** Text in, token ranges out (X02: "Reiner Text→Tokenbereich-Service").
  *
  * Asynchronous by contract, even though the local implementation answers at once: a worker adapter
  * answers later or never, and a caller that relied on an immediate reply would break the day one
  * is plugged in. The subscription cancels a pending answer; one already given is not taken back.
  */
trait Highlighter:

  def highlight(request: HighlightRequest, reply: HighlightResult => Unit): Subscription

  /** The block is gone or no longer code; whatever is cached for it can go too. */
  def forget(block: NodeId): Unit

/** The highlighter that runs in the calling thread, with incremental relexing per block.
  *
  * The same code on a server and in a browser, so static output and the editor colour a block the
  * same way (X02: "SSR ohne Worker").
  */
final class LocalHighlighter(
    languages: HighlightLanguages = HighlightLanguages.standard,
    limits: HighlightLimits = HighlightLimits.default
) extends Highlighter:

  private val lexed = mutable.HashMap.empty[NodeId, LexedText]

  def highlight(request: HighlightRequest, reply: HighlightResult => Unit): Subscription =
    reply(compute(request))
    Subscription.cancelled

  def forget(block: NodeId): Unit = lexed.remove(block): Unit

  /** How many lines the last request for this block had to lex. For tests and measurements. */
  def lastRelexed(block: NodeId): Option[Int] = lexed.get(block).map(_.relexed)

  def compute(request: HighlightRequest): HighlightResult =
    def skipped(reason: HighlightSkip): HighlightResult =
      lexed.remove(request.block)
      HighlightResult(request.block, request.revision, request.text, Vector.empty, Some(reason))

    request.language match
      case None       => skipped(HighlightSkip.NoLanguage)
      case Some(name) =>
        languages.grammarFor(name) match
          case None => skipped(HighlightSkip.UnknownLanguage(name))
          case Some(_) if request.text.length > limits.maxChars =>
            skipped(HighlightSkip.TooLarge(request.text.length, limits.maxChars))
          case Some(grammar) =>
            val next = lexed.get(request.block) match
              case Some(previous) if previous.grammar eq grammar => previous.update(request.text)
              case _ => LexedText.lex(grammar, request.text, limits.maxLineChars)
            lexed(request.block) = next
            HighlightResult(request.block, request.revision, request.text, next.tokens, None)

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
