package ember.editor.html

/** Which named character references the importer resolves.
  *
  * ==Why this is not shared with the Markdown module==
  *
  * `ember-markdown` has the same table for the same reason, and the duplication is deliberate. §7
  * puts the two format modules side by side: neither may import the other, so a shared table would
  * have to live in the core -- and §6 keeps the core free of format concerns ("er kennt keines der
  * Module, die auf ihm aufbauen -- auch nicht die Formatmodule").
  *
  * Duplicated data with a stated reason beats an architectural inversion. The two tables are also
  * not obliged to agree: CommonMark defers to the WHATWG list, and an HTML importer answers to what
  * clipboards actually produce.
  *
  * ==Why a subset==
  *
  * The WHATWG list has 2231 names, most of which no editor document has ever contained. Shipping it
  * would put roughly 150 kB of table into every bundle for `&angmsdaa;` and friends.
  *
  * [[HtmlEntities.common]] is the five XML names, the Latin-1 supplement -- which is what makes
  * German, French and Scandinavian text survive a paste -- and the punctuation Word and browsers
  * emit. Roughly 150 entries. An application that needs more passes its own, the same shape as
  * every other policy in this editor.
  *
  * ==Numeric references are complete==
  *
  * `&#160;` and `&#x1F600;` need no table. They follow the HTML specification, U+0000 mapped to
  * U+FFFD included, and so there is no subset to apologise for.
  */
final case class HtmlEntities(private val names: Map[String, String]):

  /** The replacement for a name, without `&` and `;`. */
  def lookup(name: String): Option[String] = names.get(name)

  def size: Int = names.size

  def and(more: (String, String)*): HtmlEntities = HtmlEntities(names ++ more)

