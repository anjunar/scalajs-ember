package ember.editor.markdown

import scala.collection.mutable

/** Writes a syntax tree back to Markdown.
  *
  * ==What is promised, and what is not==
  *
  * §18.2 is precise about this, and the precision matters because the obvious expectation is
  * the wrong one:
  *
  *   - '''Promised:''' `decode(encode(document)) ≃ normalize(document)`. Write a tree, parse it
  *     again, and you get the same tree back up to normalisation.
  *   - '''Not promised:''' `encode(decode(source)) == source`. The writer picks a canonical
  *     syntax. A heading written `Titel` over `=====` comes back as `# Titel`; a list written
  *     with `+` comes back with `-`. §18.2 says so outright: "`encode(decode(source)) == source`
  *     ist kein Ziel."
  *
  * That is not laziness. Preserving the input spelling would mean carrying it through the
  * document model, and the document model is the editor's state -- it holds what the text
  * '''means''', not how someone typed it. The one exception is [[HeadingStyle]], which the
  * syntax tree keeps because it costs a field and cannot be recovered later.
  *
  * ==Escaping==
  *
  * The writer escapes what would otherwise be read back as syntax, and nothing else. §18.2:
  * "Dekodierung und erneutes Escaping ohne Syntaxinjektion." Over-escaping is safe but makes
  * the output unreadable, so the rule is positional: a `#` is escaped at the start of a line
  * and left alone in the middle of one.
  */
