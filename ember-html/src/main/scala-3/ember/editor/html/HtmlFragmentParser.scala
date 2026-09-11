package ember.editor.html

import scala.collection.mutable

/** Why a fragment could not be read at all.
  *
  * The same shape the Markdown module uses for the same situation, and for the same reason: a limit
  * is not a diagnosis. A diagnosis says what was lost from a result; this says there is no result.
  */
enum HtmlParseError:

  case LimitExceeded(limit: String, allowed: Int, found: Int)

  def render: String = this match
    case LimitExceeded(limit, allowed, found) =>
      s"Grenze `$limit` ueberschritten: $allowed erlaubt, $found gefunden."

/** A parsed fragment and everything the safe profile took out of it. */
final case class HtmlParseResult(
    fragments: Vector[HtmlFragment],
    diagnostics: Vector[HtmlDiagnostic]
)

/** Builds a fragment tree out of tokens, sanitising as it goes.
  *
  * ==Sanitising happens here, not afterwards==
  *
  * §19.1 describes `HtmlFragment` as carrying "typisierte zulaessige Attribute" -- so a fragment is
  * already the safe form, and there is no moment at which an unsafe one exists. That is not a
  * convenience: a two-step design ("parse everything, clean it later") has a window in which the
  * dangerous version is a value that something else could read, and every such window has
  * eventually been walked through.
  *
  * ==The four recovery rules, and why exactly these==
  *
  * §19.1 asks for "feste Recovery-Regeln fuer unterstuetzte Clipboard-HTML-Faelle" and rules out
  * full tree construction in the same breath. So these are not a subset of the HTML5 algorithm
  * chosen for convenience; they are the four shapes real fragments take:
  *
  *   1. '''Void elements never get children.''' `<br>`, `<img>`, `<hr>` -- written with a slash,
  *      without one, closed or not. All four spellings occur and all four mean the same.
  *   1. '''A close tag that matches something open closes through to it.''' `<b><i>x</b>` closes
  *      the `<i>` too. A browser reopens the `<i>` afterwards (the adoption agency algorithm); this
  *      does not, and the difference is one formatting boundary in a rare shape.
  *   1. '''A close tag that matches nothing open is ignored.''' `</div>` at the start of a fragment
  *      copied from the middle of a page. Treating it as text would paste `</div>`.
  *   1. '''Some tags close their own kind.''' `<p>` closes an open `<p>`, `<li>` an open `<li>`.
  *      Word emits unclosed `<p>` and `<li>` constantly.
  *
  * Whatever is still open at the end is closed. A fragment is not a document and is usually not
  * well formed.
  */
