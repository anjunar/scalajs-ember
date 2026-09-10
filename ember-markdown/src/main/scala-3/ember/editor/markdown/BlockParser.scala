package ember.editor.markdown

import scala.collection.mutable
import scala.util.matching.Regex

/** The entry point of this module.
  *
  * §18.1: "`ember-markdown` enthaelt einen in Scala geschriebenen Parser und Writer. Er braucht
  * weder Lexical noch DOM noch HTML als Zwischenstufe."
  */
object Markdown:

  /** Parses a source into block syntax.
    *
    * Deterministic and free of side effects: the same source and profile give the same tree, on
    * any platform, with no DOM and no clock involved (P17, acceptance).
    *
    * Returns `Either` rather than throwing, because every failure here is a resource bound, and
    * a resource bound is an expected outcome for foreign input rather than a broken call
    * (`ember-core/package.scala`).
    */
  def parseSyntax(
      source: String,
      profile: MarkdownProfile = MarkdownProfile.commonMarkSafe
  ): Either[ParseError, ParseResult] =
    val limits = profile.limits

    if source.length > limits.maxSourceChars then
      Left(ParseError.LimitExceeded("maxSourceChars", limits.maxSourceChars, source.length))
    else new BlockParser(source, profile).run()

/** A successful parse.
  *
  * @param document
  *   the block tree.
  * @param sourceMap
  *   source spans by block; see [[SourceMap]].
  * @param diagnostics
  *   things worth knowing about a tree that nonetheless exists.
  */
final case class ParseResult(
    document: MarkdownDocument,
    sourceMap: SourceMap,
    diagnostics: Vector[ParseDiagnostic] = Vector.empty
)

/** The CommonMark block parser.
  *
  * ==Provenance==
  *
  * This is a port of the rule structure of `lib/blocks.js` from commonmark.js 0.31.2
  * (BSD-2-Clause, Copyright (c) 2014 John MacFarlane -- see `ember-markdown/NOTICE`). What was
  * taken is the shape: the order of block starts, the continuation condition of each container,
  * the lazy-continuation rule and the list-marker arithmetic. Those are the parts P17's risk
  * line calls "echte Parserarbeit", and re-deriving them from the specification would produce a
  * worse parser and the same rules.
  *
  * What was '''not''' taken is the code. Each difference has a reason:
  *
  *   - '''Offsets instead of line/column.''' commonmark.js reports `sourcepos` as line/column
  *     pairs with tab-expanded columns. §18.2 wants UTF-16 ranges, and everything else in this
  *     editor counts that way (§11). Converting afterwards is where an off-by-one hides, so this
  *     parser tracks absolute offsets from the start.
  *   - '''A budget.''' §18.2 bounds work steps; the reference has no such notion.
  *   - '''No inline parser.''' P17 is blocks. Where commonmark.js calls `processInlines` and
  *     strips link reference definitions, this parser leaves the source where it is.
  *   - '''An immutable result.''' The mutable open-block tree exists only during the parse and
  *     is converted once at the end. Nothing outside this file can observe a half-built block.
  *
  * ==Why one mutable class and not a fold==
  *
  * Because the algorithm is a line-at-a-time state machine over an open-block spine, and every
  * line touches `offset`, `column`, `tip` and the partially consumed tab. Threading eight fields
  * through a fold would not make it more functional, only harder to compare against the
  * reference -- and comparing against the reference is how this stays correct.
  *
  * The mutability is contained: the class is `private`, single-use, and its only surface is
  * [[run]], which returns an immutable value.
  */
