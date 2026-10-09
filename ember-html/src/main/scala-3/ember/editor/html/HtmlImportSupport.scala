package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** The rules an import runs with, in order. */
final class HtmlImportSupport private (
    val profile: HtmlImportProfile,
    val rules: Vector[HtmlImportRule]
):

  /** The rule with the highest priority that recognises this element. */
  def ruleFor(element: HtmlFragment.Element): Option[HtmlImportRule] =
    rules.filter(_.handles(element)).sortBy(-_.priority).headOption

  def withRules(more: HtmlImportRule*): HtmlImportSupport =
    new HtmlImportSupport(profile, rules ++ more)

  def ++(other: HtmlImportSupport): HtmlImportSupport =
    new HtmlImportSupport(profile, rules ++ other.rules)

object HtmlImportSupport:

  def of(profile: HtmlImportProfile, rules: HtmlImportRule*): HtmlImportSupport =
    new HtmlImportSupport(profile, rules.toVector)
