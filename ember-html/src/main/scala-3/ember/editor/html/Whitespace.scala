package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** How an element's text is spaced.
  *
  * HTML collapses runs of whitespace, and clipboard HTML is full of indentation that is not content
  * -- a newline and four spaces between two `<li>` tags mean nothing. `<pre>` is the exception, and
  * it is an exception a rule has to declare, because only the rule knows.
  */
enum Whitespace:
  case Collapse, Preserve
