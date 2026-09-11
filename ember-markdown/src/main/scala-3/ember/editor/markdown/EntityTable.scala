package ember.editor.markdown

/** Which named character references a profile resolves.
  *
  * ==Why this is a value and not a constant==
  *
  * Because it is a '''subset''', and a subset has to be nameable. CommonMark defers to the WHATWG
  * list -- 2231 names, most of which no editor document has ever contained. Shipping it would put
  * roughly 150 kB of table into every browser bundle that links this module, for `&angmsdaa;` and
  * friends.
  *
  * So the default table is [[EntityTable.common]]: the five XML names, the whole Latin-1 supplement
  * (which is what makes German, French and Scandinavian text survive a round trip), and the
  * punctuation and currency symbols that actually appear in prose. Roughly 150 entries.
  *
  * An application that needs more passes its own. That is the same shape as `LinkUrlPolicy` and
  * `MediaUrlPolicy`: the library picks a defensible default and the application overrides it
  * knowing that it did.
  *
  * ==Numeric references are complete==
  *
  * `&#35;` and `&#x1F600;` need no table, so there is no subset to apologise for. They are handled
  * in [[InlineParser]] and follow the specification exactly, U+0000 mapped to U+FFFD included.
  */
final case class EntityTable(private val names: Map[String, String]):

  /** The replacement for a name (without `&` and `;`), or `None`. */
  def lookup(name: String): Option[String] = names.get(name)

  def size: Int = names.size

  /** This table plus more. For an application that needs a name the default omits. */
  def and(more: (String, String)*): EntityTable = EntityTable(names ++ more)

