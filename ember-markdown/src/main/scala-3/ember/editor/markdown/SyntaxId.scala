package ember.editor.markdown

/** Identity of a block inside one parse result.
  *
  * Opaque and mintable only by the parser: an id that did not come from a parse cannot address
  * anything, and letting one be fabricated would make [[SourceMap]] lookups silently miss. There is
  * nothing to validate here -- the door exists to keep the numbering the parser's business, not to
  * check a value.
  */
opaque type SyntaxId = Int

object SyntaxId:

  private[markdown] def apply(value: Int): SyntaxId = value

  extension (id: SyntaxId)
    def value: Int     = id
    def render: String = s"#$id"