private final class BlockParser(source: String, profile: MarkdownProfile):

  import BlockParser.*

  private val limits = profile.limits

  /** Lines and their absolute start offsets. Computed once -- every span reads them. */
  private val (lines, lineStarts) = splitLines(source)

  private val document           = new OpenBlock(OpenKind.Document, 0, null)
  private var tip: OpenBlock     = document
  private var oldTip: OpenBlock  = document
  private var lastMatched: OpenBlock = document
  private var allClosed          = true

  private var currentLine    = ""
  private var lineNumber     = 0
  private var lineStart      = 0
  private var lastLineLength = 0

  private var offset               = 0
  private var column               = 0
  private var nextNonspace         = 0
  private var nextNonspaceColumn   = 0
  private var indent               = 0
  private var indented             = false
  private var blank                = false
  private var partiallyConsumedTab = false

  private var steps   = 0
  private var created = 0
  private var nextId  = 0

  private var failure: Option[ParseError] = None

  def run(): Either[ParseError, ParseResult] =
    if lines.length > limits.maxLines then
      Left(ParseError.LimitExceeded("maxLines", limits.maxLines, lines.length))
    else
      var index = 0
      while index < lines.length && failure.isEmpty do
        incorporateLine(index)
        index += 1

      if failure.isEmpty then while tip != null do finalizeBlock(tip, lines.length)

      failure match
        case Some(error) => Left(error)
        case None =>
          val spans = mutable.Map.empty[Int, SourceSpan]
          materialise(document, 0, spans) match
            case Left(error) => Left(error)
            case Right(root: MarkdownDocument) =>
              Right(ParseResult(root, SourceMap(spans.toMap, lineStarts.toVector)))
            case Right(other) =>
              // Unerreichbar: die Wurzel ist immer `OpenKind.Document`. Der Fall steht hier,
              // damit der Compiler die Vollstaendigkeit prueft statt eines Casts.
              Left(ParseError.LimitExceeded("root", 1, other.children.length))

  // -----------------------------------------------------------------------------------------
  // Budget
  // -----------------------------------------------------------------------------------------

  /** Charges work and records the first overrun.
    *
    * Recording instead of throwing: an exception thrown through a mutable state machine leaves
    * it in an unknown state, and the caller gets a stack trace where the convention asks for a
    * [[ParseError]]. Every loop checks `failure` and stops.
    */
  private def spend(amount: Int): Boolean =
    steps += amount
    if steps > limits.maxSteps then
      if failure.isEmpty then
        failure = Some(ParseError.BudgetExhausted(limits.maxSteps, lineNumber))
      false
    else true

  // -----------------------------------------------------------------------------------------
  // Position innerhalb der Zeile
  // -----------------------------------------------------------------------------------------

  /** Absolute offset of the current position in the source. */
  private def here: Int = lineStart + offset

  private def peek(at: Int): Int =
    if at >= 0 && at < currentLine.length then currentLine.charAt(at).toInt else -1

  private def restOfLine(from: Int): String =
    currentLine.substring(math.min(math.max(from, 0), currentLine.length))

  private def advanceOffset(count: Int, columns: Boolean): Unit =
    var remaining = count
    var running   = true
    while remaining > 0 && running do
      if offset >= currentLine.length then running = false
      else
        val c = currentLine.charAt(offset)
        if c == '\t' then
          val charsToTab = 4 - (column % 4)
          if columns then
            partiallyConsumedTab = charsToTab > remaining
            val toAdvance = math.min(charsToTab, remaining)
            column += toAdvance
            if !partiallyConsumedTab then offset += 1
            remaining -= toAdvance
          else
            partiallyConsumedTab = false
            column += charsToTab
            offset += 1
            remaining -= 1
        else
          partiallyConsumedTab = false
          offset += 1
          column += 1
          remaining -= 1

  private def advanceNextNonspace(): Unit =
    offset = nextNonspace
    column = nextNonspaceColumn
    partiallyConsumedTab = false

  private def findNextNonspace(): Unit =
    var i        = offset
    var cols     = column
    var scanning = true
    while scanning do
      if i >= currentLine.length then scanning = false
      else
        currentLine.charAt(i) match
          case ' ' =>
            i += 1
            cols += 1
          case '\t' =>
            i += 1
            cols += 4 - (cols % 4)
          case _ => scanning = false

    blank = i >= currentLine.length
    nextNonspace = i
    nextNonspaceColumn = cols
    indent = nextNonspaceColumn - column
    indented = indent >= CodeIndent

  // -----------------------------------------------------------------------------------------
  // Baumpflege
  // -----------------------------------------------------------------------------------------

  private def addChild(kind: OpenKind, startOffset: Int): OpenBlock =
    while !canContain(tip.kind, kind) do finalizeBlock(tip, lineNumber - 1)

    created += 1
    if created > limits.maxBlocks && failure.isEmpty then
      failure = Some(ParseError.LimitExceeded("maxBlocks", limits.maxBlocks, created))

    val child = new OpenBlock(kind, startOffset, tip)
    child.startLine = lineNumber
    child.endLine = lineNumber
    tip.children += child
    tip = child
    child

  private def addLine(): Unit =
    if partiallyConsumedTab then
      offset += 1
      tip.content.append(" " * (4 - (column % 4)))
    tip.content.append(restOfLine(offset)).append('\n')

  private def closeUnmatchedBlocks(): Unit =
    if !allClosed then
      while oldTip ne lastMatched do
        val parent = oldTip.parent
        finalizeBlock(oldTip, lineNumber - 1)
        oldTip = parent
      allClosed = true

  /** Absolute offset of the start of a one-based line number. */
  private def lineStartOf(number: Int): Int =
    val index = number - 1
    if index < 0 then 0
    else if index < lines.length then lineStarts(index)
    else source.length

  /** Absolute offset just past the last character of a one-based line number. */
  private def endOfLineAt(number: Int): Int =
    val index = number - 1
    if index < 0 then 0
    else if index < lines.length then lineStarts(index) + lines(index).length
    else source.length

  /** Closes a block, computes its end and runs whatever its kind still owes.
    *
    * `lastLineLength` is deliberately the length of the '''last processed''' line and not of
    * `atLine`: a closing code fence sets it short, so the block ends at the fence and not at
    * the trailing spaces after it. That is the reference's behaviour, and the two only differ
    * where CommonMark itself makes the distinction.
    */
  private def finalizeBlock(block: OpenBlock, atLine: Int): Unit =
    val above = block.parent
    block.open = false
    block.endLine = atLine
    block.endOffset = math.min(source.length, lineStartOf(atLine) + lastLineLength)

    block.kind match
      case OpenKind.Code(true, _, _, _) =>
        // Die erste Zeile eines Zauns ist der Info-String, nicht Inhalt.
        val content   = block.content.toString
        val newline   = content.indexOf('\n')
        val firstLine = if newline < 0 then content else content.substring(0, newline)
        val rest      = if newline < 0 then "" else content.substring(newline + 1)
        block.info = unescapeString(trimWhitespace(firstLine))
        block.literal = rest

      case OpenKind.Code(false, _, _, _) =>
        val kept = block.content.toString.split("\n", -1).toBuffer
        if kept.nonEmpty && kept.last.isEmpty then kept.remove(kept.length - 1)
        // Trailing blank lines separated the block from what followed and are not part of it.
        while kept.nonEmpty && isBlankLine(kept.last) do kept.remove(kept.length - 1)
        block.literal = if kept.isEmpty then "" else kept.mkString("", "\n", "\n")
        val lastKept = block.startLine + kept.length - 1
        if kept.nonEmpty && lastKept >= 1 && lastKept <= lines.length then
          block.endLine = lastKept
          block.endOffset = lineStarts(lastKept - 1) + lines(lastKept - 1).length

      case OpenKind.Html =>
        block.literal = block.content.toString.stripSuffix("\n")

      case OpenKind.ListBlock(_) =>
        markLooseness(block)
        block.children.lastOption.foreach { last =>
          block.endOffset = last.endOffset
          block.endLine = last.endLine
        }

      case OpenKind.Item(marker) =>
        block.children.lastOption match
          case Some(last) =>
            block.endOffset = last.endOffset
            block.endLine = last.endLine
          case None =>
            // An empty item covers its marker and nothing more -- and never more than the line
            // it sits on. `padding` counts the space that would follow the marker, and for a
            // bare `*` on its own line that space does not exist.
            block.endOffset =
              math.min(endOfLineAt(block.startLine), block.startOffset + marker.padding)
            block.endLine = block.startLine

      case _ => ()

    tip = above

  /** A list is loose when a blank line separates two items, or two blocks inside one item.
    *
    * The reference reads this off line numbers in `sourcepos`; so does this, for the same
    * reason -- the question is whether anything ends more than one line before its successor
    * begins, and that is a statement about lines, not offsets.
    */
  private def markLooseness(list: OpenBlock): Unit =
    def separated(children: mutable.ArrayBuffer[OpenBlock]): Boolean =
      children.indices.exists { index =>
        index + 1 < children.length &&
        children(index).endLine < children(index + 1).startLine - 1
      }

    list.tight = !(separated(list.children) || list.children.exists(item => separated(item.children)))

  // -----------------------------------------------------------------------------------------
  // Eine Zeile
  // -----------------------------------------------------------------------------------------

  private def incorporateLine(index: Int): Unit =
    if !spend(1) then return

    var allMatched = true

    var container = document
    oldTip = tip
    offset = 0
    column = 0
    blank = false
    partiallyConsumedTab = false
    lineNumber = index + 1
    lineStart = lineStarts(index)

    // NUL wird ersetzt, wie in der Vorlage: ein Dokument, das eines bis in ein DOM traegt, ist
    // eine Gefahr. U+FFFD hat dieselbe UTF-16-Laenge, also verschiebt sich kein Offset.
    val raw = lines(index)
    currentLine = if raw.indexOf(0) >= 0 then raw.replace(0.toChar, '\ufffd') else raw

    // Fuer jeden offenen Container die Fortsetzungsbedingung. Bricht ab, sobald einer nicht
    // passt -- `container` zeigt dann auf den letzten, der passte.
    var descending = true
    while descending do
      container.children.lastOption.filter(_.open) match
        case None => descending = false
        case Some(child) =>
          container = child
          findNextNonspace()
          continueBlock(container) match
            case Continue.Matched  => ()
            case Continue.Failed   => allMatched = false
            case Continue.LineDone => return
          if !allMatched then
            container = container.parent
            descending = false

    allClosed = container eq oldTip
    lastMatched = container

    // Ein Absatz setzt `matchedLeaf` ausdruecklich '''nicht''': genau deshalb koennen eine
    // Setext-Unterstreichung, ein Listenpunkt oder ein Zitat ihn unterbrechen.
    var matchedLeaf = !isParagraph(container.kind) && acceptsLines(container.kind)

    while !matchedLeaf && failure.isEmpty do
      findNextNonspace()

      if !indented && !maybeSpecial(currentLine, nextNonspace) then
        advanceNextNonspace()
        matchedLeaf = true
      else if !spend(1) then return
      else
        tryBlockStarts(container) match
          case Start.None =>
            advanceNextNonspace()
            matchedLeaf = true
          case Start.Container =>
            container = tip
          case Start.Leaf =>
            container = tip
            matchedLeaf = true

    if failure.nonEmpty then return

    // Lazy continuation: eine Textzeile, deren Container nicht mehr passt, gehoert trotzdem zum
    // offenen Absatz. Das ist die Regel, an der ein selbstgebauter Parser scheitert.
    if !allClosed && !blank && isParagraph(tip.kind) then addLine()
    else
      closeUnmatchedBlocks()

      if acceptsLines(container.kind) then
        addLine()
        if isHtml(container.kind) && container.htmlKind >= 1 && container.htmlKind <= 5 &&
          HtmlBlockClose(container.htmlKind).findFirstIn(restOfLine(offset)).isDefined
        then
          lastLineLength = currentLine.length
          finalizeBlock(container, lineNumber)
      else if offset < currentLine.length && !blank then
        container = addChild(OpenKind.Paragraph, here)
        advanceNextNonspace()
        addLine()

    lastLineLength = currentLine.length

  // -----------------------------------------------------------------------------------------
  // Fortsetzungsbedingungen
  // -----------------------------------------------------------------------------------------

  private def continueBlock(container: OpenBlock): Continue = container.kind match
    case OpenKind.Document | OpenKind.ListBlock(_) => Continue.Matched

    case OpenKind.BlockQuote =>
      if !indented && peek(nextNonspace) == '>'.toInt then
        advanceNextNonspace()
        advanceOffset(1, false)
        if isSpaceOrTab(peek(offset)) then advanceOffset(1, true)
        Continue.Matched
      else Continue.Failed

    case OpenKind.Item(marker) =>
      if indent >= marker.markerOffset + marker.padding then
        advanceOffset(marker.markerOffset + marker.padding, true)
        Continue.Matched
      else if blank && container.children.nonEmpty then
        // A blank line continues the item even without the indent -- but not after an empty
        // item, which is what the child check rules out.
        advanceNextNonspace()
        Continue.Matched
      else Continue.Failed

    case OpenKind.Code(fenced, fenceChar, fenceLength, fenceOffset) =>
      if fenced then
        val closing =
          if indent <= 3 && peek(nextNonspace) == fenceChar.toInt then
            ClosingCodeFence.findPrefixOf(restOfLine(nextNonspace))
          else None

        closing match
          case Some(fence) if fence.length >= fenceLength =>
            lastLineLength = offset + indent + fence.length
            finalizeBlock(container, lineNumber)
            Continue.LineDone
          case _ =>
            var remaining = fenceOffset
            while remaining > 0 && isSpaceOrTab(peek(offset)) do
              advanceOffset(1, true)
              remaining -= 1
            Continue.Matched
      else if indent >= CodeIndent then
        advanceOffset(CodeIndent, true)
        Continue.Matched
      else if blank then
        advanceNextNonspace()
        Continue.Matched
      else Continue.Failed

    case OpenKind.Html =>
      if blank && (container.htmlKind == 6 || container.htmlKind == 7) then Continue.Failed
      else Continue.Matched

    case OpenKind.Paragraph =>
      if blank then Continue.Failed else Continue.Matched

    case OpenKind.Heading(_, _) | OpenKind.ThematicBreak =>
      // Neither can ever span more than one line.
      Continue.Failed

  // -----------------------------------------------------------------------------------------
  // Blockanfaenge, in genau dieser Reihenfolge
  // -----------------------------------------------------------------------------------------

  /** The order is load-bearing.
    *
    * A Setext underline has to be tried before a thematic break, or `---` under a paragraph
    * would end it instead of making it a heading. A list item has to come after the thematic
    * break, or `- - -` would start three nested lists. This is the reference's order, and
    * changing it is not a refactoring.
    *
    * `orElse` takes its argument by name, which matters more than it looks: every one of these
    * has side effects on the parser position, so a strict argument would run all eight.
    */
  private def tryBlockStarts(container: OpenBlock): Start =
    startBlockQuote()
      .orElse(startAtxHeading())
      .orElse(startFencedCode())
      .orElse(startHtmlBlock(container))
      .orElse(startSetextHeading(container))
      .orElse(startThematicBreak())
      .orElse(startListItem(container))
      .orElse(startIndentedCode())

  private def startBlockQuote(): Start =
    if !indented && peek(nextNonspace) == '>'.toInt then
      advanceNextNonspace()
      advanceOffset(1, false)
      if isSpaceOrTab(peek(offset)) then advanceOffset(1, true)
      closeUnmatchedBlocks()
      addChild(OpenKind.BlockQuote, lineStart + nextNonspace)
      Start.Container
    else Start.None

  private def startAtxHeading(): Start =
    if indented then Start.None
    else
      AtxHeadingMarker.findPrefixOf(restOfLine(nextNonspace)) match
        case None => Start.None
        case Some(marker) =>
          val at = lineStart + nextNonspace
          advanceNextNonspace()
          advanceOffset(marker.length, false)
          closeUnmatchedBlocks()
          val block = addChild(OpenKind.Heading(trimWhitespace(marker).length, HeadingStyle.Atx), at)
          // Ein `###` am Zeilenende ist eine schliessende Sequenz, kein Inhalt.
          val rest = restOfLine(offset)
          block.content.append(
            AtxTrailing.replaceFirstIn(AtxOnlyClosing.replaceFirstIn(rest, ""), "")
          )
          advanceOffset(currentLine.length - offset, false)
          Start.Leaf

  private def startFencedCode(): Start =
    if indented then Start.None
    else
      CodeFence.findPrefixOf(restOfLine(nextNonspace)) match
        case None => Start.None
        case Some(fence) =>
          closeUnmatchedBlocks()
          addChild(
            OpenKind.Code(
              fenced = true,
              fenceChar = fence.charAt(0),
              fenceLength = fence.length,
              fenceOffset = indent
            ),
            lineStart + nextNonspace
          )
          advanceNextNonspace()
          advanceOffset(fence.length, false)
          Start.Leaf

  private def startHtmlBlock(container: OpenBlock): Start =
    if indented || peek(nextNonspace) != '<'.toInt then Start.None
    else
      val rest  = restOfLine(nextNonspace)
      var kind  = 1
      var found = 0
      while kind <= 7 && found == 0 do
        // Kind 7 may not interrupt a paragraph -- neither the matched one nor one that is
        // about to continue lazily.
        val mayInterrupt =
          kind < 7 ||
            (!isParagraph(container.kind) && !(!allClosed && !blank && isParagraph(tip.kind)))
        if mayInterrupt && HtmlBlockOpen(kind).findFirstIn(rest).isDefined then found = kind
        else kind += 1

      if found == 0 then Start.None
      else
        closeUnmatchedBlocks()
        // Der Offset bleibt, wo er ist: fuehrende Leerzeichen gehoeren zum HTML-Block.
        val block = addChild(OpenKind.Html, here)
        block.htmlKind = found
        Start.Leaf

  private def startSetextHeading(container: OpenBlock): Start =
    if indented || !isParagraph(container.kind) then Start.None
    else
      val rest = restOfLine(nextNonspace)
      if SetextHeadingLine.findPrefixOf(rest).isEmpty then Start.None
      else
        closeUnmatchedBlocks()
        if container.content.isEmpty then Start.None
        else
          // In place: der Absatz haelt Inhalt und Startoffset bereits, ein neuer Knoten muesste
          // beides kopieren. Die Vorlage haengt um, weil ihr Baum eine verkettete Liste ist.
          container.kind = OpenKind.Heading(if rest.charAt(0) == '=' then 1 else 2, HeadingStyle.Setext)
          tip = container
          advanceOffset(currentLine.length - offset, false)
          Start.Leaf

  private def startThematicBreak(): Start =
    if !indented && ThematicBreakLine.matches(restOfLine(nextNonspace)) then
      closeUnmatchedBlocks()
      addChild(OpenKind.ThematicBreak, lineStart + nextNonspace)
      advanceOffset(currentLine.length - offset, false)
      Start.Leaf
    else Start.None

  private def startListItem(container: OpenBlock): Start =
    if indented && !isList(container.kind) then Start.None
    else
      parseListMarker(container) match
        case None => Start.None
        case Some(marker) =>
          closeUnmatchedBlocks()

          val continues = tip.kind match
            case OpenKind.ListBlock(open) => open.sameListAs(marker)
            case _                        => false

          if !continues then addChild(OpenKind.ListBlock(marker), lineStart + nextNonspace)
          addChild(OpenKind.Item(marker), lineStart + nextNonspace)
          Start.Container

  private def startIndentedCode(): Start =
    if indented && !isParagraph(tip.kind) && !blank then
      advanceOffset(CodeIndent, true)
      closeUnmatchedBlocks()
      addChild(OpenKind.Code(fenced = false, fenceChar = ' ', fenceLength = 0, fenceOffset = 0), here)
      Start.Leaf
    else Start.None

  /** The list marker, and the padding arithmetic that decides what counts as its content.
    *
    * This is the fiddliest rule in the block grammar, and the port keeps it literally. The two
    * branches at the end are why: normally an item's content starts after the marker plus the
    * spaces that follow it, but with five or more spaces -- or none, or a blank item -- the
    * content starts one space after the marker and the rest is indentation '''inside''' the
    * item. Getting this wrong turns `-     foo` from an item holding a paragraph into an item
    * holding a code block.
    */
  private def parseListMarker(container: OpenBlock): Option[ListMarker] =
    if indent >= CodeIndent then None
    else
      val rest = restOfLine(nextNonspace)

      val parsed: Option[(String, ListKind)] =
        BulletListMarker.findPrefixOf(rest) match
          case Some(bullet) => Some((bullet, ListKind.Bullet(bullet.charAt(0))))
          case None =>
            OrderedListMarker.findPrefixMatchOf(rest) match
              case None => None
              case Some(found) =>
                val start = found.group(1).toInt
                // An ordered list can only interrupt a paragraph when it starts at 1 --
                // otherwise `2024. was a year` would turn into a list.
                if isParagraph(container.kind) && start != 1 then None
                else Some((found.matched, ListKind.Ordered(start, found.group(2).charAt(0))))

      parsed.flatMap { (marker, kind) =>
        val after = peek(nextNonspace + marker.length)
        if !(after == -1 || after == '\t'.toInt || after == ' '.toInt) then None
        else if isParagraph(container.kind) &&
          isBlankLine(restOfLine(nextNonspace + marker.length))
        then None // ein leerer erster Punkt darf keinen Absatz unterbrechen
        else
          advanceNextNonspace()
          val markerOffset = indent
          advanceOffset(marker.length, true)
          val spacesStartCol    = column
          val spacesStartOffset = offset

          var scanning = true
          while scanning do
            advanceOffset(1, true)
            scanning = column - spacesStartCol < 5 && isSpaceOrTab(peek(offset))

          val blankItem         = peek(offset) == -1
          val spacesAfterMarker = column - spacesStartCol

          val padding =
            if spacesAfterMarker >= 5 || spacesAfterMarker < 1 || blankItem then
              column = spacesStartCol
              offset = spacesStartOffset
              if isSpaceOrTab(peek(offset)) then advanceOffset(1, true)
              marker.length + 1
            else marker.length + spacesAfterMarker

          Some(ListMarker(kind, markerOffset, padding))
      }

  // -----------------------------------------------------------------------------------------
  // Vom offenen Baum zum unveraenderlichen
  // -----------------------------------------------------------------------------------------

  /** Converts the open tree into the immutable one, depth first.
    *
    * This is the only recursion in the module, and `maxDepth` is what keeps it off the stack
    * limit: a source of fifty thousand `>` would otherwise overflow here, where the parse loop
    * could not see it coming.
    *
    * Ids are handed out in post order, so a child's id is always smaller than its parent's.
    * That is not load-bearing, but it makes a printed tree readable, and an id scheme with no
    * property at all is a missed opportunity.
    */
  private def materialise(
      block: OpenBlock,
      depth: Int,
      spans: mutable.Map[Int, SourceSpan]
  ): Either[ParseError, MarkdownBlock] =
    if depth > limits.maxDepth then
      Left(ParseError.LimitExceeded("maxDepth", limits.maxDepth, depth))
    else
      val builder                   = Vector.newBuilder[MarkdownBlock]
      var error: Option[ParseError] = None

      block.children.foreach { child =>
        if error.isEmpty then
          materialise(child, depth + 1, spans) match
            case Left(problem) => error = Some(problem)
            case Right(built)  => builder += built
      }

      error match
        case Some(problem) => Left(problem)
        case None =>
          val children = builder.result()
          val id       = SyntaxId(nextId)
          nextId += 1

          val span = SourceSpan(block.startOffset, math.max(block.startOffset, block.endOffset))
          spans += (id.value -> span)

          Right(block.kind match
            case OpenKind.Document =>
              MarkdownDocument(id, span, children)
            case OpenKind.BlockQuote =>
              MarkdownBlock.BlockQuote(id, span, children)
            case OpenKind.ListBlock(marker) =>
              MarkdownBlock.MarkdownList(id, span, marker.kind, block.tight, children)
            case OpenKind.Item(_) =>
              MarkdownBlock.ListItem(id, span, children)
            case OpenKind.Paragraph =>
              MarkdownBlock.Paragraph(id, span, trimWhitespace(block.content.toString))
            case OpenKind.Heading(level, style) =>
              MarkdownBlock.Heading(id, span, level, style, trimWhitespace(block.content.toString))
            case OpenKind.Code(fenced, char, length, _) =>
              val fence = if fenced then Some(Fence(char, length, block.info)) else None
              MarkdownBlock.CodeBlock(id, span, block.literal, fence)
            case OpenKind.Html =>
              MarkdownBlock.HtmlBlock(id, span, block.literal)
            case OpenKind.ThematicBreak =>
              MarkdownBlock.ThematicBreak(id, span))

  // -----------------------------------------------------------------------------------------
  // Kleinkram
  // -----------------------------------------------------------------------------------------

  private def isParagraph(kind: OpenKind): Boolean = kind match
    case OpenKind.Paragraph => true
    case _                  => false

  private def isList(kind: OpenKind): Boolean = kind match
    case OpenKind.ListBlock(_) => true
    case _                     => false

  private def isHtml(kind: OpenKind): Boolean = kind match
    case OpenKind.Html => true
    case _             => false

  private def acceptsLines(kind: OpenKind): Boolean = kind match
    case OpenKind.Paragraph | OpenKind.Html => true
    case OpenKind.Code(_, _, _, _)          => true
    case _                                  => false

  private def canContain(parent: OpenKind, child: OpenKind): Boolean =
    val childIsItem = child match
      case OpenKind.Item(_) => true
      case _                => false

    parent match
      case OpenKind.Document | OpenKind.BlockQuote | OpenKind.Item(_) => !childIsItem
      case OpenKind.ListBlock(_)                                     => childIsItem
      case _                                                         => false

