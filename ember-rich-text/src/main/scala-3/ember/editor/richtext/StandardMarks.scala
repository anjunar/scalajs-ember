package ember.editor.richtext

import ember.editor.core.*

/** The built-in text marks of the rich-text profile.
  *
  * §8.2 names exactly these five: "Built-ins: Strong, Emphasis, Underline, Strike, InlineCode."
  * They are case objects, not strings -- a mark's identity is its type, and its `MarkId` is a
  * wire name, not a dispatch key (§8.1).
  *
  * ==Why they carry no data==
  *
  * [[TextMark]] allows payload -- a language tag, a comment reference. None of these five needs
  * any: "strong" is not strong *in some way*. §8.2 rules out arbitrary CSS strings as a document
  * format, and a payload-free mark is the shortest way to make that impossible rather than
  * merely discouraged.
  *
  * ==Links are not here==
  *
  * §8.2: "Links sind Inline-Container, keine Text-Mark." A link has children and a target; a
  * mark has neither. P14 builds it as a node.
  */
object StandardMarks:

  case object Strong extends TextMark:
    val markId: MarkId = MarkId("ember.rich-text.strong/1")

  case object Emphasis extends TextMark:
    val markId: MarkId = MarkId("ember.rich-text.emphasis/1")

  case object Underline extends TextMark:
    val markId: MarkId = MarkId("ember.rich-text.underline/1")

  case object Strike extends TextMark:
    val markId: MarkId = MarkId("ember.rich-text.strike/1")

  /** Code inside a line. Not a code block -- that is a block type and comes with P15. */
  case object InlineCode extends TextMark:
    val markId: MarkId = MarkId("ember.rich-text.inline-code/1")

  val all: Vector[TextMark] = Vector(Strong, Emphasis, Underline, Strike, InlineCode)

  private val byId: Map[MarkId, TextMark] = all.map(mark => mark.markId -> mark).toMap

  def byMarkId(markId: MarkId): Option[TextMark] = byId.get(markId)

  // -----------------------------------------------------------------------------------------
  // Conflicts
  // -----------------------------------------------------------------------------------------

  /** Adds a mark, dropping the ones this profile considers incompatible with it.
    *
    * §8.2 leaves the decision to the profile: "Widersprueche und gegenseitiger Ausschluss werden
    * vom Profil bestimmt." This profile has exactly one rule, and it is about `InlineCode`.
    *
    * ==Why InlineCode is exclusive==
    *
    * Not for taste. `InlineCode` says "this text is not prose, render it verbatim", and every
    * other mark says something about prose. Markdown has no way to write bold inside a code
    * span -- backticks make their content literal, asterisks included -- so a run carrying both
    * would be a document that §18 cannot export without loss. Deciding it here, once, is
    * cheaper than a lossy diagnostic in every writer.
    *
    * The rule works in both directions: code displaces the others, and any other mark displaces
    * code. Neither wins by being applied second.
    */
  def add(marks: MarkSet, mark: TextMark): MarkSet =
    if mark == InlineCode then MarkSet.of(InlineCode)
    else if marks.contains(InlineCode.markId) then MarkSet.of(mark)
    else marks + mark

  /** Toggles a mark: removes it where it is set, adds it (with [[add]]'s rules) where it is not. */
  def toggle(marks: MarkSet, mark: TextMark): MarkSet =
    if marks.contains(mark.markId) then marks - mark.markId else add(marks, mark)
