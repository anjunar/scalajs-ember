package ember.editor.html

/** Something the import did not take, said out loud.
  *
  * ==Why every loss is reported==
  *
  * Because §19.1's acceptance is "unterstuetzte Semantik bleibt erhalten, Verlust wird
  * diagnostiziert", and because a paste is the one operation where silence is indistinguishable
  * from theft. A user who pastes a table into an editor without tables has to be told that the
  * table is gone -- the text is there, the structure is not, and only a message makes the
  * difference visible.
  *
  * The same shape as `ParseDiagnostic` in the Markdown module, deliberately. Two formats, one
  * idea of what a loss report is.
  */
final case class HtmlDiagnostic(kind: HtmlLoss, detail: String):
  def render: String = s"${kind.render}: $detail"

/** What kind of thing was lost. */
enum HtmlLoss:

  /** An element the profile refuses outright -- a script, a style block, an active embed. */
  case DroppedElement

  /** An attribute that is not part of the document format (§19.1). */
  case DroppedAttribute

  /** An element nobody claimed; its children were kept. */
  case UnwrappedElement

  /** A URL the policy refused. The content around it usually survives. */
  case RefusedUrl

  /** A limit stopped the import. */
  case LimitExceeded

  def render: String = this match
    case DroppedElement   => "Element verworfen"
    case DroppedAttribute => "Attribut verworfen"
    case UnwrappedElement => "Element aufgeloest"
    case RefusedUrl       => "Adresse abgelehnt"
    case LimitExceeded    => "Grenze erreicht"

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

/** The safe fragment profile (§19.1).
  *
  * ==What is refused, and on what grounds==
  *
  * §19.1 names four things and this implements exactly those:
  *
  *   - '''script, style, active embeds are discarded''' -- content and all. Their content is not
  *     prose, and keeping it as text would paste a stylesheet into a paragraph.
  *   - '''Event attributes are not taken.''' `onerror`, `onclick` and the rest, by prefix rather
  *     than by list -- a list would be a list to keep up to date.
  *   - '''Arbitrary CSS is not taken.''' `style` carries the whole cascade, including
  *     `position: fixed` and `url(...)`, and §19.1 rules it out as a document format in the same
  *     words the export side uses.
  *   - '''Unknown harmless wrappers are dissolved with their text kept.''' That is the import
  *     driver's part; here it is only the list of what counts as harmful.
  *
  * ==What this policy does not decide==
  *
  * Whether a `href` or a `src` is acceptable. §19.1 sends URLs "durch die jeweilige Link-/Media-
  * Policy", and those live in `ember-link` and `ember-image`, which this module may not import
  * (§7). What happens here is the part that belongs to the format: normalisation, and a baseline
  * refusal of the schemes that are dangerous in any profile. The rule that builds a link asks its
  * own policy afterwards.
  */
final case class HtmlImportPolicy(
    droppedTags: Set[String] = HtmlImportPolicy.unsafeTags,
    entities: HtmlEntities = HtmlEntities.common,
    limits: HtmlLimits = HtmlLimits.default
):

  /** Whether an element and everything in it goes. */
  def drops(tag: String): Boolean = droppedTags.contains(tag)

  /** Reads one raw attribute, or says why not.
    *
    * `Left` carries the reason so the caller can report it; an attribute that is simply not part
    * of the document format is not worth a message on its own, and [[HtmlImportPolicy.Silent]]
    * marks those.
    */
  def attribute(name: String, value: String): Either[String, HtmlAttribute] =
    if name.startsWith("on") && name.length > 2 then Left("Eventattribut")
    else if name == "style" then Left("freies CSS")
    else if name.startsWith("data-ember-") then
      // §19.1: "browserseitige Wrapper/Editor-Attribute werden beim Austausch entfernt." A paste
      // that carried node ids would import somebody else's identity into this document.
      Left("Editor-Attribut")
    else if name == "class" then
      // The one class §19.1 allows through, because a code block's language lives in it. Word's
      // `MsoNormal` and a framework's utility classes are not document content.
      if value.split(' ').exists(_.startsWith("language-")) then
        HtmlAttribute.parse(name, value).toRight(HtmlImportPolicy.Silent)
      else Left(HtmlImportPolicy.Silent)
    else
      HtmlAttribute.parse(name, value).toRight(HtmlImportPolicy.Silent)

  /** A URL as the format leaves it: entity-decoded, trimmed, control characters removed.
    *
    * §19.1: "URLs werden nach Entities-/Whitespace-Normalisierung durch die jeweilige
    * Link-/Media-Policy geprueft." The normalisation is the part that has to happen first,
    * because `java script:` and `java&#9;script:` are the oldest way past a scheme check.
    */
  def normaliseUrl(raw: String): String =
    val out = new StringBuilder(raw.length)
    raw.foreach(char => if char > ' ' && char != '\u007f' then out.append(char))
    out.result()

  /** Whether a URL is refused whatever the feature policy says.
    *
    * A baseline, not the decision. The link and media policies are stricter and know more; this
    * catches the schemes that are wrong in every profile, so that a rule which forgets to ask
    * still cannot import a `javascript:` href.
    */
  def refusesUrl(url: String): Boolean =
    val scheme = url.takeWhile(_ != ':').toLowerCase
    url.contains(':') && HtmlImportPolicy.unsafeSchemes.contains(scheme)

object HtmlImportPolicy:

  /** The reason that is not worth reporting: an attribute the document format simply has no place
    * for. Reporting every `cellpadding` from a Word table would bury the losses that matter.
    */
  val Silent: String = ""

  /** Discarded with their content (§19.1). */
  val unsafeTags: Set[String] = Set(
    "script", "style", "iframe", "frame", "frameset", "object", "embed", "applet",
    "noscript", "noembed", "template", "link", "meta", "base", "title", "head",
    "svg", "math", "canvas", "audio", "video", "source", "track", "form", "input",
    "button", "select", "option", "textarea", "label", "fieldset"
  )

  /** Schemes no document format accepts. */
  val unsafeSchemes: Set[String] = Set("javascript", "vbscript", "data", "file", "about")

  val default: HtmlImportPolicy = HtmlImportPolicy()

  /** Everything the safe profile allows, and nothing to be decided later. For a test. */
  def strict: HtmlImportPolicy = default
