package ember.editor.markdown

import scala.collection.mutable
import scala.util.matching.Regex

/** The inline parser: emphasis, links, code spans, entities and escapes.
  *
  * ==Provenance==
  *
  * A port of `lib/inlines.js` from commonmark.js 0.31.2 (BSD-2-Clause, Copyright (c) 2014 John
  * MacFarlane -- see `ember-markdown/NOTICE`), on the same terms as [[BlockParser]]: the rule
  * structure is taken, the code is not.
  *
  * The rules worth naming, because they are the ones nobody derives correctly from scratch:
  *
  *   - '''Flanking.''' Whether a run of `*` or `_` can open or close depends on the characters on
  *     both sides being whitespace, punctuation or neither. `_` is stricter than `*`, which is what
  *     stops `snake_case_words` from becoming emphasis.
  *   - '''The rule of three.''' When a closer can also open (or an opener can also close), a pair
  *     whose lengths sum to a multiple of three is refused. Without it `*foo**bar**baz*` nests
  *     wrongly.
  *   - '''`openersBottom`.''' Fourteen separate lower bounds, indexed by delimiter character, by
  *     whether the closer can also open, and by the opener length modulo three. This is what keeps
  *     a pathological run of delimiters linear instead of quadratic -- P18's risk line names
  *     "langsame Delimiterfaelle" and this is the answer to it.
  *   - '''Links deactivate outer link openers.''' A link may not contain a link, and the check
  *     happens when the inner one closes, not when the outer one opens.
  *
  * ==What this port does differently==
  *
  *   - '''Offsets.''' Every inline node carries a [[SourceSpan]], which the reference does not
  *     track at all below the block level. §18.2 asks for source maps, and P19b's source view needs
  *     them down to the delimiter.
  *   - '''No smart punctuation.''' The reference has an option that turns quotes into curly quotes
  *     and `--` into dashes. It is off here and there is no switch: §18.2 asks for a round trip
  *     that preserves semantics, and rewriting the author's punctuation is a change of content, not
  *     of formatting.
  *   - '''A named entity subset.''' See [[EntityTable]].
  *   - '''A step budget.''' Shared with the block parser through [[ParseLimits]].
  */
