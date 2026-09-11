package ember.editor.markdown

/** What a parse is allowed to recognise, and what it promises.
  *
  * ==Why the profile carries the conformance level==
  *
  * §18.1 is explicit: "Bis die Konformitaetsfaelle vollstaendig bestanden sind, wird nur die
  * tatsaechlich getestete Teilmenge beworben." A comment saying so would be a promise; a field
  * saying so is a value a caller can read and branch on.
  *
  * An application that wants to know whether emphasis will survive a round trip asks the profile
  * instead of guessing from the version number. And what [[Conformance.Inlines]] is worth is a
  * measured number, not the word -- see `CommonMarkConformanceSpec`.
  *
  * @param name
  *   Shown in diagnostics. §18.1 names the target profile `CommonMarkSafe`.
  * @param specVersion
  *   The CommonMark version the rules follow. A string, because that is what the spec calls itself,
  *   and because comparing it is nobody's business.
  * @param conformance
  *   How much of the syntax this parse actually resolves.
  * @param limits
  *   Resource bounds; see [[ParseLimits]].
  * @param rawHtml
  *   What happens to raw HTML. §18.1 fixes the answer for the safe profile.
  * @param entities
  *   Which named character references are resolved; see [[EntityTable]]. Numeric references need no
  *   table and are always complete.
  */
final case class MarkdownProfile(
    name: String,
    specVersion: String,
    conformance: Conformance,
    limits: ParseLimits,
    rawHtml: RawHtmlPolicy,
    entities: EntityTable
)

object MarkdownProfile:

  /** The CommonMark version these block rules were ported from. */
  val specVersion: String = "0.31.2"

  /** The target profile of §18.1.
    *
    * Safe means one thing here and it is worth stating plainly: raw HTML is '''kept as visible
    * text''' and never executed. That decision belongs to the profile and not to the parser,
    * because the parser has to recognise HTML blocks either way -- their block boundaries differ
    * from a paragraph's, and a parser that ignored them would get the structure '''around''' them
    * wrong too.
    */
  val commonMarkSafe: MarkdownProfile = MarkdownProfile(
    name = "CommonMarkSafe",
    specVersion = specVersion,
    conformance = Conformance.Inlines,
    limits = ParseLimits.default,
    rawHtml = RawHtmlPolicy.AsText,
    entities = EntityTable.common
  )

  /** The same rules under paste-sized bounds. For a source a user did not write. */
  val untrustedPaste: MarkdownProfile =
    commonMarkSafe.copy(name = "CommonMarkSafe/Paste", limits = ParseLimits.paste)

  /** Block structure only -- for a table of contents or a search index, where resolving emphasis
    * and links would be work nobody reads.
    */
  val blocksOnly: MarkdownProfile =
    commonMarkSafe.copy(name = "Blocks", conformance = Conformance.BlocksOnly)

/** How much of the syntax a profile actually resolves.
  *
  * Ordered by strength, and there are deliberately only two: what exists now, and what P18 will
  * add. A third value for "we think it is mostly right" would be exactly the claim §18.1 forbids.
  */
enum Conformance:

  /** Block structure per CommonMark; inline content left as source.
    *
    * Emphasis, links, code spans, entities and backslash escapes are '''not''' resolved -- a
    * paragraph holds a single [[MarkdownInline.Text]] with the raw source. Link reference
    * definitions are likewise left standing, because resolving them needs the inline parser.
    *
    * Useful for a cheap outline: a table of contents, a search index, a document map.
    */
  case BlocksOnly

  /** Blocks and inlines, to the extent the conformance suite records.
    *
    * The name states the ambition, not a proof. What this profile actually reproduces is a number,
    * measured against the versioned corpus and asserted exactly in `CommonMarkConformanceSpec`.
    * §18.1 asks for the number and not for the word.
    */
  case Inlines

/** What becomes of raw HTML. */
enum RawHtmlPolicy:

  /** Kept as visible text, never executed. §18.1's answer for the safe profile. */
  case AsText
