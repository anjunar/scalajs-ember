package ember.editor.markdown

/** What a parse is allowed to recognise, and what it promises.
  *
  * ==Why the profile carries the conformance level==
  *
  * §18.1 is explicit: "Bis die Konformitaetsfaelle vollstaendig bestanden sind, wird nur die
  * tatsaechlich getestete Teilmenge beworben." A comment saying so would be a promise; a field
  * saying so is a value a caller can read and branch on.
  *
  * After P17 that field says [[Conformance.BlocksOnly]] on every profile in this file, and it
  * will keep saying it until P18 lands the inline parser. An application that wants to know
  * whether emphasis will survive a round trip asks the profile instead of guessing from the
  * version number.
  *
  * @param name
  *   Shown in diagnostics. §18.1 names the target profile `CommonMarkSafe`.
  * @param specVersion
  *   The CommonMark version the rules follow. A string, because that is what the spec calls
  *   itself, and because comparing it is nobody's business.
  * @param conformance
  *   How much of the syntax this parse actually resolves.
  * @param limits
  *   Resource bounds; see [[ParseLimits]].
  * @param rawHtml
  *   What happens to raw HTML. §18.1 fixes the answer for the safe profile.
  */
final case class MarkdownProfile(
    name: String,
    specVersion: String,
    conformance: Conformance,
    limits: ParseLimits,
    rawHtml: RawHtmlPolicy
)

object MarkdownProfile:

  /** The CommonMark version these block rules were ported from. */
  val specVersion: String = "0.31.2"

  /** The target profile of §18.1.
    *
    * Safe means one thing here and it is worth stating plainly: raw HTML is '''kept as visible
    * text''' and never executed. That decision belongs to the profile and not to the parser,
    * because the parser has to recognise HTML blocks either way -- their block boundaries
    * differ from a paragraph's, and a parser that ignored them would get the structure
    * '''around''' them wrong too.
    */
  val commonMarkSafe: MarkdownProfile = MarkdownProfile(
    name = "CommonMarkSafe",
    specVersion = specVersion,
    conformance = Conformance.BlocksOnly,
    limits = ParseLimits.default,
    rawHtml = RawHtmlPolicy.AsText
  )

  /** The same rules under paste-sized bounds. For a source a user did not write. */
  val untrustedPaste: MarkdownProfile =
    commonMarkSafe.copy(name = "CommonMarkSafe/Paste", limits = ParseLimits.paste)

/** How much of the syntax a profile actually resolves.
  *
  * Ordered by strength, and there are deliberately only two: what exists now, and what P18
  * will add. A third value for "we think it is mostly right" would be exactly the claim §18.1
  * forbids.
  */
enum Conformance:

  /** Block structure per CommonMark; inline content left as source.
    *
    * Emphasis, links, code spans, entities and backslash escapes are '''not''' resolved --
    * they are still in the `source` field of the paragraph or heading that holds them. Link
    * reference definitions are likewise still there, because resolving them needs the inline
    * parser.
    */
  case BlocksOnly

  /** Blocks and inlines. Reserved for P18; no profile carries it yet. */
  case Full

/** What becomes of raw HTML. */
enum RawHtmlPolicy:

  /** Kept as visible text, never executed. §18.1's answer for the safe profile. */
  case AsText
