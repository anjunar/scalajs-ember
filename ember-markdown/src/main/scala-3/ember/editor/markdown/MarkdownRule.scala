package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** The typed bridge between Markdown syntax and document nodes (§18.1).
  *
  * ==Why rules and not a match statement==
  *
  * §18.1: "Typisierte Regeln verbinden Syntax und registrierte NodeTypes; Standardregeln liegen im
  * Integrationsmodul." A `match` over node types would have to name them, and this module is not
  * allowed to know any -- §6 gives it the core alone. So the mapping arrives from outside, one rule
  * per kind, and an application with its own block types adds a rule instead of patching a match.
  *
  * ==Three kinds of rule, because the syntax has three shapes==
  *
  *   - [[MarkdownBlockRule]] -- a block becomes a node. Paragraph, heading, quote, list, code.
  *   - [[MarkdownInlineRule]] -- an inline becomes one or more nodes. Text, image, link.
  *   - [[MarkdownMarkRule]] -- an inline becomes a '''mark''', not a node. Emphasis, strong, inline
  *     code.
  *
  * The third is the one that is easy to miss and impossible to bolt on afterwards. `*a*` is not a
  * node containing a run; it is a run carrying a mark (§8.2). The codec therefore carries a
  * [[MarkSet]] down the inline tree instead of building a wrapper, and a rule that wanted a wrapper
  * would have produced a document the rich-text profile refuses.
  */
trait MarkdownRule:

  /** Shown in diagnostics. Must be unique inside one [[MarkdownSupport]]. */
  def id: String