object HtmlEntities:

  /** Only numeric references. */
  val numericOnly: HtmlEntities = HtmlEntities(Map.empty)

  private val xml = Map(
    "amp"  -> "&",
    "lt"   -> "<",
    "gt"   -> ">",
    "quot" -> "\"",
    "apos" -> "'"
  )

  /** The Latin-1 supplement, U+00A0 to U+00FF, by its HTML names. */
  private val latin1 = Map(
    "nbsp"   -> " ",
    "iexcl"  -> "¡",
    "cent"   -> "¢",
    "pound"  -> "£",
    "curren" -> "¤",
    "yen"    -> "¥",
    "brvbar" -> "¦",
    "sect"   -> "§",
    "uml"    -> "¨",
    "copy"   -> "©",
    "ordf"   -> "ª",
    "laquo"  -> "«",
    "not"    -> "¬",
    "shy"    -> "­",
    "reg"    -> "®",
    "macr"   -> "¯",
    "deg"    -> "°",
    "plusmn" -> "±",
    "sup2"   -> "²",
    "sup3"   -> "³",
    "acute"  -> "´",
    "micro"  -> "µ",
    "para"   -> "¶",
    "middot" -> "·",
    "cedil"  -> "¸",
    "sup1"   -> "¹",
    "ordm"   -> "º",
    "raquo"  -> "»",
    "frac14" -> "¼",
    "frac12" -> "½",
    "frac34" -> "¾",
    "iquest" -> "¿",
    "Agrave" -> "À",
    "Aacute" -> "Á",
    "Acirc"  -> "Â",
    "Atilde" -> "Ã",
    "Auml"   -> "Ä",
    "Aring"  -> "Å",
    "AElig"  -> "Æ",
    "Ccedil" -> "Ç",
    "Egrave" -> "È",
    "Eacute" -> "É",
    "Ecirc"  -> "Ê",
    "Euml"   -> "Ë",
    "Igrave" -> "Ì",
    "Iacute" -> "Í",
    "Icirc"  -> "Î",
    "Iuml"   -> "Ï",
    "ETH"    -> "Ð",
    "Ntilde" -> "Ñ",
    "Ograve" -> "Ò",
    "Oacute" -> "Ó",
    "Ocirc"  -> "Ô",
    "Otilde" -> "Õ",
    "Ouml"   -> "Ö",
    "times"  -> "×",
    "Oslash" -> "Ø",
    "Ugrave" -> "Ù",
    "Uacute" -> "Ú",
    "Ucirc"  -> "Û",
    "Uuml"   -> "Ü",
    "Yacute" -> "Ý",
    "THORN"  -> "Þ",
    "szlig"  -> "ß",
    "agrave" -> "à",
    "aacute" -> "á",
    "acirc"  -> "â",
    "atilde" -> "ã",
    "auml"   -> "ä",
    "aring"  -> "å",
    "aelig"  -> "æ",
    "ccedil" -> "ç",
    "egrave" -> "è",
    "eacute" -> "é",
    "ecirc"  -> "ê",
    "euml"   -> "ë",
    "igrave" -> "ì",
    "iacute" -> "í",
    "icirc"  -> "î",
    "iuml"   -> "ï",
    "eth"    -> "ð",
    "ntilde" -> "ñ",
    "ograve" -> "ò",
    "oacute" -> "ó",
    "ocirc"  -> "ô",
    "otilde" -> "õ",
    "ouml"   -> "ö",
    "divide" -> "÷",
    "oslash" -> "ø",
    "ugrave" -> "ù",
    "uacute" -> "ú",
    "ucirc"  -> "û",
    "uuml"   -> "ü",
    "yacute" -> "ý",
    "thorn"  -> "þ",
    "yuml"   -> "ÿ"
  )

  /** What a word processor and a browser put on the clipboard.
    *
    * Every one of these has been seen in pasted prose. The dashes and the quotation marks are the
    * ones that matter most -- a paste that turns an em dash into `&mdash;` is a paste nobody trusts
    * again.
    */
  private val punctuation = Map(
    "ensp"   -> " ",
    "emsp"   -> " ",
    "thinsp" -> " ",
    "zwnj"   -> "‌",
    "zwj"    -> "‍",
    "lrm"    -> "‎",
    "rlm"    -> "‏",
    "ndash"  -> "–",
    "mdash"  -> "—",
    "lsquo"  -> "‘",
    "rsquo"  -> "’",
    "sbquo"  -> "‚",
    "ldquo"  -> "“",
    "rdquo"  -> "”",
    "bdquo"  -> "„",
    "dagger" -> "†",
    "Dagger" -> "‡",
    "bull"   -> "•",
    "hellip" -> "…",
    "permil" -> "‰",
    "prime"  -> "′",
    "Prime"  -> "″",
    "lsaquo" -> "‹",
    "rsaquo" -> "›",
    "oline"  -> "‾",
    "frasl"  -> "⁄",
    "euro"   -> "€",
    "trade"  -> "™",
    "larr"   -> "←",
    "uarr"   -> "↑",
    "rarr"   -> "→",
    "darr"   -> "↓",
    "harr"   -> "↔",
    "minus"  -> "−",
    "lowast" -> "∗",
    "radic"  -> "√",
    "infin"  -> "∞",
    "ne"     -> "≠",
    "le"     -> "≤",
    "ge"     -> "≥",
    "loz"    -> "◊",
    "spades" -> "♠",
    "clubs"  -> "♣",
    "hearts" -> "♥",
    "diams"  -> "♦",
    "OElig"  -> "Œ",
    "oelig"  -> "œ",
    "Scaron" -> "Š",
    "scaron" -> "š",
    "Yuml"   -> "Ÿ",
    "fnof"   -> "ƒ",
    "circ"   -> "ˆ",
    "tilde"  -> "˜"
  )

  /** Greek letters. Word emits them for mathematics, and losing them loses the meaning. */
  private val greek = Map(
    "Alpha"   -> "Α",
    "Beta"    -> "Β",
    "Gamma"   -> "Γ",
    "Delta"   -> "Δ",
    "Epsilon" -> "Ε",
    "Zeta"    -> "Ζ",
    "Eta"     -> "Η",
    "Theta"   -> "Θ",
    "Iota"    -> "Ι",
    "Kappa"   -> "Κ",
    "Lambda"  -> "Λ",
    "Mu"      -> "Μ",
    "Nu"      -> "Ν",
    "Xi"      -> "Ξ",
    "Omicron" -> "Ο",
    "Pi"      -> "Π",
    "Rho"     -> "Ρ",
    "Sigma"   -> "Σ",
    "Tau"     -> "Τ",
    "Upsilon" -> "Υ",
    "Phi"     -> "Φ",
    "Chi"     -> "Χ",
    "Psi"     -> "Ψ",
    "Omega"   -> "Ω",
    "alpha"   -> "α",
    "beta"    -> "β",
    "gamma"   -> "γ",
    "delta"   -> "δ",
    "epsilon" -> "ε",
    "zeta"    -> "ζ",
    "eta"     -> "η",
    "theta"   -> "θ",
    "iota"    -> "ι",
    "kappa"   -> "κ",
    "lambda"  -> "λ",
    "mu"      -> "μ",
    "nu"      -> "ν",
    "xi"      -> "ξ",
    "omicron" -> "ο",
    "pi"      -> "π",
    "rho"     -> "ρ",
    "sigmaf"  -> "ς",
    "sigma"   -> "σ",
    "tau"     -> "τ",
    "upsilon" -> "υ",
    "phi"     -> "φ",
    "chi"     -> "χ",
    "psi"     -> "ψ",
    "omega"   -> "ω"
  )

  /** The default: XML, Latin-1, punctuation and Greek. */
  val common: HtmlEntities = HtmlEntities(xml ++ latin1 ++ punctuation ++ greek)

  /** Resolves a numeric reference, decimal or hexadecimal.
    *
    * Follows the HTML specification's replacement rules rather than rejecting: a null character and
    * a lone surrogate become U+FFFD, and an out-of-range code point does too. Pasted HTML is full
    * of these, and a paste that fails on one is a paste that fails.
    */
  def numeric(digits: String, hexadecimal: Boolean): Option[String] =
    val radix = if hexadecimal then 16 else 10
    try
      val code = java.lang.Integer.parseInt(digits, radix)
      if code == 0 || code > 0x10ffff || (code >= 0xd800 && code <= 0xdfff) then Some("�")
      else Some(new String(Character.toChars(code)))
    catch case _: NumberFormatException => None

  /** Decodes every reference in a string.
    *
    * An `&` that starts nothing recognisable stays an `&`. That is what browsers do, and pasted
    * HTML is full of bare ampersands -- a decoder that failed on them would fail on most real
    * fragments.
    */
  def decode(value: String, table: HtmlEntities): String =
    if !value.contains('&') then value
    else
      val out   = new StringBuilder(value.length)
      var index = 0

      while index < value.length do
        val char = value.charAt(index)
        if char != '&' then
          out.append(char)
          index += 1
        else
          val semicolon = value.indexOf(';', index + 1)
          // A reference is short. Scanning to a semicolon far away would swallow a paragraph
          // between two bare ampersands.
          if semicolon < 0 || semicolon - index > MaxReferenceLength then
            out.append(char)
            index += 1
          else
            val body = value.substring(index + 1, semicolon)
            resolve(body, table) match
              case Some(replacement) =>
                out.append(replacement)
                index = semicolon + 1
              case None =>
                out.append(char)
                index += 1

      out.result()

  private def resolve(body: String, table: HtmlEntities): Option[String] =
    if body.isEmpty then None
    else if body.charAt(0) != '#' then table.lookup(body)
    else if body.length > 1 && (body.charAt(1) == 'x' || body.charAt(1) == 'X') then
      numeric(body.substring(2), hexadecimal = true)
    else numeric(body.substring(1), hexadecimal = false)

  /** Long enough for `&angmsdaa;`, short enough not to span a sentence. */
  private val MaxReferenceLength = 32
