package ember.editor.richtext

import ember.editor.core.*

/** A heading level. Typed, not an `Int`.
  *
  * The plan asks for "typisierte Level" and the reason shows up at the first call site: an `Int`
  * makes `SetHeading(0)` and `SetHeading(9)` compile, and both are documents that no renderer can
  * produce. HTML has six levels; so has this.
  */
enum HeadingLevel(val level: Int):
  case H1 extends HeadingLevel(1)
  case H2 extends HeadingLevel(2)
  case H3 extends HeadingLevel(3)
  case H4 extends HeadingLevel(4)
  case H5 extends HeadingLevel(5)
  case H6 extends HeadingLevel(6)

object HeadingLevel:

  /** For wire formats, where the level arrives as a number. `None` if it is not one of the six. */
  def fromInt(level: Int): Option[HeadingLevel] = values.find(_.level == level)
