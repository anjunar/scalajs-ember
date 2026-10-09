package ember.editor.markdown

/** Bullet or ordered, with what the source actually used. */
enum ListKind:

  /** `-`, `+` or `*`. The character matters: changing it starts a new list (§18.2). */
  case Bullet(marker: Char)

  /** `1.` or `1)`. `start` is the first item's number, `delimiter` is `.` or `)`. */
  case Ordered(start: Int, delimiter: Char)
