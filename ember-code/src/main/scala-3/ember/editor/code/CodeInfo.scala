package ember.editor.code

import ember.editor.core.*

/** What a fence says about its content.
  *
  * §18.2 asks for "Info-/Sprachmetadaten typisiert behandeln", and the split below is what that
  * means in practice. Markdown writes one info string; its '''first word''' is the language and
  * every renderer treats it as such, while the rest is whatever the author's toolchain wanted:
  *
  * {{{
  * ```scala {highlight=3-5}
  *      ^^^^^ language   ^^^^^^^^^^^^^^ meta
  * }}}
  *
  * Keeping them apart lets [[CodeSupport]] write `language-scala` without inventing a parser, and
  * lets P18 write the info string back unchanged. Keeping them together as one string would force
  * every consumer to split it again, each in its own slightly different way.
  */
final case class CodeInfo(language: Option[CodeLanguage] = None, meta: Option[String] = None):

  /** The info string as Markdown writes it. Empty when there is nothing to say. */
  def render: String =
    (language.map(_.value).toVector ++ meta.toVector).mkString(" ")

object CodeInfo:

  val empty: CodeInfo = CodeInfo()

  def of(language: String): CodeInfo = CodeInfo(CodeLanguage.parse(language))

  /** Reads an info string: first word is the language, the rest is meta.
    *
    * Never fails. An info string that names no valid language is not an error -- it is an info
    * string without a language, and the text is kept as meta so that a round trip does not lose it
    * (§18.2: "Inhalt einschliesslich innerer Leerzeilen erhalten").
    */
  def parse(info: String): CodeInfo =
    val trimmed = info.trim
    if trimmed.isEmpty then empty
    else
      val (head, rest) = trimmed.span(!_.isWhitespace)
      val meta         = Some(rest.trim).filter(_.nonEmpty)

      CodeLanguage.parse(head) match
        case Some(language) => CodeInfo(Some(language), meta)
        case None           => CodeInfo(None, Some(trimmed))