private object BlockParser:

  val CodeIndent = 4

  // Die Muster der Vorlage, unveraendert uebernommen. Keines hat eine verschachtelte
  // Wiederholung ueber derselben Zeichenklasse -- P17s Risikozeile "keine katastrophale
  // Regex-Laufzeit" ist damit eine Eigenschaft der Muster und nicht nur eine Absicht.
  val ThematicBreakLine: Regex =
    raw"""(?:\*[ \t]*){3,}|(?:_[ \t]*){3,}|(?:-[ \t]*){3,}""".r
  val BulletListMarker: Regex  = raw"""[*+-]""".r
  val OrderedListMarker: Regex = raw"""(\d{1,9})([.)])""".r
  val AtxHeadingMarker: Regex  = raw"""#{1,6}(?:[ \t]+|$$)""".r
  val CodeFence: Regex         = raw"""`{3,}(?!.*`)|~{3,}""".r
  val ClosingCodeFence: Regex  = raw"""(?:`{3,}|~{3,})(?=[ \t]*$$)""".r
  val SetextHeadingLine: Regex = raw"""(?:=+|-+)[ \t]*$$""".r
  val NonSpace: Regex          = raw"""[^ \t\f\r\n]""".r

  val AtxOnlyClosing: Regex = raw"""^[ \t]*#+[ \t]*$$""".r
  val AtxTrailing: Regex    = raw"""[ \t]+#+[ \t]*$$""".r

  /** Cheap pre-filter: a line whose first non-space character is none of these can begin no
    * block. The reference keeps it as a performance optimisation; here it also keeps the step
    * budget honest, because an ordinary paragraph line then costs one step instead of eight.
    */
  private val MaybeSpecialChars = "#`~*+_=<>0123456789-".toSet

  def maybeSpecial(line: String, at: Int): Boolean =
    at < line.length && MaybeSpecialChars.contains(line.charAt(at))

  private val OpenTag =
    raw"""<[A-Za-z][A-Za-z0-9-]*(?:[ \t\n]+[a-zA-Z_:][a-zA-Z0-9:._-]*(?:[ \t\n]*=""" +
      raw"""[ \t\n]*(?:[^"'=<>`\x00-\x20]+|'[^']*'|"[^"]*"))?)*[ \t\n]*/?>"""

  private val CloseTag = raw"""</[A-Za-z][A-Za-z0-9-]*[ \t\n]*>"""

  /** The seven HTML block kinds of the specification, indexed from one so the numbers match the
    * spec's own. Index zero is never read.
    */
  val HtmlBlockOpen: Array[Regex] = Array(
    raw""".""".r,
    raw"""(?i)^<(?:script|pre|textarea|style)(?:\s|>|$$)""".r,
    raw"""^<!--""".r,
    raw"""^<[?]""".r,
    raw"""^<![A-Za-z]""".r,
    raw"""^<!\[CDATA\[""".r,
    (raw"""(?i)^</?(?:address|article|aside|base|basefont|blockquote|body|caption|center|col|""" +
      raw"""colgroup|dd|details|dialog|dir|div|dl|dt|fieldset|figcaption|figure|footer|form|""" +
      raw"""frame|frameset|h[123456]|head|header|hr|html|iframe|legend|li|link|main|menu|""" +
      raw"""menuitem|nav|noframes|ol|optgroup|option|p|param|section|search|summary|table|""" +
      raw"""tbody|td|tfoot|th|thead|title|tr|track|ul)(?:\s|[/]?[>]|$$)""").r,
    raw"""(?i)^(?:$OpenTag|$CloseTag)\s*$$""".r
  )

  val HtmlBlockClose: Array[Regex] = Array(
    raw""".""".r,
    raw"""(?i)</(?:script|pre|textarea|style)>""".r,
    raw"""-->""".r,
    raw"""\?>""".r,
    raw""">""".r,
    raw"""\]\]>""".r
  )

  def isSpaceOrTab(code: Int): Boolean = code == ' '.toInt || code == '\t'.toInt

  def isBlankLine(line: String): Boolean = NonSpace.findFirstIn(line).isEmpty

  /** Splits a source into lines and their absolute start offsets.
    *
    * All three line endings, and a trailing newline does '''not''' produce a final empty line --
    * the reference drops it, and keeping it would append an empty paragraph to every document
    * that ends the way documents normally end.
    */
  def splitLines(source: String): (Array[String], Array[Int]) =
    val texts  = Array.newBuilder[String]
    val starts = Array.newBuilder[Int]

    var index     = 0
    var lineStart = 0
    while index < source.length do
      source.charAt(index) match
        case '\n' =>
          texts += source.substring(lineStart, index)
          starts += lineStart
          index += 1
          lineStart = index
        case '\r' =>
          texts += source.substring(lineStart, index)
          starts += lineStart
          index += (if index + 1 < source.length && source.charAt(index + 1) == '\n' then 2 else 1)
          lineStart = index
        case _ => index += 1

    if lineStart < source.length then
      texts += source.substring(lineStart)
      starts += lineStart

    (texts.result(), starts.result())

  /** Whitespace as ECMAScript defines it, because the reference trims with `String.trim`.
    *
    * `java.lang.String.trim` stops at U+0020 and would leave a non-breaking space or an ideo-
    * graphic space standing. The specification has examples with both, so the difference is
    * observable and this is not pedantry.
    */
  private def isTrimmable(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == '\u000b' ||
      c == '\u00a0' || c == '\u1680' || (c >= '\u2000' && c <= '\u200a') ||
      c == '\u2028' || c == '\u2029' || c == '\u202f' || c == '\u205f' ||
      c == '\u3000' || c == '\ufeff'

  def trimWhitespace(text: String): String =
    var start = 0
    var end   = text.length
    while start < end && isTrimmable(text.charAt(start)) do start += 1
    while end > start && isTrimmable(text.charAt(end - 1)) do end -= 1
    text.substring(start, end)

  private val EscapableChars = """!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~""".toSet

  /** Backslash escapes in an info string.
    *
    * The only unescaping the block parser does, and it does it because the info string is a
    * block-level value: it is decided when the fence is finalised, not when inlines are parsed.
    * Everything else stays escaped for P18.
    */
  def unescapeString(text: String): String =
    if text.indexOf('\\') < 0 then text
    else
      val out = new StringBuilder(text.length)
      var i   = 0
      while i < text.length do
        val c = text.charAt(i)
        if c == '\\' && i + 1 < text.length && EscapableChars.contains(text.charAt(i + 1)) then
          out.append(text.charAt(i + 1))
          i += 2
        else
          out.append(c)
          i += 1
      out.toString

  /** The result of one continuation check. Three outcomes, exactly as in the reference. */
  enum Continue:
    case Matched, Failed, LineDone

  /** The result of one block-start attempt. */
  enum Start:
    case None, Container, Leaf

    /** By name, because every alternative moves the parser position. */
    def orElse(next: => Start): Start = this match
      case Start.None => next
      case matched    => matched

  final case class ListMarker(kind: ListKind, markerOffset: Int, padding: Int):

    /** Two markers belong to the same list when type, bullet character and delimiter agree.
      * Changing any of them starts a new list (§18.2).
      */
    def sameListAs(other: ListMarker): Boolean = (kind, other.kind) match
      case (ListKind.Bullet(a), ListKind.Bullet(b))         => a == b
      case (ListKind.Ordered(_, a), ListKind.Ordered(_, b)) => a == b
      case _                                                => false

  /** A block while it is still open. Mutable, file-private, and gone before anyone outside sees
    * a result.
    */
  final class OpenBlock(var kind: OpenKind, val startOffset: Int, val parent: OpenBlock):
    val children: mutable.ArrayBuffer[OpenBlock] = mutable.ArrayBuffer.empty
    val content: StringBuilder                   = new StringBuilder

    var open      = true
    var endOffset = startOffset
    var startLine = 0
    var endLine   = 0
    var tight     = true
    var literal   = ""
    var info      = ""
    var htmlKind  = 0

  enum OpenKind:
    case Document
    case BlockQuote
    case ListBlock(marker: ListMarker)
    case Item(marker: ListMarker)
    case Paragraph
    case Heading(level: Int, style: HeadingStyle)
    case Code(fenced: Boolean, fenceChar: Char, fenceLength: Int, fenceOffset: Int)
    case Html
    case ThematicBreak