private[markdown] final class InlineParser(
    profile: MarkdownProfile,
    references: ReferenceMap,
    nextId: () => SyntaxId,
    spend: Int => Boolean
):

  import InlineParser.*

  private var subject = ""
  private var pos     = 0

  /** Absolute offset in the whole source of `subject.charAt(0)`. */
  private var base = 0

  private var delimiters: Delimiter | Null = null
  private var brackets: Bracket | Null     = null

  private val slots = mutable.ArrayBuffer.empty[InlineSlot]

  /** Parses one block's inline content.
    *
    * `content` is the block's accumulated text; `offsetOf` maps a position inside it back to an
    * absolute source offset. The two differ because block parsing strips markers -- the `> ` of a
    * quote, the indentation of a list item -- so the content is not a slice of the source.
    */
  def parse(content: String, offsetOf: Int => Int): Vector[MarkdownInline] =
    subject = content
    pos = 0
    delimiters = null
    brackets = null
    slots.clear()

    var running = true
    while running do
      if !spend(1) then running = false
      else if !parseInline() then running = false

    processEmphasis(null)
    build(slots.toVector, offsetOf)

  // -----------------------------------------------------------------------------------------
  // Aufbau des Ergebnisses
  // -----------------------------------------------------------------------------------------

  private def build(from: Vector[InlineSlot], offsetOf: Int => Int): Vector[MarkdownInline] =
    from.filterNot(_.removed).flatMap { slot =>
      val span     = SourceSpan(offsetOf(slot.start), offsetOf(slot.end))
      val children = build(slot.children.toVector, offsetOf)

      slot.kind match
        case SlotKind.Text =>
          val literal = Option(slot.text).map(_.literal).getOrElse("")
          if literal.isEmpty then Vector.empty
          else Vector(MarkdownInline.Text(nextId(), span, literal))
        case SlotKind.Code(literal) => Vector(MarkdownInline.Code(nextId(), span, literal))
        case SlotKind.SoftBreak     => Vector(MarkdownInline.SoftBreak(nextId(), span))
        case SlotKind.HardBreak     => Vector(MarkdownInline.HardBreak(nextId(), span))
        case SlotKind.Html(literal) => Vector(MarkdownInline.HtmlInline(nextId(), span, literal))
        case SlotKind.Emphasis      => Vector(MarkdownInline.Emphasis(nextId(), span, children))
        case SlotKind.Strong        => Vector(MarkdownInline.Strong(nextId(), span, children))
        case SlotKind.Link(destination, title) =>
          Vector(MarkdownInline.Link(nextId(), span, destination, title, children))
        case SlotKind.Image(destination, title) =>
          Vector(MarkdownInline.Image(nextId(), span, destination, title, children))
    }

  private def append(slot: InlineSlot): InlineSlot =
    slots += slot
    slot

  private def appendText(literal: String, start: Int, end: Int): InlineSlot =
    val slot = new InlineSlot(SlotKind.Text, start, end)
    slot.text = new PendingText(literal, start, end)
    append(slot)

  // -----------------------------------------------------------------------------------------
  // Position
  // -----------------------------------------------------------------------------------------

  private def peek(): Int =
    if pos < subject.length then subject.charAt(pos).toInt else -1

  private def matchAt(regex: Regex): Option[String] =
    regex.findPrefixOf(subject.substring(math.min(pos, subject.length))) match
      case Some(found) =>
        pos += found.length
        Some(found)
      case None => None

  private def spnl(): Boolean =
    matchAt(SpaceNewline): Unit
    true

  // -----------------------------------------------------------------------------------------
  // Ein Inline
  // -----------------------------------------------------------------------------------------

  private def parseInline(): Boolean =
    val c = peek()
    if c == -1 then false
    else
      val startPos = pos
      val handled  = c.toChar match
        case '\n'      => parseNewline()
        case '\\'      => parseBackslash()
        case '`'       => parseBackticks()
        case '*' | '_' => handleDelimiter(c.toChar)
        case '['       => parseOpenBracket()
        case '!'       => parseBang()
        case ']'       => parseCloseBracket()
        case '<'       => parseAutolink() || parseHtmlTag()
        case '&'       => parseEntity()
        case _         => parseString()

      if !handled then
        pos += 1
        appendText(subject.substring(startPos, pos), startPos, pos): Unit
      true

  /** A run of ordinary characters. The pre-filter that keeps the common case cheap. */
  private def parseString(): Boolean =
    val start = pos
    matchAt(Main) match
      case Some(found) =>
        appendText(found, start, pos): Unit
        true
      case None => false

  /** A newline. Two trailing spaces before it make it a hard break (§18.2). */
  private def parseNewline(): Boolean =
    val start = pos
    pos += 1

    val last = slots.lastOption.filter(slot => slot.kind == SlotKind.Text && !slot.removed)
    val hard = last.flatMap(slot => Option(slot.text)) match
      case Some(text) if text.literal.endsWith(" ") =>
        val isHard = text.literal.endsWith("  ")
        text.literal = FinalSpaces.replaceFirstIn(text.literal, "")
        if text.literal.isEmpty then last.foreach(_.removed = true)
        isHard
      case _ => false

    append(
      new InlineSlot(if hard then SlotKind.HardBreak else SlotKind.SoftBreak, start, pos)
    ): Unit
    // Fuehrenden Leerraum der naechsten Zeile schlucken.
    matchAt(InitialSpaces): Unit
    true

  /** A backslash: an escape, a hard break before a newline, or a literal backslash. */
  private def parseBackslash(): Boolean =
    val start = pos
    pos += 1
    if peek() == '\n'.toInt then
      pos += 1
      append(new InlineSlot(SlotKind.HardBreak, start, pos)): Unit
    else if pos < subject.length && Escapable.contains(subject.charAt(pos)) then
      val escaped = subject.charAt(pos)
      pos += 1
      appendText(escaped.toString, start, pos): Unit
    else appendText("\\", start, pos): Unit
    true

  /** A code span. The closing run must be exactly as long as the opening one (§18.2). */
  private def parseBackticks(): Boolean =
    val start = pos
    matchAt(TicksHere) match
      case None        => false
      case Some(ticks) =>
        val afterOpen = pos
        var found     = false
        var scan      = pos

        // Von Hand und nicht per Regex: gesucht ist ein Backtick-Lauf '''derselben Laenge'''.
        // Ein Muster wie `[^`]*`+` liefert Text und Lauf in einem Stueck, und der Vergleich mit
        // dem Oeffnungslauf kann dann nie stimmen -- `foo` bliebe Text.
        while !found && scan < subject.length && spend(1) do
          if subject.charAt(scan) != '`' then scan += 1
          else
            var runEnd = scan
            while runEnd < subject.length && subject.charAt(runEnd) == '`' do runEnd += 1

            if runEnd - scan == ticks.length then
              val raw = subject.substring(afterOpen, scan).replace('\n', ' ')
              // Ein Leerzeichen an beiden Enden gehoert zur Schreibweise, nicht zum Inhalt --
              // aber nur, wenn nicht alles Leerzeichen ist.
              val literal =
                if raw.length > 1 && raw.startsWith(" ") && raw.endsWith(" ") &&
                  raw.exists(_ != ' ')
                then raw.substring(1, raw.length - 1)
                else raw
              pos = runEnd
              append(new InlineSlot(SlotKind.Code(literal), start, pos)): Unit
              found = true
            else scan = runEnd

        if found then true
        else
          // Kein passender Schlusslauf: die Backticks sind Text.
          pos = afterOpen
          appendText(ticks, start, pos): Unit
          true

  /** An autolink: `<https://…>` or `<someone@example.com>`. */
  private def parseAutolink(): Boolean =
    val start = pos
    matchAt(EmailAutolink) match
      case Some(found) =>
        val address = found.substring(1, found.length - 1)
        autolink(s"mailto:$address", address, start)
        true
      case None =>
        matchAt(Autolink) match
          case Some(found) =>
            val target = found.substring(1, found.length - 1)
            autolink(target, target, start)
            true
          case None => false

  private def autolink(destination: String, label: String, start: Int): Unit =
    val link = new InlineSlot(SlotKind.Link(normaliseUri(destination), None), start, pos)
    val text = new InlineSlot(SlotKind.Text, start + 1, pos - 1)
    text.text = new PendingText(label, start + 1, pos - 1)
    link.children += text
    append(link): Unit

  /** A raw inline tag, kept as literal source (§18.1). */
  private def parseHtmlTag(): Boolean =
    val start = pos
    matchAt(HtmlTag) match
      case Some(found) =>
        append(new InlineSlot(SlotKind.Html(found), start, pos)): Unit
        true
      case None => false

  /** A character reference. Numeric ones are complete; named ones come from the profile. */
  private def parseEntity(): Boolean =
    val start = pos
    matchAt(EntityHere) match
      case None        => false
      case Some(found) =>
        decodeEntity(found, profile.entities) match
          case Some(value) =>
            appendText(value, start, pos): Unit
            true
          case None =>
            // Ein Name, den diese Tabelle nicht kennt, bleibt Text -- unveraendert, damit ein
            // Round-Trip ihn nicht verliert (siehe EntityTable).
            pos = start
            false

  // -----------------------------------------------------------------------------------------
  // Emphasis
  // -----------------------------------------------------------------------------------------

  /** Whether a run of delimiters can open, close, both or neither.
    *
    * The flanking rules, verbatim from the specification. Their whole job is `_` inside a word:
    * `snake_case` must stay a word, `*bold*` must not.
    */
  private def scanDelimiters(char: Char): Option[(Int, Boolean, Boolean)] =
    val startPos = pos
    var count    = 0
    while peek() == char.toInt do
      count += 1
      pos += 1

    if count == 0 then None
    else
      val before = if startPos == 0 then '\n' else subject.charAt(startPos - 1)
      val after  = if pos < subject.length then subject.charAt(pos) else '\n'

      val afterIsWhitespace   = isUnicodeWhitespace(after)
      val afterIsPunctuation  = isPunctuation(after)
      val beforeIsWhitespace  = isUnicodeWhitespace(before)
      val beforeIsPunctuation = isPunctuation(before)

      val leftFlanking =
        !afterIsWhitespace && (!afterIsPunctuation || beforeIsWhitespace || beforeIsPunctuation)
      val rightFlanking =
        !beforeIsWhitespace && (!beforeIsPunctuation || afterIsWhitespace || afterIsPunctuation)

      val (canOpen, canClose) =
        if char == '_' then
          (
            leftFlanking && (!rightFlanking || beforeIsPunctuation),
            rightFlanking && (!leftFlanking || afterIsPunctuation)
          )
        else (leftFlanking, rightFlanking)

      pos = startPos
      Some((count, canOpen, canClose))

  private def handleDelimiter(char: Char): Boolean =
    scanDelimiters(char) match
      case None                             => false
      case Some((count, canOpen, canClose)) =>
        val startPos = pos
        pos += count
        val slot = appendText(subject.substring(startPos, pos), startPos, pos)

        if canOpen || canClose then
          val entry = new Delimiter(
            char = char,
            count = count,
            originalCount = count,
            node = slot.text.nn,
            canOpen = canOpen,
            canClose = canClose,
            previous = delimiters,
            next = null
          )
          delimiters match
            case null                => ()
            case previous: Delimiter => previous.next = entry
          delimiters = entry

        true

  private def removeDelimiter(entry: Delimiter): Unit =
    entry.previous match
      case null              => ()
      case before: Delimiter => before.next = entry.next
    entry.next match
      case null             => delimiters = entry.previous
      case after: Delimiter => after.previous = entry.previous

  /** Pairs openers with closers and wraps what lies between them.
    *
    * `openersBottom` is the part that is easy to leave out and expensive to leave out: without it,
    * a run like `*a *b *c *d …` re-scans the whole stack for every closer. With it, each of the
    * fourteen classes remembers how far back it is worth looking, and the scan stays linear. P18's
    * risk line calls this "langsame Delimiterfaelle".
    */
  private def processEmphasis(stackBottom: Delimiter | Null): Unit =
    val openersBottom = Array.fill[Delimiter | Null](14)(stackBottom)

    var closer: Delimiter | Null = delimiters
    while closer != null && closer.asInstanceOf[Delimiter].previous != stackBottom do
      closer = closer.asInstanceOf[Delimiter].previous

    var running = true
    while running && closer != null do
      val current = closer.asInstanceOf[Delimiter]
      if !spend(1) then running = false
      else if !current.canClose then closer = current.next
      else
        val index =
          (if current.char == '_' then 2 else 8) +
            (if current.canOpen then 3 else 0) + (current.originalCount % 3)

        var opener: Delimiter | Null = current.previous
        var openerFound              = false

        while !openerFound && opener != null && opener != stackBottom &&
          opener != openersBottom(index)
        do
          val candidate = opener.asInstanceOf[Delimiter]
          // Die Dreierregel: ein Paar, dessen Laengen zusammen durch drei teilbar sind, passt
          // nicht -- wenn eine der beiden Seiten auch die andere Rolle spielen koennte.
          val oddMatch =
            (current.canOpen || candidate.canClose) &&
              current.originalCount                             % 3 != 0 &&
              (candidate.originalCount + current.originalCount) % 3 == 0

          if candidate.char == current.char && candidate.canOpen && !oddMatch then
            openerFound = true
          else opener = candidate.previous

        val oldCloser = current

        if !openerFound then closer = current.next
        else
          val open = opener.asInstanceOf[Delimiter]
          val used = if current.count >= 2 && open.count >= 2 then 2 else 1

          open.count -= used
          current.count -= used
          open.node.literal = open.node.literal.dropRight(used)
          current.node.literal = current.node.literal.dropRight(used)

          wrap(open, current, if used == 1 then SlotKind.Emphasis else SlotKind.Strong)

          // Alles zwischen Oeffner und Schliesser ist verbraucht.
          if open.next != current then
            open.next = current
            current.previous = open

          if open.count == 0 then
            markRemoved(open.node)
            removeDelimiter(open)

          if current.count == 0 then
            markRemoved(current.node)
            val after = current.next
            removeDelimiter(current)
            closer = after

        if !openerFound then
          openersBottom(index) = oldCloser.previous
          if !oldCloser.canOpen then removeDelimiter(oldCloser)

    while delimiters != null && delimiters != stackBottom do
      removeDelimiter(delimiters.asInstanceOf[Delimiter])

  /** Finds the buffer a slot lives in, and its index there.
    *
    * ==Why this has to search recursively==
    *
    * Because a delimiter can outlive the move that put its text inside a link. When a `]` closes,
    * the reference moves everything after the opener '''into''' the new link node and only then
    * runs `processEmphasis` over the delimiters that were pushed since -- their text nodes are now
    * children of the link. A linked list does not care: `unlink` and `insertAfter` work wherever a
    * node sits.
    *
    * A flat buffer does care, and getting this wrong is not a crash. It is `[*a*](/url)` silently
    * losing its emphasis, which is exactly the shape of bug a conformance count finds and a
    * hand-written test does not.
    */
  private def locate(text: PendingText): Option[(mutable.ArrayBuffer[InlineSlot], Int)] =
    def search(
        buffer: mutable.ArrayBuffer[InlineSlot]
    ): Option[(mutable.ArrayBuffer[InlineSlot], Int)] =
      val index = buffer.indexWhere(slot => (slot.text eq text) && !slot.removed)
      if index >= 0 then Some((buffer, index))
      else buffer.iterator.map(slot => search(slot.children)).collectFirst { case Some(hit) => hit }

    search(slots)

  private def markRemoved(text: PendingText): Unit =
    locate(text).foreach((buffer, index) => buffer(index).removed = true)

  /** Moves the slots between two delimiters into a new one. */
  private def wrap(open: Delimiter, close: Delimiter, kind: SlotKind): Unit =
    (locate(open.node), locate(close.node)) match
      case (Some((buffer, from)), Some((closeBuffer, to)))
          if (buffer eq closeBuffer) && to > from =>
        val moved = buffer.slice(from + 1, to).filterNot(_.removed).toVector
        val start = open.node.end - open.count
        val end   = close.node.start + close.count

        val wrapper = new InlineSlot(kind, start, end)
        wrapper.children ++= moved

        // Aus dem Puffer '''entfernen''', nicht markieren. `removed` heisst "gehoert nicht mehr
        // hierher", und dieselben Objekte stehen jetzt in `wrapper.children` -- eine Markierung
        // wuerde sie dort ebenso ausblenden, und `*foo*` ergaebe ein leeres `<em>`.
        buffer.remove(from + 1, to - from - 1)
        buffer.insert(from + 1, wrapper)

      case _ => ()

  // -----------------------------------------------------------------------------------------
  // Links und Bilder
  // -----------------------------------------------------------------------------------------

  private def parseOpenBracket(): Boolean =
    val start = pos
    pos += 1
    val slot = appendText("[", start, pos)
    addBracket(slot.text.nn, pos, isImage = false)
    true

  private def parseBang(): Boolean =
    val start = pos
    pos += 1
    if peek() == '['.toInt then
      pos += 1
      val slot = appendText("![", start, pos)
      addBracket(slot.text.nn, pos, isImage = true)
    else appendText("!", start, pos): Unit
    true

  private def addBracket(node: PendingText, index: Int, isImage: Boolean): Unit =
    brackets match
      case null          => ()
      case open: Bracket => open.bracketAfter = true
    brackets =
      new Bracket(node, brackets, delimiters, index, isImage, active = true, bracketAfter = false)

  private def removeBracket(): Unit =
    brackets = brackets.asInstanceOf[Bracket].previous

  /** The `]` that decides whether everything before it was a link. */
  private def parseCloseBracket(): Boolean =
    val bracketStart = pos
    pos += 1
    val startPos = pos

    brackets match
      case null =>
        appendText("]", bracketStart, pos): Unit
        true
      case opener: Bracket if !opener.active =>
        appendText("]", bracketStart, pos): Unit
        removeBracket()
        true
      case opener: Bracket =>
        val savedPos                      = pos
        var target: Option[LinkReference] = None

        // Inline: `](/url "Titel")`
        if peek() == '('.toInt then
          pos += 1
          spnl(): Unit
          parseLinkDestination() match
            case Some(destination) =>
              spnl(): Unit
              val beforeTitle = pos
              val title       =
                if beforeTitle > 0 && isWhitespaceChar(subject.charAt(beforeTitle - 1)) then
                  parseLinkTitle()
                else None
              spnl(): Unit
              if peek() == ')'.toInt then
                pos += 1
                target = Some(LinkReference(destination, title))
              else pos = savedPos
            case None => pos = savedPos

        // Referenz: `][label]`, `][]` oder `[label]`
        if target.isEmpty then
          val beforeLabel = pos
          val consumed    = parseLinkLabel()
          val label       =
            if consumed > 2 then Some(subject.substring(beforeLabel, beforeLabel + consumed))
            else if !opener.bracketAfter then
              Some(subject.substring(opener.index - 1, bracketStart + 1))
            else None
          if consumed == 0 then pos = savedPos
          target = label.flatMap(references.lookup)

        target match
          case Some(LinkReference(destination, title)) =>
            val kind =
              if opener.isImage then SlotKind.Image(destination, title)
              else SlotKind.Link(destination, title)

            val wrapper = new InlineSlot(kind, opener.node.start, pos)

            locate(opener.node).foreach { (buffer, from) =>
              wrapper.children ++= buffer.drop(from + 1).filterNot(_.removed).toVector
              // Der Oeffner selbst geht mit: das `[` war nie Inhalt.
              buffer.remove(from, buffer.length - from)
              buffer += wrapper
            }

            processEmphasis(opener.previousDelimiter)
            removeBracket()

            // Ein Link darf keinen Link enthalten -- und das entscheidet sich, wenn der innere
            // schliesst, nicht wenn der aeussere oeffnet.
            if !opener.isImage then
              var outer = brackets
              while outer != null do
                val entry = outer.asInstanceOf[Bracket]
                if !entry.isImage then entry.active = false
                outer = entry.previous

            true

          case None =>
            removeBracket()
            pos = startPos
            appendText("]", bracketStart, pos): Unit
            true

  private def parseLinkTitle(): Option[String] =
    matchAt(LinkTitle)
      .map(title => unescapeString(title.substring(1, title.length - 1), profile.entities))

  /** A link destination: `<in pointy brackets>` or a run with balanced parentheses. */
  private def parseLinkDestination(): Option[String] =
    matchAt(LinkDestinationBraces) match
      case Some(found) =>
        Some(normaliseUri(unescapeString(found.substring(1, found.length - 1), profile.entities)))
      case None if peek() == '<'.toInt => None
      case None                        =>
        val savedPos   = pos
        var openParens = 0
        var running    = true
        var last       = -1

        while running do
          val c = peek()
          last = c
          if c == -1 then running = false
          else if c == '\\'.toInt && pos + 1 < subject.length &&
            Escapable.contains(subject.charAt(pos + 1))
          then pos += 2
          else if c == '('.toInt then
            pos += 1
            openParens += 1
          else if c == ')'.toInt then
            if openParens < 1 then running = false
            else
              pos += 1
              openParens -= 1
          else if isWhitespaceChar(c.toChar) || c < 0x20 then running = false
          else pos += 1

        if pos == savedPos && last != ')'.toInt then None
        else if openParens != 0 then None
        else Some(normaliseUri(unescapeString(subject.substring(savedPos, pos), profile.entities)))

  /** How many characters a `[…]` label takes, or zero. */
  private def parseLinkLabel(): Int =
    matchAt(LinkLabel) match
      case Some(found) if found.length <= 1001 => found.length
      case Some(found)                         =>
        pos -= found.length
        0
      case None => 0

  // -----------------------------------------------------------------------------------------
  // Link-Referenzdefinitionen
  // -----------------------------------------------------------------------------------------

  /** Reads leading link reference definitions off a paragraph's content.
    *
    * Returns how many characters were consumed. The block parser calls this when it finalises a
    * paragraph: a definition is not a block, it is a prefix of one, and what remains is the
    * paragraph. If nothing remains, the paragraph disappears.
    */
  def parseReferences(content: String): Int =
    subject = content
    pos = 0

    var consumed = 0
    var running  = true
    while running do
      if pos >= subject.length || subject.charAt(pos) != '[' then running = false
      else
        parseOneReference() match
          case 0 => running = false
          case _ =>
            // `pos` und nicht die Laenge dieser einen Definition: mehrere stehen hintereinander,
            // und die Rueckgabe muss alle zusammen meinen. Sonst bleibt die zweite als Absatz
            // stehen -- sichtbar als ein `<p><a href="first">foo</a>: second</p>` am Dokumentende.
            consumed = pos
    consumed

  private def parseOneReference(): Int =
    val startPos = pos

    val labelLength = parseLinkLabel()
    if labelLength == 0 then return 0
    val rawLabel = subject.substring(startPos, startPos + labelLength)

    if peek() != ':'.toInt then
      pos = startPos
      return 0
    pos += 1

    spnl(): Unit
    val destination = parseLinkDestination() match
      case Some(found) => found
      case None        =>
        pos = startPos
        return 0

    val beforeTitle = pos
    spnl(): Unit
    var title = if pos != beforeTitle then parseLinkTitle() else None
    if title.isEmpty then pos = beforeTitle

    var atLineEnd = matchAt(SpaceAtEndOfLine).isDefined
    if !atLineEnd then
      if title.isEmpty then
        pos = startPos
        return 0
      else
        // Der Titel war keiner -- aber ohne ihn koennte die Zeile trotzdem eine Definition sein.
        title = None
        pos = beforeTitle
        atLineEnd = matchAt(SpaceAtEndOfLine).isDefined

    if !atLineEnd then
      pos = startPos
      return 0

    if ReferenceMap.normalise(rawLabel).isEmpty then
      pos = startPos
      return 0

    references.define(rawLabel, LinkReference(destination, title))
    pos - startPos

