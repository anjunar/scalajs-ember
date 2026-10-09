package ember.editor.codehighlighting

/** What a stretch of code is, as far as colour is concerned.
  *
  * ==Why a closed set==
  *
  * A theme is written once against these names and has to work for every language. A grammar that
  * could invent `scala-soft-keyword` would produce text no stylesheet colours, and the author would
  * find out by looking. The set is the vocabulary most themes already share -- Prism, highlight.js
  * and TextMate scopes all have a keyword, a string and a comment -- plus the few markup kinds that
  * Markdown needs.
  */
enum TokenKind(val cssName: String):
  case Keyword     extends TokenKind("keyword")
  case Type        extends TokenKind("type")
  case String      extends TokenKind("string")
  case Escape      extends TokenKind("escape")
  case Number      extends TokenKind("number")
  case Comment     extends TokenKind("comment")
  case Operator    extends TokenKind("operator")
  case Punctuation extends TokenKind("punctuation")
  case Function    extends TokenKind("function")
  case Variable    extends TokenKind("variable")
  case Constant    extends TokenKind("constant")
  case Tag         extends TokenKind("tag")
  case Attribute   extends TokenKind("attribute")
  case Property    extends TokenKind("property")
  case Meta        extends TokenKind("meta")
  case Heading     extends TokenKind("heading")
  case Emphasis    extends TokenKind("emphasis")
  case Strong      extends TokenKind("strong")
  case Link        extends TokenKind("link")
  case Code        extends TokenKind("code")

  /** What a stylesheet addresses: `::highlight(ember-tok-keyword)` in the editor, the class
    * `ember-tok-keyword` in static output. One name for both, so one theme serves both.
    */
  def className: String = s"ember-tok-$cssName"
