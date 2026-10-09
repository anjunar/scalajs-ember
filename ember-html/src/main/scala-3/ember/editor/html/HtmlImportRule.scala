package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** Recognises an element and says what to make of it.
  *
  * ==Why a rule and not a table==
  *
  * Because recognition is not always by tag. `<b>` and `<span style="font-weight:bold">` mean the
  * same thing, `<div>` means half a dozen things depending on what is in it, and a `<p>` inside a
  * `<li>` is not a paragraph. A predicate can say all of that; a `Map[String, NodeType]` cannot.
  *
  * ==Priority==
  *
  * §19.1 names it. Two rules may both recognise an element -- a specific one for `<pre><code>` and
  * a general one for `<pre>` -- and the higher number decides. Within the same number, the order
  * the support was assembled in.
  */
trait HtmlImportRule:

  def name: String

  /** Higher goes first. */
  def priority: Int = 0

  def handles(element: HtmlFragment.Element): Boolean

  def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision

object HtmlImportRule:

  /** A rule that recognises a set of tags and builds a container. */
  def container(
      ruleName: String,
      tags: Set[String],
      mode: ChildMode = ChildMode.Inline,
      level: NodeLevel = NodeLevel.Block,
      rulePriority: Int = 0,
      whitespace: Whitespace = Whitespace.Collapse
  )(build: (HtmlFragment.Element, Vector[NodeId], HtmlImportScope) => EditorNode): HtmlImportRule =
    new HtmlImportRule:
      val name                                            = ruleName
      override val priority                               = rulePriority
      def handles(element: HtmlFragment.Element): Boolean = tags.contains(element.tag)
      def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
        HtmlImportDecision.Container(
          children => build(element, children, scope),
          level,
          mode,
          whitespace
        )

  /** A rule that turns a set of tags into one mark. */
  def mark(ruleName: String, tags: Set[String], value: TextMark): HtmlImportRule =
    new HtmlImportRule:
      val name                                            = ruleName
      def handles(element: HtmlFragment.Element): Boolean = tags.contains(element.tag)
      def decide(element: HtmlFragment.Element, scope: HtmlImportScope): HtmlImportDecision =
        HtmlImportDecision.Marked(value)
