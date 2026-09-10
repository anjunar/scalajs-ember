package ember.editor.image

import ember.editor.core.*

/** A media source that has passed a [[MediaUrlPolicy]].
  *
  * Same construction as `LinkUrl` in `ember-link`, and for the same reason: there is no way to
  * build a [[MediaReference]] without one, and no way to build one except through a policy. A
  * document therefore cannot hold a source that nothing checked.
  */
opaque type MediaUrl = String

object MediaUrl:

  private[image] def trusted(value: String): MediaUrl = value

  extension (url: MediaUrl) def value: String = url

  given Ordering[MediaUrl] = Ordering.String

/** The application's own identifier for a stored file.
  *
  * Optional, and deliberately opaque to this module: what it means -- a database key, a hash, an
  * S3 object name -- is the application's business (§20, "Uploads sind ein Anwendungsservice").
  * What matters here is that it is a stable name and not a file, a handle or an object URL.
  */
opaque type MediaId = String

object MediaId:

  def parse(value: String): Option[MediaId] =
    val trimmed = value.trim
    if trimmed.nonEmpty && !trimmed.exists(character => character.isControl) then Some(trimmed)
    else None

  def apply(value: String): MediaId =
    parse(value).getOrElse(
      throw EditorContractViolation(s"Keine gueltige MediaId: `$value`")
    )

  extension (id: MediaId) def value: String = id

/** Where a picture comes from.
  *
  * §20 gives it two halves and both earn their place: the `src` is what a browser loads, the
  * [[MediaId]] is what the application knows about the file behind it. A document that carries
  * only the URL cannot be migrated when the storage moves; one that carries only the id cannot
  * be rendered by anything that does not know the application.
  *
  * '''What is not in here''': file data, an `ObjectURL`, an upload handle, a progress value.
  * §20 is explicit -- "Lokale Object-URL-Previews werden freigegeben und niemals serialisiert",
  * and P16's acceptance repeats it: "Keine Dateidaten oder Object-URL im Document." Those live
  * in the effect state of whoever is uploading, and they die with it.
  */
final case class MediaReference(src: MediaUrl, mediaId: Option[MediaId] = None)

/** Why a media source was refused. */
sealed trait MediaError extends EditorError

object MediaError:

  final case class Empty(override val path: DiagnosticPath) extends MediaError:
    def message: String = "Eine leere Adresse ist keine Bildquelle."

  final case class ForbiddenScheme(scheme: String, override val path: DiagnosticPath)
      extends MediaError:
    def message: String =
      s"Das Schema `$scheme` ist als Bildquelle nicht zugelassen. " +
        "§20 laesst `data:`, `blob:` und aktive Schemata ausdruecklich nicht als dauerhafte " +
        "MediaReference zu."

  final case class ProtocolRelative(override val path: DiagnosticPath) extends MediaError:
    def message: String =
      "`//host/...` uebernimmt das Schema der Seite und ist damit keine feste Quelle."

  final case class ForbiddenHost(host: String, override val path: DiagnosticPath)
      extends MediaError:
    def message: String = s"Der Host `$host` steht nicht auf der Allowlist."

/** Which media sources a profile accepts.
  *
  * ==Why this is not the link policy==
  *
  * §20 and P14's risk line insist on the separation: "Links und Media haben unterschiedliche
  * Policies." The reason is concrete. A link is something the reader chooses to follow; an image
  * is something the page loads on its own, from a host the author named, into the reader's
  * browser, with the reader's IP address attached. So the defaults differ:
  *
  *   - '''`https` only.''' §20: "Die Default-Policy erlaubt sichere absolute HTTPS-Quellen sowie
  *     eindeutige relative/interne Pfade. HTTP kann die Anwendung explizit erlauben." A picture
  *     loaded over `http` into an `https` page is mixed content that browsers block anyway.
  *   - '''No `mailto:` or `tel:`.''' They are perfectly good links and not pictures at all.
  *   - '''An optional host allowlist.''' §20 names it as "separate, deterministische
  *     Konfiguration" -- an editor may accept links anywhere and images only from its own CDN.
  *
  * ==What no policy does==
  *
  * Fetch anything. §20: "keine externe URL wird vom Parser oder SSR-Server automatisch
  * abgerufen." Checking a source is a decision about a string; whether the picture exists is a
  * question for the browser that eventually renders it, and asking it here would turn every
  * decode into a network call.
  *
  * @param hosts
  *   `None` accepts any host. A set accepts exactly those, compared lower case.
  */
final case class MediaUrlPolicy(
    schemes: Set[String] = Set("https"),
    allowRelative: Boolean = true,
    hosts: Option[Set[String]] = None
):

  /** Normalises and checks. The only way to obtain a [[MediaUrl]]. */
  def parse(raw: String, at: DiagnosticPath = DiagnosticPath.Root): Either[MediaError, MediaUrl] =
    val value = UrlNormalisation.normalise(raw)

    if value.isEmpty then Left(MediaError.Empty(at))
    else if value.startsWith("//") then Left(MediaError.ProtocolRelative(at))
    else
      UrlNormalisation.schemeOf(value) match
        case Some(scheme) if !schemes.contains(scheme) =>
          Left(MediaError.ForbiddenScheme(scheme, at))
        case Some(_) => checkHost(value, at)
        case None if allowRelative => Right(MediaUrl.trusted(value))
        case None                  => Left(MediaError.ForbiddenScheme("(relativ)", at))

  private def checkHost(value: String, at: DiagnosticPath): Either[MediaError, MediaUrl] =
    hosts match
      case None => Right(MediaUrl.trusted(value))
      case Some(allowed) =>
        UrlNormalisation.hostOf(value) match
          case Some(host) if allowed.map(_.toLowerCase).contains(host) =>
            Right(MediaUrl.trusted(value))
          case Some(host) => Left(MediaError.ForbiddenHost(host, at))
          case None       => Left(MediaError.ForbiddenHost("(unbekannt)", at))

  /** Like [[parse]], but throws. For sources that stand in the code. */
  def unsafe(raw: String): MediaUrl =
    parse(raw) match
      case Right(url)  => url
      case Left(error) => throw EditorContractViolation(error.render)