object EntityTable:

  /** Only numeric references. The smallest correct table there is. */
  val numericOnly: EntityTable = EntityTable(Map.empty)

  /** The default: what prose actually contains.
    *
    * Grouped by why each group is here, because "which entities" is exactly the kind of list that
    * grows by accident when nobody can say what belongs in it.
    */
  val common: EntityTable = EntityTable(
    Map(
      // Die fuenf, ohne die HTML nicht funktioniert.
      "amp"  -> "&",
      "lt"   -> "<",
      "gt"   -> ">",
      "quot" -> "\"",
      "apos" -> "'",

      // Leerraum und Striche -- die haeufigsten typografischen Zeichen in Fliesstext.
      "nbsp"   -> " ",
      "ensp"   -> " ",
      "emsp"   -> " ",
      "thinsp" -> " ",
      "zwnj"   -> "‌",
      "zwj"    -> "‍",
      "ndash"  -> "–",
      "mdash"  -> "—",
      "shy"    -> "­",

      // Anfuehrungszeichen, in allen Sprachen, die dieses Projekt betreffen.
      "lsquo"  -> "‘",
      "rsquo"  -> "’",
      "sbquo"  -> "‚",
      "ldquo"  -> "“",
      "rdquo"  -> "”",
      "bdquo"  -> "„",
      "laquo"  -> "«",
      "raquo"  -> "»",
      "lsaquo" -> "‹",
      "rsaquo" -> "›",

      // Satzzeichen und Zeichen, die in Texten vorkommen.
      "hellip" -> "…",
      "dagger" -> "†",
      "Dagger" -> "‡",
      "bull"   -> "•",
      "middot" -> "·",
      "para"   -> "¶",
      "sect"   -> "§",
      "iexcl"  -> "¡",
      "iquest" -> "¿",
      "prime"  -> "′",
      "Prime"  -> "″",

      // Waehrung und Rechtliches.
      "cent"   -> "¢",
      "pound"  -> "£",
      "curren" -> "¤",
      "yen"    -> "¥",
      "euro"   -> "€",
      "copy"   -> "©",
      "reg"    -> "®",
      "trade"  -> "™",
      "deg"    -> "°",
      "permil" -> "‰",

      // Mathematik, soweit sie in Prosa auftaucht.
      "plusmn" -> "±",
      "times"  -> "×",
      "divide" -> "÷",
      "frac14" -> "¼",
      "frac12" -> "½",
      "frac34" -> "¾",
      "sup1"   -> "¹",
      "sup2"   -> "²",
      "sup3"   -> "³",
      "micro"  -> "µ",
      "ne"     -> "≠",
      "le"     -> "≤",
      "ge"     -> "≥",
      "minus"  -> "−",
      "infin"  -> "∞",

      // Pfeile.
      "larr" -> "←",
      "uarr" -> "↑",
      "rarr" -> "→",
      "darr" -> "↓",
      "harr" -> "↔",
      "lArr" -> "⇐",
      "rArr" -> "⇒",
      "hArr" -> "⇔"
    ) ++ latin1Letters
  )

  /** The Latin-1 letters, spelled out.
    *
    * This is the group that earns the table: without it `Gr&ouml;&szlig;e` survives a round trip as
    * `Gr&ouml;&szlig;e` and a German document quietly stops being German.
    */
  private def latin1Letters: Map[String, String] =
    val vowels = Vector(
      ("A", 'A'),
      ("E", 'E'),
      ("I", 'I'),
      ("O", 'O'),
      ("U", 'U'),
      ("Y", 'Y'),
      ("a", 'a'),
      ("e", 'e'),
      ("i", 'i'),
      ("o", 'o'),
      ("u", 'u'),
      ("y", 'y')
    )

    // Die Latin-1-Buchstaben liegen im Codeblock in fester Reihenfolge; sie einzeln
    // hinzuschreiben waere hundert Zeilen, in denen sich ein Tippfehler versteckte.
    val accents = Map(
      "grave" -> Map(
        'A' -> 'À',
        'E' -> 'È',
        'I' -> 'Ì',
        'O' -> 'Ò',
        'U' -> 'Ù',
        'a' -> 'à',
        'e' -> 'è',
        'i' -> 'ì',
        'o' -> 'ò',
        'u' -> 'ù'
      ),
      "acute" -> Map(
        'A' -> 'Á',
        'E' -> 'É',
        'I' -> 'Í',
        'O' -> 'Ó',
        'U' -> 'Ú',
        'Y' -> 'Ý',
        'a' -> 'á',
        'e' -> 'é',
        'i' -> 'í',
        'o' -> 'ó',
        'u' -> 'ú',
        'y' -> 'ý'
      ),
      "circ" -> Map(
        'A' -> 'Â',
        'E' -> 'Ê',
        'I' -> 'Î',
        'O' -> 'Ô',
        'U' -> 'Û',
        'a' -> 'â',
        'e' -> 'ê',
        'i' -> 'î',
        'o' -> 'ô',
        'u' -> 'û'
      ),
      "tilde" -> Map('A' -> 'Ã', 'O' -> 'Õ', 'a' -> 'ã', 'o' -> 'õ'),
      "uml"   -> Map(
        'A' -> 'Ä',
        'E' -> 'Ë',
        'I' -> 'Ï',
        'O' -> 'Ö',
        'U' -> 'Ü',
        'a' -> 'ä',
        'e' -> 'ë',
        'i' -> 'ï',
        'o' -> 'ö',
        'u' -> 'ü',
        'y' -> 'ÿ'
      )
    )

    val accented =
      for
        (suffix, table) <- accents.toVector
        (letter, value) <- table.toVector
      yield s"$letter$suffix" -> value.toString

    accented.toMap ++ Map(
      "Ccedil" -> "Ç",
      "ccedil" -> "ç",
      "Ntilde" -> "Ñ",
      "ntilde" -> "ñ",
      "AElig"  -> "Æ",
      "aelig"  -> "æ",
      "Oslash" -> "Ø",
      "oslash" -> "ø",
      "Aring"  -> "Å",
      "aring"  -> "å",
      "szlig"  -> "ß",
      "ETH"    -> "Ð",
      "eth"    -> "ð",
      "THORN"  -> "Þ",
      "thorn"  -> "þ",
      "OElig"  -> "Œ",
      "oelig"  -> "œ",
      "Scaron" -> "Š",
      "scaron" -> "š",
      "Yuml"   -> "Ÿ",
      "fnof"   -> "ƒ",
      "ordf"   -> "ª",
      "ordm"   -> "º"
    )
