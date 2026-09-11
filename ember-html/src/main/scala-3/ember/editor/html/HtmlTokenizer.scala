package ember.editor.html

import scala.collection.mutable

/** One piece of a fragment, as the tokenizer found it. */
enum HtmlToken:

  case Text(value: String)

  case Open(tag: String, attributes: Vector[(String, String)], selfClosing: Boolean)

  case Close(tag: String)

  /** Kept as a token rather than dropped, so the parser decides. A conditional comment from Word
    * carries markup, and the decision to throw it away is the policy's, not the scanner's.
    */
  case Comment(value: String)

  case Doctype(value: String)

/** Turns a fragment of HTML into tokens.
  *
  * ==What this is not==
  *
  * §19.1 is explicit: the importer "beansprucht keine vollstaendige HTML5-Tree-Construction". It
  * is not a browser. It does not implement the insertion modes, the foster parenting, the
  * adoption agency algorithm or the fifteen-odd special cases for tables.
  *
  * What it is: a scanner for the fragment shapes that clipboards actually produce, written so
  * that "Tokenisierung, Entities, verschachtelte erlaubte Tags, Void-Elemente und Fehlformungen
  * werden einzeln implementiert" is literally true -- each of those is a separate, separately
  * tested piece.
  *
  * ==Why not `innerHTML` and then read the DOM==
  *
  * §19.1 forbids it twice, and the second time by name: "Niemals Inhalte erst in den lebenden DOM
  * einsetzen und anschliessend bereinigen." Assigning `innerHTML` runs `<img onerror>`, fetches
  * URLs and executes whatever a paste brought with it -- sanitising afterwards is sanitising a
  * page that has already done the damage. And it would not work on a server at all, where §19.1
  * requires the same result.
  */
