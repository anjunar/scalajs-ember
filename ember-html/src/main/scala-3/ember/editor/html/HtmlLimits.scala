package ember.editor.html

/** How much a fragment may be.
  *
  * A paste is the most convenient way to hand an editor a megabyte of markup, on purpose or by
  * accident, and every limit here has a failure it prevents rather than a number someone liked.
  */
final case class HtmlLimits(
    maxSourceChars: Int = 1 << 20,
    maxNodes: Int = 20000,
    maxDepth: Int = 100
)

object HtmlLimits:
  val default: HtmlLimits = HtmlLimits()
