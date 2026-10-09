package ember.editor.browser

import ember.editor.core.*
import ember.editor.html.*
import org.scalajs.dom

import scala.collection.mutable

/** Why a server-rendered subtree does not match the document it claims to be. */
enum HydrationProblem:

  case TagMismatch(node: NodeId, expected: String, actual: String)
  case AttributeMismatch(node: NodeId, name: String, expected: String, actual: Option[String])
  case TextMismatch(node: NodeId, expected: String, actual: String)
  case ChildCount(node: NodeId, expected: Int, actual: Int)
  case NoSemantics(node: NodeId)
  case DetachedHost(node: NodeId)

  def render: String = this match
    case TagMismatch(node, expected, actual) =>
      s"`${node.value}`: erwartet `<$expected>`, gefunden `<$actual>`"
    case AttributeMismatch(node, name, expected, actual) =>
      s"`${node.value}`: `$name` sollte `$expected` sein, ist ${actual.map(value => s"`$value`").getOrElse("nicht gesetzt")}"
    case TextMismatch(node, expected, actual) =>
      s"`${node.value}`: Text sollte `$expected` sein, ist `$actual`"
    case ChildCount(node, expected, actual) =>
      s"`${node.value}`: $expected Kinder erwartet, $actual gefunden"
    case NoSemantics(node) =>
      s"`${node.value}`: keine HtmlSemantics registriert"
    case DetachedHost(node) =>
      s"`${node.value}`: der gebundene DOM-Host wurde abgetrennt oder ersetzt"