object HtmlFragmentParser:

  /** Elements that cannot contain anything. */
  val voidTags: Set[String] = Set(
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr"
  )

  /** Which tags an open tag implicitly closes.
    *
    * Only the ones that occur. A table of the whole HTML content model would be a claim to full
    * tree construction, and §19.1 declines that claim.
    */
  private val closes: Map[String, Set[String]] = Map(
    "p"          -> Set("p"),
    "li"         -> Set("li"),
    "dt"         -> Set("dt", "dd"),
    "dd"         -> Set("dt", "dd"),
    "tr"         -> Set("tr", "td", "th"),
    "td"         -> Set("td", "th"),
    "th"         -> Set("td", "th"),
    "h1"         -> Set("p"),
    "h2"         -> Set("p"),
    "h3"         -> Set("p"),
    "h4"         -> Set("p"),
    "h5"         -> Set("p"),
    "h6"         -> Set("p"),
    "ul"         -> Set("p"),
    "ol"         -> Set("p"),
    "blockquote" -> Set("p"),
    "pre"        -> Set("p"),
    "div"        -> Set("p"),
    "table"      -> Set("p"),
    "hr"         -> Set("p")
  )

  def parse(
      html: String,
      policy: HtmlImportPolicy = HtmlImportPolicy.default
  ): Either[HtmlParseError, HtmlParseResult] =
    if html.length > policy.limits.maxSourceChars then
      Left(
        HtmlParseError.LimitExceeded("maxSourceChars", policy.limits.maxSourceChars, html.length)
      )
    else build(HtmlTokenizer.tokenize(html, policy.entities), policy)

  def build(
      tokens: Vector[HtmlToken],
      policy: HtmlImportPolicy
  ): Either[HtmlParseError, HtmlParseResult] =
    val roots                            = mutable.ArrayBuffer.empty[HtmlFragment]
    val diagnostics                      = mutable.ArrayBuffer.empty[HtmlDiagnostic]
    var nodes                            = 0
    var overflow: Option[HtmlParseError] = None

    /** One element being built. `kept = false` means it and its children are being discarded. */
    final class Open(val tag: String, val attributes: Vector[HtmlAttribute], val kept: Boolean):
      val children = mutable.ArrayBuffer.empty[HtmlFragment]

    val stack = mutable.ArrayBuffer.empty[Open]

    def dropping: Boolean = stack.exists(!_.kept)

    def append(fragment: HtmlFragment): Unit =
      if dropping then ()
      else
        nodes += 1
        if nodes > policy.limits.maxNodes then
          overflow = overflow.orElse(
            Some(HtmlParseError.LimitExceeded("maxNodes", policy.limits.maxNodes, nodes))
          )
        else if stack.isEmpty then roots += fragment
        else stack.last.children += fragment

    def finish(): Unit =
      val open = stack.remove(stack.length - 1)
      if open.kept then
        append(HtmlFragment.Element(open.tag, open.attributes, open.children.toVector))

    def closeThrough(tag: String): Unit =
      val depth = stack.lastIndexWhere(_.tag == tag)
      if depth >= 0 then while stack.length > depth do finish()

    /** Reads the attributes the profile allows, reporting the ones with a reason. */
    def attributesOf(tag: String, raw: Vector[(String, String)]): Vector[HtmlAttribute] =
      raw.flatMap { (name, value) =>
        policy.attribute(name, value) match
          case Right(attribute) => Some(attribute)
          case Left(reason)     =>
            if reason != HtmlImportPolicy.Silent then
              diagnostics += HtmlDiagnostic(HtmlLoss.DroppedAttribute, s"<$tag $name>: $reason")
            None
      }

    tokens.foreach { token =>
      if overflow.isEmpty then
        token match
          case HtmlToken.Text(value) =>
            if value.nonEmpty then append(HtmlFragment.Text(value))

          case HtmlToken.Open(tag, raw, selfClosing) =>
            closes.get(tag).foreach { implied =>
              if stack.nonEmpty && implied.contains(stack.last.tag) then
                closeThrough(stack.last.tag)
            }

            val kept = !policy.drops(tag)
            if !kept && !dropping then
              diagnostics += HtmlDiagnostic(HtmlLoss.DroppedElement, s"<$tag>")

            if stack.length >= policy.limits.maxDepth then
              overflow = Some(
                HtmlParseError.LimitExceeded("maxDepth", policy.limits.maxDepth, stack.length + 1)
              )
            else if voidTags.contains(tag) || selfClosing then
              if kept then append(HtmlFragment.Element(tag, attributesOf(tag, raw)))
            else stack += new Open(tag, if kept then attributesOf(tag, raw) else Vector.empty, kept)

          case HtmlToken.Close(tag) =>
            // A void element has no end tag to honour; `</br>` is a Word artefact, not a boundary.
            if !voidTags.contains(tag) then closeThrough(tag)

          // Comments and doctypes carry nothing a document can hold. A Word conditional comment
          // carries markup, but reading it would mean parsing what a browser deliberately hides.
          case HtmlToken.Comment(_) => ()
          case HtmlToken.Doctype(_) => ()
    }

    while stack.nonEmpty do finish()

    overflow.toLeft(HtmlParseResult(roots.toVector, diagnostics.toVector))

  /** The text of a fragment and everything under it.
    *
    * §19.1's fallback for anything the rules cannot place: "unbekannte harmlose Wrapper werden mit
    * erhaltenem Text aufgeloest", and an unsafe image keeps its alt text. Losing structure is a
    * diagnosis; losing the words is not acceptable.
    */
  def textOf(fragment: HtmlFragment): String = fragment match
    case HtmlFragment.Text(value)         => value
    case HtmlFragment.Element(_, _, kids) => kids.map(textOf).mkString
