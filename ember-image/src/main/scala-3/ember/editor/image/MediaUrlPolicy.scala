package ember.editor.image

import ember.editor.core.*

/** Which media sources a profile accepts.
  *
  * ==Why this is not the link policy==
  *
  * §20 and P14's risk line insist on the separation: "Links und Media haben unterschiedliche
  * Policies." The reason is concrete. A link is something the reader chooses to follow; an image is
  * something the page loads on its own, from a host the author named, into the reader's browser,
  * with the reader's IP address attached. So the defaults differ:
  *
  *   - '''`https` only.''' §20: "Die Default-Policy erlaubt sichere absolute HTTPS-Quellen sowie
  *     eindeutige relative/interne Pfade. HTTP kann die Anwendung explizit erlauben." A picture
  *     loaded over `http` into an `https` page is mixed content that browsers block anyway.
  *   - '''No `mailto:` or `tel:`.''' They are perfectly good links and not pictures at all.
  *   - '''An optional host allowlist.''' §20 names it as "separate, deterministische Konfiguration"
  *     -- an editor may accept links anywhere and images only from its own CDN.
  *
  * ==What no policy does==
  *
  * Fetch anything. §20: "keine externe URL wird vom Parser oder SSR-Server automatisch abgerufen."
  * Checking a source is a decision about a string; whether the picture exists is a question for the
  * browser that eventually renders it, and asking it here would turn every decode into a network
  * call.
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
    // Browsers interpret backslashes as slashes in HTTP(S) URLs, including relative
    // network paths. Reject them instead of checking a different authority than the browser.
    else if value.contains('\\') then Left(MediaError.AmbiguousUrl(at))
    else if value.startsWith("//") then Left(MediaError.ProtocolRelative(at))
    else
      UrlNormalisation.schemeOf(value) match
        case Some(scheme) if !schemes.contains(scheme) =>
          Left(MediaError.ForbiddenScheme(scheme, at))
        case Some(_)               => checkHost(value, at)
        case None if allowRelative => Right(MediaUrl.trusted(value))
        case None                  => Left(MediaError.ForbiddenScheme("(relativ)", at))

  private def checkHost(value: String, at: DiagnosticPath): Either[MediaError, MediaUrl] =
    hosts match
      case None          => Right(MediaUrl.trusted(value))
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
