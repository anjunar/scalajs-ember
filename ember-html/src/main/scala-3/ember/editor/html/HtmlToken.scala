package ember.editor.html

import scala.collection.mutable

/** One piece of a fragment, as the tokenizer found it. */
enum HtmlToken:

  case Text(value: String)

  case Open(tag: String, attributes: Vector[(String, String)], selfClosing: Boolean)

  case Close(tag: String)

  /** Kept as a token rather than dropped, so the parser decides. A conditional comment from Word
    * carries markup, and the decision to throw it away is the policy's, not the scanner's.
    */
  case Comment(value: String)

  case Doctype(value: String)
