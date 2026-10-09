package ember.editor.codehighlighting

import ember.editor.core.*

import scala.collection.mutable

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
