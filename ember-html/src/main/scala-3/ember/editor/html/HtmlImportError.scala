package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** Why an import produced nothing. */
enum HtmlImportError:

  case Parse(error: HtmlParseError)

  /** The nodes were built but do not form a valid document. */
  case Invalid(violations: Vector[Violation])

  def render: String = this match
    case Parse(error)        => error.render
    case Invalid(violations) => violations.map(_.render).mkString("; ")