object MarkdownWriter:

  /** Writes a tree. Always succeeds -- every syntax tree has a Markdown spelling. */
  def write(document: MarkdownDocument): String =
    val out = new Sink
    writeBlocks(document.children, out)
    out.result

  /** Writes the inline content of one block. For a caller that has only that. */
  def writeInlines(inlines: Vector[MarkdownInline]): String =
    val out = new StringBuilder
    writeInline(inlines, out)
    out.toString

  // -----------------------------------------------------------------------------------------
  // Bloecke
  // -----------------------------------------------------------------------------------------

  private def writeBlocks(blocks: Vector[MarkdownBlock], out: Sink): Unit =
    blocks.zipWithIndex.foreach { (block, index) =>
      if index > 0 then out.blankLine()
      writeBlock(block, out)
    }

  private def writeBlock(block: MarkdownBlock, out: Sink): Unit = block match
    case document: MarkdownDocument =>
      writeBlocks(document.children, out)

    case MarkdownBlock.Paragraph(_, _, inlines) =>
      out.line(escapeLeading(writeInlines(inlines)))

    case MarkdownBlock.Heading(_, _, level, style, inlines) =>
      val text = writeInlines(inlines)
      // Setext kann nur eins und zwei, und nur ohne Umbruch im Text. Alles andere wird ATX --
      // eine Ueberschrift auszugeben, die sich nicht zurueckliest, waere schlimmer als eine,
      // die anders aussieht als das Original.
      if style == HeadingStyle.Setext && level <= 2 && !text.contains('\n') && text.nonEmpty then
        out.line(text)
        out.line(if level == 1 then "=" * math.max(3, text.length) else "-" * math.max(3, text.length))
      else out.line(s"${"#" * level} $text".stripSuffix(" "))

    case MarkdownBlock.CodeBlock(_, _, literal, fence) =>
      fence match
        case Some(Fence(char, _, info)) =>
          // Der Zaun muss laenger sein als jeder Lauf desselben Zeichens im Inhalt, sonst
          // schliesst der Inhalt ihn selbst. §18.2: "sichere Fence-Laenge beim Export."
          val needed = math.max(3, longestRun(literal, char) + 1)
          out.line(s"${char.toString * needed}${escapeInfo(info)}")
          literal.stripSuffix("\n").split("\n", -1).foreach(out.line)
          out.line(char.toString * needed)
        case None =>
          // Eingerueckter Code kann keinen Info-String tragen; ein Zaun waere hier eine
          // Aenderung der Bedeutung, keine der Schreibweise.
          literal.stripSuffix("\n").split("\n", -1).foreach(line => out.line(s"    $line"))

    case MarkdownBlock.HtmlBlock(_, _, literal) =>
      literal.split("\n", -1).foreach(out.line)

    case MarkdownBlock.ThematicBreak(_, _) =>
      out.line("---")

    case MarkdownBlock.BlockQuote(_, _, children) =>
      out.indented("> ", "> ") { writeBlocks(children, out) }

    case MarkdownBlock.MarkdownList(_, _, kind, tight, children) =>
      children.zipWithIndex.foreach { (item, index) =>
        if index > 0 && !tight then out.blankLine()
        val marker = kind match
          case ListKind.Bullet(char)              => s"$char "
          case ListKind.Ordered(start, delimiter) => s"${start + index}$delimiter "
        // Ein leerer Punkt schreibt keine Zeile -- und damit auch keinen Marker, wenn man ihn
        // nur als Praefix behandelt. `- foo`, `-`, `- bar` verlor so den mittleren Punkt
        // spurlos. Er bekommt seine Zeile ausdruecklich.
        if item.children.isEmpty then out.line(marker.stripSuffix(" "))
        else out.indented(marker, " " * marker.length) { writeBlock(item, out) }
      }

    case MarkdownBlock.ListItem(_, _, children) =>
      writeBlocks(children, out)

  // -----------------------------------------------------------------------------------------
  // Inlines
  // -----------------------------------------------------------------------------------------

  private def writeInline(inlines: Vector[MarkdownInline], out: StringBuilder): Unit =
    inlines.foreach {
      case MarkdownInline.Text(_, _, value) => out.append(escapeText(value))

      case MarkdownInline.Code(_, _, literal) =>
        // Dieselbe Ueberlegung wie beim Zaun, eine Ebene tiefer: der Lauf muss laenger sein als
        // jeder im Inhalt, und ein Inhalt mit Backtick am Rand braucht Randleerzeichen.
        val ticks   = "`" * (longestRun(literal, '`') + 1)
        val padding = if literal.startsWith("`") || literal.endsWith("`") then " " else ""
        out.append(ticks).append(padding).append(literal).append(padding).append(ticks): Unit

      case MarkdownInline.SoftBreak(_, _) => out.append('\n'): Unit
      // Rueckstrich statt zwei Leerzeichen: unsichtbarer Leerraum am Zeilenende ueberlebt
      // keinen Editor, der ihn trimmt, und §18.2 verlangt den Unterschied erhalten.
      case MarkdownInline.HardBreak(_, _) => out.append("\\\n"): Unit

      case MarkdownInline.HtmlInline(_, _, literal) => out.append(literal): Unit

      case MarkdownInline.Emphasis(_, _, children) =>
        out.append('*'): Unit
        writeInline(children, out)
        out.append('*'): Unit

      case MarkdownInline.Strong(_, _, children) =>
        out.append("**"): Unit
        writeInline(children, out)
        out.append("**"): Unit

      case MarkdownInline.Link(_, _, destination, title, children) =>
        out.append('['): Unit
        writeInline(children, out)
        out.append("](").append(writeDestination(destination)).append(titleOf(title)).append(')'): Unit

      case MarkdownInline.Image(_, _, destination, title, children) =>
        out.append("!["): Unit
        writeInline(children, out)
        out.append("](").append(writeDestination(destination)).append(titleOf(title)).append(')'): Unit
    }

  private def titleOf(title: Option[String]): String =
    title.map(value => s""" "${value.replace("\\", "\\\\").replace("\"", "\\\"")}"""").getOrElse("")

  /** A destination goes in pointy brackets when it holds anything that would end it. */
  private def writeDestination(target: String): String =
    if target.isEmpty then "<>"
    else if target.exists(c => c == ' ' || c == '(' || c == ')' || c == '<' || c == '>' || c < ' ')
    then s"<${target.replace("<", "\\<").replace(">", "\\>")}>"
    else target

  // -----------------------------------------------------------------------------------------
  // Escaping
  // -----------------------------------------------------------------------------------------

  /** Characters that would be read back as inline syntax. */
  private val InlineSpecial = Set('\\', '`', '*', '_', '[', ']', '<', '&')

  private def escapeText(text: String): String =
    val out = new StringBuilder(text.length)
    text.foreach { c =>
      // `!` nur vor `[`, sonst waere jedes Ausrufezeichen im Text ein Rueckstrich.
      if InlineSpecial.contains(c) then out.append('\\')
      out.append(c)
    }
    // Ein `!` ist nur vor einer Klammer gefaehrlich, und die ist an dieser Stelle bereits
    // maskiert -- `!\[` liest sich als Ausrufezeichen und literale Klammer.
    out.toString

  /** Characters that start a block, escaped only where they would. */
  private def escapeLeading(text: String): String =
    text
      .split("\n", -1)
      .map { line =>
        val trimmed = line.dropWhile(_ == ' ')
        val indent  = line.length - trimmed.length
        val escaped =
          if trimmed.isEmpty then trimmed
          else
            trimmed.headOption match
              case Some('#') | Some('>') | Some('-') | Some('+') | Some('=') | Some('~') =>
                s"\\$trimmed"
              case Some(digit) if digit.isDigit =>
                // `1. ` am Zeilenanfang waere ein Listenpunkt; `1.5` ist es nicht.
                val marker = trimmed.takeWhile(_.isDigit)
                if trimmed.length > marker.length &&
                  (trimmed.charAt(marker.length) == '.' || trimmed.charAt(marker.length) == ')')
                then s"$marker\\${trimmed.substring(marker.length)}"
                else trimmed
              case _ => trimmed
        " " * indent + escaped
      }
      .mkString("\n")

  /** An info string may hold no backtick and no newline. */
  private def escapeInfo(info: String): String =
    info.replace("`", "\\`").replace("\n", " ")

  private def longestRun(text: String, char: Char): Int =
    var best    = 0
    var current = 0
    text.foreach { c =>
      if c == char then
        current += 1
        if current > best then best = current
      else current = 0
    }
    best

  /** The output buffer, with the block-prefix stack containers need.
    *
    * A block quote inside a list item is `- > text`, and the prefix of the '''first''' line
    * differs from the rest. Getting that wrong produces Markdown that parses back into a
    * different tree, which is exactly what the round-trip promise forbids.
    */
  private final class Sink:
    private val buffer   = new StringBuilder
    private val prefixes = mutable.ArrayBuffer.empty[Level]

    private final class Level(val first: String, val rest: String):
      var used = false

    def line(text: String): Unit =
      // Jede Ebene entscheidet selbst, ob sie ihr Marker- oder ihr Fortsetzungspraefix schreibt.
      // Ein einziges "erste Zeile"-Flag reichte nicht: `- > Text` hat zwei Ebenen, die beide
      // ihre erste Zeile schreiben, und danach schreibt jede etwas anderes.
      val prefix = prefixes
        .map { level =>
          val value = if level.used then level.rest else level.first
          level.used = true
          value
        }
        .mkString

      // Ein Praefix ohne Inhalt traegt keinen Leerraum ans Zeilenende -- `>` und nicht `> `.
      val rendered = if text.isEmpty then prefix.replaceAll("\\s+$", "") else prefix + text
      buffer.append(rendered).append('\n'): Unit

    def blankLine(): Unit = line("")

    def indented(first: String, rest: String)(body: => Unit): Unit =
      prefixes += new Level(first, rest)
      body
      prefixes.remove(prefixes.length - 1): Unit

    def result: String = buffer.toString
