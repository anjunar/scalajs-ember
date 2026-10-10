package ember.editor.image

import ember.editor.core.*
import java.util.regex.Matcher

/** Bringing a URL into the shape a check may look at.
  *
  * ==Why this exists twice==
  *
  * `ember-link` has the same code. §6 puts both modules on the core and nothing else, so there is
  * no shared place to put it -- and inventing a `url-utils` module for one predicate and two
  * functions would be worse than the duplication. The two are kept in step by their tests, which
  * cover the same attacks.
  */
private[image] object UrlNormalisation:

  def normalise(raw: String): String = stripInsideScheme(decodeEntities(raw))

  /** Characters that carry no glyph and must not be allowed to hide inside a scheme.
    *
    * ==Why `isWhitespace` is not enough==
    *
    * Java's `Character.isWhitespace` deliberately excludes the non-breaking space (U+00A0), and
    * `isControl` covers only the Cc block -- so a zero-width space (U+200B), a word joiner (U+2060)
    * or a byte-order mark (U+FEFF) sail straight through. All of them are ignored by browsers
    * inside a URL, which is precisely what makes them useful for hiding a scheme:
    *
    * {{{
    * "java​script:alert(1)"   zero-width space
    * " javascript:alert(1)"   non-breaking space
    * "﻿javascript:alert(1)"   byte-order mark
    * }}}
    */
  def isInvisible(character: Char): Boolean =
    character.isWhitespace || character.isControl ||
      character == '\u00a0' ||                            // no-break space
      character == '\u1680' ||                            // ogham space mark
      (character >= '\u2000' && character <= '\u200f') || // en quad .. right-to-left mark
      (character >= '\u2028' && character <= '\u202f') || // line separator .. narrow nbsp
      (character >= '\u205f' && character <= '\u2064') || // medium math space .. invisible plus
      (character >= '\u2066' && character <= '\u206f') || // bidi isolates and overrides
      character == '\u3000' ||                            // ideographic space
      character == '\ufeff'                               // byte order mark

  private def decodeEntities(raw: String): String =
    val numeric = "&#(x?)([0-9A-Fa-f]+);?".r
    val decoded = numeric.replaceAllIn(
      raw,
      found =>
        val radix = if found.group(1).isEmpty then 10 else 16
        try
          val code = Integer.parseInt(found.group(2), radix)
          if code > 0 && code <= 0x10ffff then
            Matcher.quoteReplacement(String.valueOf(Character.toChars(code)))
          else Matcher.quoteReplacement(found.matched)
        catch case _: Throwable => Matcher.quoteReplacement(found.matched)
    )

    decoded.replace("&amp;", "&").replace("&colon;", ":").replace("&tab;", "\t")

  private def stripInsideScheme(value: String): String =
    val trimmed = value.dropWhile(isInvisible).reverse.dropWhile(isInvisible).reverse
    val colon   = trimmed.indexOf(':')

    if colon < 0 then trimmed.filterNot(isInvisible)
    else
      val head = trimmed.take(colon).filterNot(isInvisible)
      val tail = trimmed.drop(colon)
      if head.exists(character => character == '/' || character == '?' || character == '#') then
        trimmed
      else head.toLowerCase + tail

  def schemeOf(value: String): Option[String] =
    val colon = value.indexOf(':')
    if colon <= 0 then None
    else
      val head = value.take(colon)
      if head.head.isLetter &&
        head.forall(c => c.isLetterOrDigit || c == '+' || c == '-' || c == '.')
      then Some(head.toLowerCase)
      else None

  /** The host of an absolute URL, lower case. `None` when there is none to speak of. */
  def hostOf(value: String): Option[String] =
    val afterScheme = value.indexOf("://")
    if afterScheme < 0 then None
    else
      val rest      = value.drop(afterScheme + 3)
      val authority =
        rest.takeWhile(character => character != '/' && character != '?' && character != '#')
      val host = authority.dropWhile(_ != '@').drop(1) match
        case ""    => authority
        case after => after
      Some(host.takeWhile(_ != ':').toLowerCase).filter(_.nonEmpty)
