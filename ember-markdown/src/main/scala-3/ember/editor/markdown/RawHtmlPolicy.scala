package ember.editor.markdown

/** What becomes of raw HTML. */
enum RawHtmlPolicy:

  /** Kept as visible text, never executed. §18.1's answer for the safe profile. */
  case AsText