private[markdown] object InlineParser:

  // Die Muster der Vorlage. Wie im Blockparser unveraendert uebernommen und einzeln geprueft.
  val Main: Regex             = raw"""[^\n`\[\]\\!<&*_'"]+""".r
  val TicksHere: Regex        = raw"""`+""".r
  val EntityHere: Regex       = raw"""(?i)&(?:#x[a-f0-9]{1,6}|#[0-9]{1,7}|[a-z][a-z0-9]{1,31});""".r
  val FinalSpaces: Regex      = raw""" *$$""".r
  val InitialSpaces: Regex    = raw"""^ *""".r
  val SpaceNewline: Regex     = raw""" *(?:\n *)?""".r
  val SpaceAtEndOfLine: Regex = raw""" *(?:\n|$$)""".r

  val LinkTitle: Regex =
    (raw"""(?:"(?:\\.|[^"\x00])*"|'(?:\\.|[^'\x00])*'|\((?:\\.|[^()\x00])*\))""").r

  val LinkDestinationBraces: Regex = raw"""(?:<(?:[^<>\n\\\x00]|\\.)*>)""".r

  /** At most 1000 characters between the brackets, per the specification. `(?s)` because a label
    * may span lines.
    */
  val LinkLabel: Regex = raw"""(?s)\[(?:[^\\\[\]]|\\.){0,1000}\]""".r

  val EmailAutolink: Regex =
    (raw"""<([a-zA-Z0-9.!#$$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?""" +
      raw"""(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*)>""").r

  val Autolink: Regex = raw"""(?i)<[A-Za-z][A-Za-z0-9.+-]{1,31}:[^<>\x00-\x20]*>""".r

  private val TagName        = raw"""[A-Za-z][A-Za-z0-9-]*"""
  private val AttributeName  = raw"""[a-zA-Z_:][a-zA-Z0-9:._-]*"""
  private val AttributeValue =
    raw"""(?:[^"'=<>`\x00-\x20]+|'[^']*'|"[^"]*")"""
  private val Attribute =
    raw"""(?:\s+$AttributeName(?:\s*=\s*$AttributeValue)?)"""

  val HtmlTag: Regex =
    (raw"""(?:<$TagName$Attribute*\s*/?>""" +
      raw"""|</$TagName\s*>""" +
      raw"""|<!-->|<!--->|<!--[\s\S]*?-->""" +
      raw"""|<[?][\s\S]*?[?]>""" +
      raw"""|<![A-Za-z][^>]*>""" +
      raw"""|<!\[CDATA\[[\s\S]*?\]\]>)""").r

  val Escapable: Set[Char] = """!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~""".toSet

  def isWhitespaceChar(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '\n' || c == '\u000b' || c == '\f' || c == '\r'

  /** `\s` as the reference uses it -- Unicode whitespace, not just ASCII. */
  def isUnicodeWhitespace(c: Char): Boolean =
    isWhitespaceChar(c) || c == '\u00a0' || c == '\u1680' ||
      (c >= '\u2000' && c <= '\u200a') || c == '\u2028' || c == '\u2029' ||
      c == '\u202f' || c == '\u205f' || c == '\u3000' || c == '\ufeff'

  /** Unicode punctuation, as the flanking rules need it.
    *
    * ASCII punctuation plus the general categories the specification names. `Character.getType`
    * gives them directly, which is shorter and more correct than the reference's regular expression
    * -- that one enumerates ranges by hand because JavaScript had no property escapes when it was
    * written.
    */
  def isPunctuation(c: Char): Boolean =
    if c < 128 then !c.isLetterOrDigit && !c.isWhitespace && c > ' '
    else
      Character.getType(c) match
        case Character.CONNECTOR_PUNCTUATION | Character.DASH_PUNCTUATION |
            Character.START_PUNCTUATION | Character.END_PUNCTUATION |
            Character.INITIAL_QUOTE_PUNCTUATION | Character.FINAL_QUOTE_PUNCTUATION |
            Character.OTHER_PUNCTUATION | Character.MATH_SYMBOL | Character.CURRENCY_SYMBOL |
            Character.MODIFIER_SYMBOL | Character.OTHER_SYMBOL =>
          true
        case _ => false

  /** Backslash escapes '''and''' character references.
    *
    * Both, because CommonMark resolves both in a link destination, a link title and a fenced
    * block's info string -- those are not inline content and never reach the inline parser's own
    * entity handling. `[link](foo%20b&auml;)` has to arrive as `foo%20b%C3%A4`, and it only does if
    * the entity is decoded before the URI is normalised.
    */
  def unescapeString(text: String, entities: EntityTable): String =
    if text.indexOf('\\') < 0 && text.indexOf('&') < 0 then text
    else
      val out = new StringBuilder(text.length)
      var i   = 0
      while i < text.length do
        val c = text.charAt(i)
        if c == '\\' && i + 1 < text.length && Escapable.contains(text.charAt(i + 1)) then
          out.append(text.charAt(i + 1))
          i += 2
        else if c == '&' then
          EntityHere.findPrefixOf(text.substring(i)) match
            case Some(found) =>
              decodeEntity(found, entities) match
                case Some(value) =>
                  out.append(value)
                  i += found.length
                case None =>
                  out.append(c)
                  i += 1
            case None =>
              out.append(c)
              i += 1
        else
          out.append(c)
          i += 1
      out.toString

  /** Decodes one `&…;`, numeric or named. `None` when the table does not know the name. */
  def decodeEntity(reference: String, entities: EntityTable): Option[String] =
    val body = reference.substring(1, reference.length - 1)
    if body.startsWith("#x") || body.startsWith("#X") then codePoint(body.substring(2), 16)
    else if body.startsWith("#") then codePoint(body.substring(1), 10)
    else entities.lookup(body)

  private def codePoint(digits: String, radix: Int): Option[String] =
    if digits.isEmpty || digits.length > 7 then None
    else
      try
        val value = java.lang.Integer.parseInt(digits, radix)
        // §2.5: NUL und alles ausserhalb von Unicode werden zum Ersetzungszeichen.
        if value == 0 || value > 0x10ffff then Some("�")
        else Some(new String(Character.toChars(value)))
      catch case _: NumberFormatException => None

  /** Percent-encodes what a URL may not contain literally.
    *
    * The reference calls out to `mdurl`. Reimplemented here rather than pulled in, for the reason
    * §6 gives this module no dependencies at all -- and because the rule is short: leave an
    * existing valid escape alone, leave the unreserved and reserved sets alone, encode the rest as
    * UTF-8 bytes.
    */
  def normaliseUri(uri: String): String =
    val out = new StringBuilder(uri.length)
    var i   = 0
    while i < uri.length do
      val c = uri.charAt(i)
      if c == '%' && i + 2 < uri.length && isHex(uri.charAt(i + 1)) && isHex(uri.charAt(i + 2))
      then
        // `substring` und nicht `append(uri, i, i + 3)`: Scalas StringBuilder hat keine solche
        // Ueberladung, und statt eines Compilefehlers greift Auto-Tupling -- der Puffer bekaeme
        // `(uri,3,6)` als Text. Der Fehler faellt erst in einer URL auf, die ein `%` enthaelt.
        out.append(uri.substring(i, i + 3))
        i += 3
      else if UriSafe.contains(c) then
        out.append(c)
        i += 1
      else
        val codePoint = uri.codePointAt(i)
        val width     = Character.charCount(codePoint)
        new String(uri.toCharArray, i, width)
          .getBytes(java.nio.charset.StandardCharsets.UTF_8)
          .foreach(byte => out.append("%%%02X".format(byte & 0xff)))
        i += width
    out.toString

  private def isHex(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

  /** What may stand literally in a URL.
    *
    * Unreserved plus reserved per RFC 3986, '''minus''' `[` and `]`. Those two are reserved for
    * IPv6 literals in the authority and are encoded anywhere else -- which is what makes
    * `<https://example.com/?search=](uri)>` come out with `%5D` instead of a bracket that would end
    * the link for the next reader of the source.
    */
  private val UriSafe: Set[Char] =
    (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toSet ++ "-_.~!*'();:@&=+$,/?#".toSet