object MediaUrlPolicy:

  /** HTTPS and relative paths. §20's default. */
  val default: MediaUrlPolicy = MediaUrlPolicy()

  /** The same, plus plain HTTP. An explicit decision, as §20 requires. */
  val allowingHttp: MediaUrlPolicy = MediaUrlPolicy(schemes = Set("https", "http"))

  /** Only paths inside the application's own site. */
  val internalOnly: MediaUrlPolicy = MediaUrlPolicy(schemes = Set.empty)

/** Bringing a URL into the shape a check may look at.
  *
  * ==Why this exists twice==
  *
  * `ember-link` has the same code. §6 puts both modules on the core and nothing else, so there
  * is no shared place to put it -- and inventing a `url-utils` module for one predicate and two
  * functions would be worse than the duplication. The two are kept in step by their tests, which
  * cover the same attacks.
  */
private[image] object UrlNormalisation:

  def normalise(raw: String): String = stripInsideScheme(decodeEntities(raw))

  /** Characters that carry no glyph and must not be allowed to hide inside a scheme.
    *
    * ==Why `isWhitespace` is not enough==
    *
    * Java's `Character.isWhitespace` deliberately excludes the non-breaking space (U+00A0), and
    * `isControl` covers only the Cc block -- so a zero-width space (U+200B), a word joiner
    * (U+2060) or a byte-order mark (U+FEFF) sail straight through. All of them are ignored by
    * browsers inside a URL, which is precisely what makes them useful for hiding a scheme:
    *
    * {{{
    * "java​script:alert(1)"   zero-width space
    * " javascript:alert(1)"   non-breaking space
    * "﻿javascript:alert(1)"   byte-order mark
    * }}}
    */
  def isInvisible(character: Char): Boolean =
    character.isWhitespace || character.isControl ||
      character == '\u00a0' ||                            // no-break space
      character == '\u1680' ||                            // ogham space mark
      (character >= '\u2000' && character <= '\u200f') ||  // en quad .. right-to-left mark
      (character >= '\u2028' && character <= '\u202f') ||  // line separator .. narrow nbsp
      (character >= '\u205f' && character <= '\u2064') ||  // medium math space .. invisible plus
      (character >= '\u2066' && character <= '\u206f') ||  // bidi isolates and overrides
      character == '\u3000' ||                            // ideographic space
      character == '\ufeff'                               // byte order mark

  private def decodeEntities(raw: String): String =
    val numeric = "&#(x?)([0-9A-Fa-f]+);?".r
    val decoded = numeric.replaceAllIn(
      raw,
      found =>
        val radix = if found.group(1).isEmpty then 10 else 16
        try
          val code = Integer.parseInt(found.group(2), radix)
          if code > 0 && code <= 0x10ffff then
            java.util.regex.Matcher.quoteReplacement(String.valueOf(Character.toChars(code)))
          else java.util.regex.Matcher.quoteReplacement(found.matched)
        catch case _: Throwable => java.util.regex.Matcher.quoteReplacement(found.matched)
    )

    decoded.replace("&amp;", "&").replace("&colon;", ":").replace("&tab;", "\t")

  private def stripInsideScheme(value: String): String =
    val trimmed = value.dropWhile(isInvisible).reverse.dropWhile(isInvisible).reverse
    val colon   = trimmed.indexOf(':')

    if colon < 0 then trimmed.filterNot(isInvisible)
    else
      val head = trimmed.take(colon).filterNot(isInvisible)
      val tail = trimmed.drop(colon)
      if head.exists(character => character == '/' || character == '?' || character == '#') then
        trimmed
      else head.toLowerCase + tail

  def schemeOf(value: String): Option[String] =
    val colon = value.indexOf(':')
    if colon <= 0 then None
    else
      val head = value.take(colon)
      if head.head.isLetter &&
        head.forall(c => c.isLetterOrDigit || c == '+' || c == '-' || c == '.')
      then Some(head.toLowerCase)
      else None

  /** The host of an absolute URL, lower case. `None` when there is none to speak of. */
  def hostOf(value: String): Option[String] =
    val afterScheme = value.indexOf("://")
    if afterScheme < 0 then None
    else
      val rest      = value.drop(afterScheme + 3)
      val authority = rest.takeWhile(character => character != '/' && character != '?' && character != '#')
      val host      = authority.dropWhile(_ != '@').drop(1) match
        case ""    => authority
        case after => after
      Some(host.takeWhile(_ != ':').toLowerCase).filter(_.nonEmpty)
