package ember.editor.codehighlighting

import TokenKind.*

/** Language names and the grammars behind them.
  *
  * Looked up case-insensitively: a fence says `Scala` as often as `scala`, and neither author means
  * a different language. The name comes from [[ember.editor.code.CodeInfo]], so what is found here
  * is exactly what Markdown wrote and what `class="language-…"` says.
  */
final class HighlightLanguages private (byName: Map[String, Grammar]):

  def grammarFor(language: String): Option[Grammar] = byName.get(language.toLowerCase)

  def names: Set[String] = byName.keySet

  /** Adds or replaces a language under all of its names. */
  def withLanguage(grammar: Grammar, names: String*): HighlightLanguages =
    new HighlightLanguages(byName ++ names.map(_.toLowerCase -> grammar))

object HighlightLanguages:

  val empty: HighlightLanguages = new HighlightLanguages(Map.empty)

  /** The first set: Scala, JavaScript/TypeScript, JSON, HTML/XML, CSS, shell and Markdown. */
  lazy val standard: HighlightLanguages =
    empty
      .withLanguage(Grammars.scala, "scala", "sc", "sbt")
      .withLanguage(Grammars.javascript, "javascript", "js", "mjs", "cjs", "jsx")
      .withLanguage(Grammars.typescript, "typescript", "ts", "mts", "cts", "tsx")
      .withLanguage(Grammars.json, "json", "jsonc", "json5")
      .withLanguage(Grammars.html, "html", "htm", "xhtml", "xml", "svg")
      .withLanguage(Grammars.css, "css")
      .withLanguage(Grammars.shell, "shell", "sh", "bash", "zsh", "shellscript")
      .withLanguage(Grammars.markdown, "markdown", "md")
