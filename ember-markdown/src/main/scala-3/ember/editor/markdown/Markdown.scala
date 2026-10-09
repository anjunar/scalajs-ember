package ember.editor.markdown

import scala.collection.mutable
import scala.util.matching.Regex

/** The entry point of this module.
  *
  * §18.1: "`ember-markdown` enthaelt einen in Scala geschriebenen Parser und Writer. Er braucht
  * weder Lexical noch DOM noch HTML als Zwischenstufe."
  */
object Markdown:

  /** Parses a source into block syntax.
    *
    * Deterministic and free of side effects: the same source and profile give the same tree, on any
    * platform, with no DOM and no clock involved (P17, acceptance).
    *
    * Returns `Either` rather than throwing, because every failure here is a resource bound, and a
    * resource bound is an expected outcome for foreign input rather than a broken call
    * (`ember-core/package.scala`).
    */
  def parseSyntax(
      source: String,
      profile: MarkdownProfile = MarkdownProfile.commonMarkSafe
  ): Either[ParseError, ParseResult] =
    val limits = profile.limits

    if source.length > limits.maxSourceChars then
      Left(ParseError.LimitExceeded("maxSourceChars", limits.maxSourceChars, source.length))
    else new BlockParser(source, profile).run()