object HtmlTokenizer:

  /** Elements whose content is text, not markup.
    *
    * Everything up to the matching close tag is one text token, entities and all. That is what
    * makes `<script>if (a < b)</script>` scan without the `<` starting a tag -- and what makes a
    * `<style>` block droppable in one piece rather than as a shower of fake elements.
    */
  private val rawText = Set("script", "style", "textarea", "title", "xmp", "noscript", "noembed")

  def tokenize(html: String, entities: HtmlEntities = HtmlEntities.common): Vector[HtmlToken] =
    val tokens = mutable.ArrayBuffer.empty[HtmlToken]
    val text   = new StringBuilder
    var index  = 0

    def flushText(): Unit =
      if text.nonEmpty then
        tokens += HtmlToken.Text(HtmlEntities.decode(text.result(), entities))
        text.clear()

    while index < html.length do
      val char = html.charAt(index)

      if char != '<' then
        text.append(char)
        index += 1
      else if index + 1 >= html.length then
        // A trailing `<` is text. The alternative -- discarding it -- loses a character from a
        // paste for no benefit.
        text.append(char)
        index += 1
      else
        val next = html.charAt(index + 1)

        if next == '!' then
          flushText()
          index = readBang(html, index, tokens)
        else if next == '/' then
          // Looked at before anything is emitted: a `</` that is not followed by a name is text,
          // like `a </ b`, and flushing first would put the close tag ahead of it.
          if index + 2 < html.length && isNameStart(html.charAt(index + 2)) then
            flushText()
            index = readCloseTag(html, index, tokens)
          else
            text.append(char)
            index += 1
        else if isNameStart(next) then
          flushText()
          val (end, tag) = readOpenTag(html, index, tokens, entities)
          index = end
          tag.filter(rawText.contains).foreach { name =>
            index = readRawText(html, index, name, tokens)
          }
        else
          text.append(char)
          index += 1

    flushText()
    tokens.toVector

  // -----------------------------------------------------------------------------------------
  // Die einzelnen Formen
  // -----------------------------------------------------------------------------------------

  /** `<!-- -->`, `<!doctype>` and the rest of the `<!` family. */
  private def readBang(
      html: String,
      start: Int,
      tokens: mutable.ArrayBuffer[HtmlToken]
  ): Int =
    if html.startsWith("<!--", start) then
      val end = html.indexOf("-->", start + 4)
      if end < 0 then
        tokens += HtmlToken.Comment(html.substring(start + 4))
        html.length
      else
        tokens += HtmlToken.Comment(html.substring(start + 4, end))
        end + 3
    else
      val end = html.indexOf('>', start)
      val body = if end < 0 then html.substring(start + 2) else html.substring(start + 2, end)
      tokens += HtmlToken.Doctype(body)
      if end < 0 then html.length else end + 1

  private def readCloseTag(
      html: String,
      start: Int,
      tokens: mutable.ArrayBuffer[HtmlToken]
  ): Int =
    var index = start + 2
    if index >= html.length || !isNameStart(html.charAt(index)) then start
    else
      val from = index
      while index < html.length && isNameChar(html.charAt(index)) do index += 1
      val tag = html.substring(from, index).toLowerCase

      // Anything between the name and `>` is ignored, which is what browsers do with
      // `</p foo="bar">`.
      val end = html.indexOf('>', index)
      tokens += HtmlToken.Close(tag)
      if end < 0 then html.length else end + 1

  private def readOpenTag(
      html: String,
      start: Int,
      tokens: mutable.ArrayBuffer[HtmlToken],
      entities: HtmlEntities
  ): (Int, Option[String]) =
    var index = start + 1
    val from  = index
    while index < html.length && isNameChar(html.charAt(index)) do index += 1
    val tag = html.substring(from, index).toLowerCase

    val attributes  = mutable.ArrayBuffer.empty[(String, String)]
    var selfClosing = false
    var done        = false

    while !done && index < html.length do
      index = skipSpace(html, index)
      if index >= html.length then done = true
      else
        val char = html.charAt(index)
        if char == '>' then
          index += 1
          done = true
        else if char == '/' then
          selfClosing = true
          index += 1
        else if isNameStart(char) || char == '_' || char == ':' then
          val (next, name, value) = readAttribute(html, index, entities)
          // First wins. Duplicate attributes are a Word speciality, and the browsers agree on
          // keeping the first.
          if !attributes.exists(_._1 == name) then attributes += (name -> value)
          index = next
        else
          // A stray character in a tag -- `<p ="x">`. Skipping it keeps the tag rather than
          // turning the rest of the document into text.
          index += 1

    tokens += HtmlToken.Open(tag, attributes.toVector, selfClosing)
    (index, Option.when(!selfClosing)(tag))

  private def readAttribute(
      html: String,
      start: Int,
      entities: HtmlEntities
  ): (Int, String, String) =
    var index = start
    while index < html.length && isAttributeNameChar(html.charAt(index)) do index += 1
    val name = html.substring(start, index).toLowerCase

    val afterName = skipSpace(html, index)
    if afterName >= html.length || html.charAt(afterName) != '=' then
      // A bare attribute: `<input disabled>`. Its value is its name in HTML, but an empty string
      // is what every consumer here wants.
      (index, name, "")
    else
      var cursor = skipSpace(html, afterName + 1)
      if cursor >= html.length then (cursor, name, "")
      else
        val quote = html.charAt(cursor)
        if quote == '"' || quote == '\'' then
          val end   = html.indexOf(quote.toInt, cursor + 1)
          val value = if end < 0 then html.substring(cursor + 1) else html.substring(cursor + 1, end)
          (if end < 0 then html.length else end + 1, name, HtmlEntities.decode(value, entities))
        else
          val from = cursor
          while cursor < html.length && !isSpace(html.charAt(cursor)) && html.charAt(cursor) != '>'
          do cursor += 1
          (cursor, name, HtmlEntities.decode(html.substring(from, cursor), entities))

  /** Everything up to the matching close tag, verbatim. */
  private def readRawText(
      html: String,
      start: Int,
      tag: String,
      tokens: mutable.ArrayBuffer[HtmlToken]
  ): Int =
    val closing = s"</$tag"
    val end     = indexOfIgnoreCase(html, closing, start)

    if end < 0 then
      if start < html.length then tokens += HtmlToken.Text(html.substring(start))
      html.length
    else
      if end > start then tokens += HtmlToken.Text(html.substring(start, end))
      val after = html.indexOf('>', end)
      tokens += HtmlToken.Close(tag)
      if after < 0 then html.length else after + 1

  // -----------------------------------------------------------------------------------------
  // Zeichenklassen
  // -----------------------------------------------------------------------------------------

  private def isSpace(char: Char): Boolean =
    char == ' ' || char == '\t' || char == '\n' || char == '\r' || char == '\f'

  private def skipSpace(html: String, from: Int): Int =
    var index = from
    while index < html.length && isSpace(html.charAt(index)) do index += 1
    index

  private def isNameStart(char: Char): Boolean = char.isLetter

  private def isNameChar(char: Char): Boolean =
    char.isLetterOrDigit || char == '-' || char == '_' || char == ':' || char == '.'

  private def isAttributeNameChar(char: Char): Boolean =
    !isSpace(char) && char != '=' && char != '>' && char != '/' && char != '"' && char != '\''

  private def indexOfIgnoreCase(html: String, needle: String, from: Int): Int =
    var index = from
    val limit = html.length - needle.length
    var found = -1
    while found < 0 && index <= limit do
      if html.regionMatches(true, index, needle, 0, needle.length) then found = index
      index += 1
    found
