package ember.editor.markdown

/** The conformance suites HTML renderer. '''Test scope only.'''
  *
  * ==Why this exists==
  *
  * The conformance suite states its expectation as HTML, so comparing against it needs an HTML
  * renderer -- and this module has no such thing on purpose. §6 puts HTML in `ember-html`, and P18s
  * writer writes Markdown.
  *
  * So this exists here, in the test scope, doing exactly one job: turning a syntax tree into the
  * HTML the specification expects, so that the count of matching examples is a measurement and not
  * an impression. §18.1 asks for that measurement.
  *
  * ==Why it is not in the main sources==
  *
  * Because it would be a second HTML writer. A renderer that exists only to make a test comparable
  * is a test fixture, and putting it in `src/main` would make it something an application could
  * find and mistake for the real one.
  *
  * The whitespace rules follow `lib/render/html.js` of commonmark.js: `cr` writes a newline unless
  * the last thing written was one. Getting that wrong makes every comparison fail for a reason that
  * has nothing to do with the parser.
  */
object ConformanceHtml:

  def render(block: MarkdownBlock): String =
    val out = new Builder
    write(block, out, tightItem = false)
    out.result

  private def write(block: MarkdownBlock, out: Builder, tightItem: Boolean): Unit =
    block match
      case document: MarkdownDocument =>
        document.children.foreach(write(_, out, tightItem = false))

      case MarkdownBlock.Paragraph(_, _, inlines) =>
        // In einer engen Liste hat ein Absatz keine Huelle -- das ist der ganze sichtbare
        // Unterschied zwischen eng und weit (§18.2).
        if tightItem then out.literal(inlineHtml(inlines))
        else
          out.newline()
          out.literal("<p>")
          out.literal(inlineHtml(inlines))
          out.literal("</p>")
          out.newline()

      case MarkdownBlock.Heading(_, _, level, _, inlines) =>
        out.newline()
        out.literal(s"<h$level>")
        out.literal(inlineHtml(inlines))
        out.literal(s"</h$level>")
        out.newline()

      case MarkdownBlock.CodeBlock(_, _, literal, fence) =>
        val language  = fence.map(_.info).getOrElse("").split("\\s+").headOption.getOrElse("")
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

  /** Inline content as HTML, following `lib/render/html.js`.
    *
    * Not called `inline` -- that is a soft keyword in Scala 3 and the call sites stop compiling in
    * a way that names everything except the cause.
    */
  private def inlineHtml(inlines: Vector[MarkdownInline]): String =
    inlines.map {
      case MarkdownInline.Text(_, _, value)         => escape(value)
      case MarkdownInline.Code(_, _, literal)       => s"<code>${escape(literal)}</code>"
      case MarkdownInline.SoftBreak(_, _)           => "\n"
      case MarkdownInline.HardBreak(_, _)           => "<br />\n"
      case MarkdownInline.HtmlInline(_, _, literal) => literal
      case MarkdownInline.Emphasis(_, _, kids)      => s"<em>${inlineHtml(kids)}</em>"
      case MarkdownInline.Strong(_, _, kids)        => s"<strong>${inlineHtml(kids)}</strong>"

      case MarkdownInline.Link(_, _, destination, title, kids) =>
        val titleAttribute = title.map(value => s""" title="${escape(value)}"""").getOrElse("")
        s"""<a href="${escape(destination)}"$titleAttribute>${inlineHtml(kids)}</a>"""

      case MarkdownInline.Image(_, _, destination, title, kids) =>
        // Der Alt-Text ist der reine Text der Kinder -- ein `<em>` darin verschwindet, weil ein
        // Attribut kein Markup traegt. Genau so macht es die Vorlage.
        val titleAttribute = title.map(value => s""" title="${escape(value)}"""").getOrElse("")
        val alt            = escape(MarkdownInline.plainText(kids))
        s"""<img src="${escape(destination)}" alt="$alt"$titleAttribute />"""
    }.mkString

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
    private val buffer         = new StringBuilder
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
