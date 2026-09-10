package ember.editor.markdown

/** A block-only HTML renderer. '''Test scope only.'''
  *
  * ==Why this exists==
  *
  * The conformance suite states its expectation as HTML. To compare against it at all, the
  * block tree has to become HTML -- and P17 has no writer yet (that is P18, and it writes
  * Markdown, not HTML).
  *
  * So this renders exactly what a block parser knows and puts the '''unparsed inline source'''
  * where inline content would go, escaped as text. Every example whose inline content is plain
  * text then matches the specification byte for byte; every example that needs emphasis, a
  * link, a code span, an entity or a backslash escape does not.
  *
  * That is the point. The count of matching examples is a measurement of how much of CommonMark
  * this module actually implements, and §18.1 asks for exactly that: "Bis die Konformitaetsfaelle
  * vollstaendig bestanden sind, wird nur die tatsaechlich getestete Teilmenge beworben." The
  * number lives in [[CommonMarkBlockSpec]] and will move in P18.
  *
  * ==Why it is not in the main sources==
  *
  * Because it would be a second HTML writer, and §6 puts HTML in `ember-html`. A renderer that
  * exists only to make a test comparable is a test fixture, and putting it in `src/main` would
  * make it something an application could find and mistake for the real one.
  *
  * The whitespace rules follow `lib/render/html.js` of commonmark.js: `cr` writes a newline
  * unless the last thing written was one. Getting that wrong makes every comparison fail for a
  * reason that has nothing to do with the parser.
  */
object BlockHtml:

  def render(block: MarkdownBlock): String =
    val out = new Builder
    write(block, out, tightItem = false)
    out.result

  private def write(block: MarkdownBlock, out: Builder, tightItem: Boolean): Unit =
    block match
      case document: MarkdownDocument =>
        document.children.foreach(write(_, out, tightItem = false))

      case MarkdownBlock.Paragraph(_, _, source) =>
        // In einer engen Liste hat ein Absatz keine Huelle -- das ist der ganze sichtbare
        // Unterschied zwischen eng und weit (§18.2).
        if tightItem then out.literal(escape(source))
        else
          out.newline()
          out.literal("<p>")
          out.literal(escape(source))
          out.literal("</p>")
          out.newline()

      case MarkdownBlock.Heading(_, _, level, _, source) =>
        out.newline()
        out.literal(s"<h$level>")
        out.literal(escape(source))
        out.literal(s"</h$level>")
        out.newline()

      case MarkdownBlock.CodeBlock(_, _, literal, fence) =>
        val language = fence.map(_.info).getOrElse("").split("\\s+").headOption.getOrElse("")
        val attribute =
          if language.isEmpty then ""
          else
            val escaped = escape(language)
            val name    = if escaped.startsWith("language-") then escaped else s"language-$escaped"
            s""" class="$name""""
        out.newline()
        out.literal("<pre>")
        out.literal(s"<code$attribute>")
        out.literal(escape(literal))
        out.literal("</code>")
        out.literal("</pre>")
        out.newline()

      case MarkdownBlock.HtmlBlock(_, _, literal) =>
        // Ungefiltert, weil die Suite es so erwartet. Was `RawHtmlPolicy.AsText` daraus macht,
        // entscheidet der Dokumentadapter in P18 -- hier wird der Parser geprueft, nicht die
        // Sicherheitsentscheidung.
        out.newline()
        out.literal(literal)
        out.newline()

      case MarkdownBlock.ThematicBreak(_, _) =>
        out.newline()
        out.literal("<hr />")
        out.newline()

      case MarkdownBlock.BlockQuote(_, _, children) =>
        out.newline()
        out.literal("<blockquote>")
        out.newline()
        children.foreach(write(_, out, tightItem = false))
        out.newline()
        out.literal("</blockquote>")
        out.newline()

      case MarkdownBlock.MarkdownList(_, _, kind, tight, children) =>
        val (tag, start) = kind match
          case ListKind.Bullet(_)          => ("ul", "")
          case ListKind.Ordered(1, _)      => ("ol", "")
          case ListKind.Ordered(number, _) => ("ol", s""" start="$number"""")
        out.newline()
        out.literal(s"<$tag$start>")
        out.newline()
        children.foreach(write(_, out, tight))
        out.newline()
        out.literal(s"</$tag>")
        out.newline()

      case MarkdownBlock.ListItem(_, _, children) =>
        out.literal("<li>")
        children.foreach(write(_, out, tightItem))
        out.literal("</li>")
        out.newline()

  /** `escapeXml` from `lib/common.js`: exactly these four, and nothing else. */
  private def escape(text: String): String =
    val out = new StringBuilder(text.length)
    text.foreach {
      case '&' => out.append("&amp;")
      case '<' => out.append("&lt;")
      case '>' => out.append("&gt;")
      case '"' => out.append("&quot;")
      case c   => out.append(c)
    }
    out.toString

  /** The buffer, with the reference's `cr` semantics. */
  private final class Builder:
    private val buffer = new StringBuilder
    private var lastWasNewline = true

    def literal(text: String): Unit =
      if text.nonEmpty then
        buffer.append(text)
        lastWasNewline = text.endsWith("\n")

    def newline(): Unit =
      if !lastWasNewline then
        buffer.append('\n')
        lastWasNewline = true

    def result: String = buffer.toString
