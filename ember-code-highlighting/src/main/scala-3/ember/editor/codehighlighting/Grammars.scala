package ember.editor.codehighlighting

import TokenKind.*

/** The languages the module ships with.
  *
  * ==What these grammars are and are not==
  *
  * They colour; they do not validate. A grammar that knows `given` is a keyword does not know that
  * `given` is also a valid identifier in Scala 3 outside a definition, and it does not need to --
  * an author who names a variable `given` gets it coloured as a keyword, which is at worst a hint.
  * What they must never do is lose track of a string or a comment, because that recolours every
  * line after it. The rules for those are the careful part, and the tests hold them to it.
  *
  * Each grammar is a `lazy val`: HTML embeds CSS and JavaScript, and an application that never
  * highlights HTML should not build either.
  */
object Grammars:

  private val identifier = """[A-Za-z_$][A-Za-z0-9_$]*"""

  // ---------------------------------------------------------------------------------------
  // Scala
  // ---------------------------------------------------------------------------------------

  private val scalaKeywords = Seq(
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "derives",
    "do",
    "else",
    "end",
    "enum",
    "export",
    "extends",
    "extension",
    "final",
    "finally",
    "for",
    "forSome",
    "given",
    "if",
    "implicit",
    "import",
    "infix",
    "inline",
    "lazy",
    "match",
    "new",
    "object",
    "opaque",
    "open",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "then",
    "this",
    "throw",
    "trait",
    "transparent",
    "try",
    "type",
    "using",
    "val",
    "var",
    "while",
    "with",
    "yield"
  )

  private val scalaNumber =
    """(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|(?:\d[\d_]*(?:\.\d[\d_]*)?|\.\d[\d_]*)(?:[eE][+-]?\d+)?)[lLfFdD]?"""

  private val unicodeEscape = """\\(?:u[0-9a-fA-F]{4}|.)"""

  lazy val scala: Grammar = Grammar("scala")(
    state("root")(
      Rule.skip("""\s+"""),
      Rule.token("//.*", Comment),
      Rule.token("""/\*""", Comment).push("blockComment"),
      // An interpolator is a word glued to its quote: `s"`, `f"""`, `sql"`.
      Rule
        .token("[a-z][A-Za-z0-9_]*\"\"\"", String)
        .push("interpolatedTriple")
        .when(Rule.atWordStart),
      Rule.token("\"\"\"", String).push("triple"),
      Rule.token("[a-z][A-Za-z0-9_]*\"", String).push("interpolated").when(Rule.atWordStart),
      Rule.token("\"", String).push("string"),
      Rule.token("""'(?:[^'\\]|\\(?:u[0-9a-fA-F]{4}|.))'""", String),
      Rule.token("""@[A-Za-z_][A-Za-z0-9_.]*""", Meta),
      Rule
        .groups(
          """(def|given)(\s+)([A-Za-z_][A-Za-z0-9_]*|`[^`]+`)""",
          Some(Keyword),
          None,
          Some(Function)
        )
        .when(Rule.atWordStart),
      Rule
        .groups(
          """(class|trait|object|enum|type)(\s+)([A-Za-z_][A-Za-z0-9_]*)""",
          Some(Keyword),
          None,
          Some(Type)
        )
        .when(Rule.atWordStart),
      Rule.words(Keyword, identifier, scalaKeywords*).when(Rule.atWordStart),
      Rule.words(Constant, identifier, "true", "false", "null").when(Rule.atWordStart),
      Rule.token("""[A-Z][A-Za-z0-9_$]*""", Type).when(Rule.atWordStart),
      Rule.token("""[a-z_][A-Za-z0-9_$]*(?=\()""", Function).when(Rule.atWordStart),
      Rule.skip(identifier),
      Rule.skip("`[^`]+`"),
      Rule.token(scalaNumber, Number).when(Rule.atWordStart),
      Rule.token("""[=<>!&|+\-*/%^~:#?\\@]+""", Operator),
      Rule.token("""[{}()\[\];,.]""", Punctuation)
    ),
    // Scala block comments nest, and a grammar that did not count them would end the comment at
    // the first `*/` and colour the rest of it as code.
    state("blockComment")(
      Rule.token("""\*/""", Comment).pop,
      Rule.token("""/\*""", Comment).push("blockComment"),
      Rule.token("""[^*/]+""", Comment),
      Rule.token("""[*/]""", Comment)
    ),
    state("string", lineBound = true)(
      Rule.token(unicodeEscape, Escape),
      Rule.token("\"", String).pop,
      Rule.token("""[^"\\]+""", String)
    ),
    state("interpolated", lineBound = true)(
      Rule.token("""\$\$""", Escape),
      Rule.token("""\$\{""", Punctuation).push("interpolation"),
      Rule.token("""\$[A-Za-z_][A-Za-z0-9_]*""", Variable),
      Rule.token(unicodeEscape, Escape),
      Rule.token("\"", String).pop,
      Rule.token("""[^"\\$]+""", String),
      Rule.token("""\$""", String)
    ),
    state("triple")(
      // `""""` ends with the last three quotes; the first one is content. Consuming all of them as
      // one token gives the same colour and needs no lookahead.
      Rule.token("\"\"\"+", String).pop,
      Rule.token("[^\"]+", String),
      Rule.token("\"", String)
    ),
    state("interpolatedTriple")(
      Rule.token("""\$\$""", Escape),
      Rule.token("""\$\{""", Punctuation).push("interpolation"),
      Rule.token("""\$[A-Za-z_][A-Za-z0-9_]*""", Variable),
      Rule.token("\"\"\"+", String).pop,
      Rule.token("[^\"$]+", String),
      Rule.token("[\"$]", String)
    ),
    state("interpolation")(
      Rule.token("""\}""", Punctuation).pop,
      Rule.token("""\{""", Punctuation).push("braces"),
      Include("root")
    ),
    state("braces")(
      Rule.token("""\}""", Punctuation).pop,
      Rule.token("""\{""", Punctuation).push("braces"),
      Include("root")
    )
  )

  // ---------------------------------------------------------------------------------------
  // JavaScript and TypeScript
  // ---------------------------------------------------------------------------------------

  private val javascriptKeywords = Seq(
    "as",
    "async",
    "await",
    "break",
    "case",
    "catch",
    "class",
    "const",
    "continue",
    "debugger",
    "default",
    "delete",
    "do",
    "else",
    "export",
    "extends",
    "finally",
    "for",
    "from",
    "function",
    "get",
    "if",
    "import",
    "in",
    "instanceof",
    "let",
    "new",
    "of",
    "return",
    "set",
    "static",
    "super",
    "switch",
    "this",
    "throw",
    "try",
    "typeof",
    "var",
    "void",
    "while",
    "with",
    "yield"
  )

  private val typescriptKeywords = Seq(
    "abstract",
    "accessor",
    "asserts",
    "declare",
    "enum",
    "implements",
    "infer",
    "interface",
    "is",
    "keyof",
    "module",
    "namespace",
    "override",
    "private",
    "protected",
    "public",
    "readonly",
    "satisfies",
    "type",
    "unique"
  )

  private val typescriptTypes =
    Seq("any", "bigint", "boolean", "never", "number", "object", "string", "symbol", "unknown")

  private val javascriptNumber =
    """(?:0[xX][\da-fA-F_]+|0[bB][01_]+|0[oO][0-7_]+|(?:\d[\d_]*(?:\.[\d_]*)?|\.\d[\d_]*)(?:[eE][+-]?\d+)?)n?"""

  /** Words after which a `/` starts a regular expression rather than a division. */
  private val regexAfterWords =
    Set(
      "return",
      "typeof",
      "instanceof",
      "in",
      "of",
      "new",
      "delete",
      "void",
      "throw",
      "case",
      "do",
      "else",
      "yield",
      "await"
    )

  /** Whether `/` at this position starts a regular expression.
    *
    * The ambiguity is real and famous: `a / b / c` divides twice, `x = /b/` matches. A parser knows
    * from the grammar; a highlighter looks at what came before, the way every editor does. After a
    * value -- a name, a number, a closing bracket -- it is a division. After an operator, an
    * opening bracket or one of a few keywords, it is a pattern.
    */
  private val regexAllowed: Rule.Guard = (line, at) =>
    var index = at - 1
    while index >= 0 && line.charAt(index).isWhitespace do index -= 1
    if index < 0 then true
    else
      val previous = line.charAt(index)
      if "(,=:[!&|?{};+-*%<>~^".indexOf(previous.toInt) >= 0 then true
      else if previous.isLetterOrDigit || previous == '_' || previous == '$' then
        var begin = index
        while begin > 0 && {
            val before = line.charAt(begin - 1)
            before.isLetterOrDigit || before == '_' || before == '$'
          }
        do begin -= 1
        regexAfterWords.contains(line.substring(begin, index + 1))
      else false

  private def ecmascript(name: String, typescript: Boolean): Grammar =
    val declarations = if typescript then "class|interface|type|enum|namespace" else "class"
    val keywords     = javascriptKeywords ++ (if typescript then typescriptKeywords else Seq.empty)
    val types        =
      if typescript then
        Vector(Rule.words(Type, identifier, typescriptTypes*).when(Rule.atWordStart))
      else Vector.empty

    Grammar(name)(
      state("root")(
        (Vector[RuleEntry](
          Rule.skip("""\s+"""),
          Rule.token("//.*", Comment),
          Rule.token("""/\*""", Comment).push("blockComment"),
          Rule.token("`", String).push("template"),
          Rule.token("\"", String).push("double"),
          Rule.token("'", String).push("single"),
          Rule
            .token("""/(?![*/])(?:[^/\\\[]|\\.|\[(?:[^\]\\]|\\.)*\])+/[dgimsuyv]*""", String)
            .when(regexAllowed),
          Rule.token("""@[A-Za-z_$][\w$.]*""", Meta),
          Rule
            .groups(
              """(function)(\s*\*?\s*)([A-Za-z_$][\w$]*)""",
              Some(Keyword),
              None,
              Some(Function)
            )
            .when(Rule.atWordStart),
          Rule
            .groups(
              "(" + declarations + """)(\s+)([A-Za-z_$][\w$]*)""",
              Some(Keyword),
              None,
              Some(Type)
            )
            .when(Rule.atWordStart),
          Rule.words(Keyword, identifier, keywords*).when(Rule.atWordStart),
          Rule
            .words(Constant, identifier, "true", "false", "null", "undefined", "NaN", "Infinity")
            .when(Rule.atWordStart)
        ) ++ types ++ Vector[RuleEntry](
          Rule.token("""[A-Z][\w$]*""", Type).when(Rule.atWordStart),
          Rule.token("""[A-Za-z_$][\w$]*(?=\s*\()""", Function).when(Rule.atWordStart),
          Rule.skip("""#?[A-Za-z_$][\w$]*"""),
          Rule.token(javascriptNumber, Number).when(Rule.atWordStart),
          Rule.token("""\.\.\.|=>|[=<>!&|+\-*/%^~?:]+""", Operator),
          Rule.token("""[{}()\[\];,.]""", Punctuation)
        ))*
      ),
      state("blockComment")(
        Rule.token("""\*/""", Comment).pop,
        Rule.token("""[^*]+""", Comment),
        Rule.token("""\*""", Comment)
      ),
      state("template")(
        Rule.token("""\\.""", Escape),
        Rule.token("""\$\{""", Punctuation).push("templateExpression"),
        Rule.token("`", String).pop,
        Rule.token("""[^`\\$]+""", String),
        Rule.token("""\$""", String)
      ),
      state("templateExpression")(
        Rule.token("""\}""", Punctuation).pop,
        Rule.token("""\{""", Punctuation).push("braces"),
        Include("root")
      ),
      state("braces")(
        Rule.token("""\}""", Punctuation).pop,
        Rule.token("""\{""", Punctuation).push("braces"),
        Include("root")
      ),
      state("double", lineBound = true)(
        Rule.token("""\\.""", Escape),
        Rule.token("\"", String).pop,
        Rule.token("""[^"\\]+""", String)
      ),
      state("single", lineBound = true)(
        Rule.token("""\\.""", Escape),
        Rule.token("'", String).pop,
        Rule.token("""[^'\\]+""", String)
      )
    )

  lazy val javascript: Grammar = ecmascript("javascript", typescript = false)

  lazy val typescript: Grammar = ecmascript("typescript", typescript = true)

  // ---------------------------------------------------------------------------------------
  // JSON
  // ---------------------------------------------------------------------------------------

  lazy val json: Grammar = Grammar("json")(
    state("root")(
      Rule.skip("""\s+"""),
      // Not JSON, but JSONC and every configuration file that calls itself JSON. Colouring a
      // comment costs nothing and misreading it as garbage would colour the rest of the line.
      Rule.token("//.*", Comment),
      Rule.token("""/\*""", Comment).push("comment"),
      Rule.token(""""(?:[^"\\]|\\.)*"(?=\s*:)""", Property),
      Rule.token("\"", String).push("string"),
      Rule.token("""-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?""", Number),
      Rule.words(Constant, "[A-Za-z]+", "true", "false", "null"),
      Rule.token("""[{}\[\],:]""", Punctuation)
    ),
    state("string", lineBound = true)(
      Rule.token(unicodeEscape, Escape),
      Rule.token("\"", String).pop,
      Rule.token("""[^"\\]+""", String)
    ),
    state("comment")(
      Rule.token("""\*/""", Comment).pop,
      Rule.token("""[^*]+""", Comment),
      Rule.token("""\*""", Comment)
    )
  )

  // ---------------------------------------------------------------------------------------
  // CSS
  // ---------------------------------------------------------------------------------------

  lazy val css: Grammar = Grammar("css")(
    state("root")(
      Rule.skip("""\s+"""),
      Include("comments"),
      Rule.token("""@[\w-]+""", Keyword),
      Rule.token("""\{""", Punctuation).push("block"),
      Include("strings"),
      Include("selector"),
      Rule.token("""[;}]""", Punctuation)
    ),
    state("comments")(Rule.token("""/\*""", Comment).push("comment")),
    state("comment")(
      Rule.token("""\*/""", Comment).pop,
      Rule.token("""[^*]+""", Comment),
      Rule.token("""\*""", Comment)
    ),
    state("strings")(
      Rule.token("\"", String).push("double"),
      Rule.token("'", String).push("single")
    ),
    state("double", lineBound = true)(
      Rule.token("""\\.""", Escape),
      Rule.token("\"", String).pop,
      Rule.token("""[^"\\]+""", String)
    ),
    state("single", lineBound = true)(
      Rule.token("""\\.""", Escape),
      Rule.token("'", String).pop,
      Rule.token("""[^'\\]+""", String)
    ),
    state("selector")(
      Rule.token("""\.[\w-]+""", Type),
      Rule.token("""#[\w-]+""", Variable),
      Rule.token("""::?[\w-]+""", Meta),
      Rule.token("""\[""", Punctuation).push("attributeSelector"),
      Rule.token("""[\w-]+""", Tag),
      Rule.token("""[,>+~*&]""", Operator),
      Rule.token("""[()]""", Punctuation)
    ),
    state("attributeSelector")(
      Rule.skip("""\s+"""),
      Rule.token("""\]""", Punctuation).pop,
      Rule.token("""[~|^$*]?=""", Operator),
      Include("strings"),
      Rule.token("""[\w-]+""", Attribute)
    ),
    state("block")(
      Rule.skip("""\s+"""),
      Include("comments"),
      Rule.token("""\}""", Punctuation).pop,
      Rule.token("""\{""", Punctuation).push("block"),
      // A name and a colon are a declaration only if no `{` follows before the value ends.
      // Otherwise it is a nested selector: `a:hover { … }` inside a block is not `a: hover`.
      Rule
        .groups(
          """(--[\w-]+)(\s*)(:)(?=[^;{}]*(?:;|\}|$))""",
          Some(Variable),
          None,
          Some(Punctuation)
        )
        .push("value"),
      Rule
        .groups(
          """([\w-]+)(\s*)(:)(?=[^;{}]*(?:;|\}|$))""",
          Some(Property),
          None,
          Some(Punctuation)
        )
        .push("value"),
      Rule.token("""@[\w-]+""", Keyword),
      Rule.token(";", Punctuation),
      Include("strings"),
      Include("selector")
    ),
    state("value")(
      Rule.skip("""\s+"""),
      Include("comments"),
      Rule.token(";", Punctuation).pop,
      // The value ends at the brace, which belongs to the block. Zero width: the block reads it.
      Rule.skip("""(?=\})""").pop,
      Rule.token("""!\s*important""", Keyword),
      Rule.token("""#[0-9a-fA-F]{3,8}(?![\w-])""", Number),
      Rule.groups(
        """(url)(\()([^)]*)(\))""",
        Some(Function),
        Some(Punctuation),
        Some(String),
        Some(Punctuation)
      ),
      Rule.token("""[\w-]+(?=\()""", Function),
      Rule.token("""--[\w-]+""", Variable),
      Rule.token("""[+-]?(?:\d+\.?\d*|\.\d+)(?:%|[A-Za-z]+)?""", Number),
      Include("strings"),
      Rule.token("""[\w-]+""", Constant),
      Rule.token("""[,/()*+]""", Punctuation)
    )
  )

  // ---------------------------------------------------------------------------------------
  // HTML and XML
  // ---------------------------------------------------------------------------------------

  lazy val html: Grammar = Grammar("html")(
    state("root")(
      Rule.token("<!--", Comment).push("comment"),
      Rule.token("""<!\[CDATA\[""", Meta).push("cdata"),
      Rule.token("""<![^>]*>""", Meta),
      Rule.token("""<\?[^>]*\?>""", Meta),
      // Tag names are case-insensitive in HTML. Spelled out rather than `(?i)`, which not every
      // regex engine accepts in the middle of a pattern.
      Rule
        .groups("""(<)([sS][cC][rR][iI][pP][tT])(?![\w:.-])""", Some(Punctuation), Some(Tag))
        .push("scriptTag"),
      Rule
        .groups("""(<)([sS][tT][yY][lL][eE])(?![\w:.-])""", Some(Punctuation), Some(Tag))
        .push("styleTag"),
      Rule.groups("""(</?)([A-Za-z][\w:.-]*)""", Some(Punctuation), Some(Tag)).push("tag"),
      Include("entity"),
      Rule.skip("""[^<&]+""")
    ),
    state("entity")(
      Rule.token("""&(?:#\d+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);""", Escape)
    ),
    state("comment")(
      Rule.token("-->", Comment).pop,
      Rule.token("""[^-]+""", Comment),
      Rule.token("-", Comment)
    ),
    state("cdata")(
      Rule.token("""\]\]>""", Meta).pop,
      Rule.token("""[^\]]+""", String),
      Rule.token("""\]""", String)
    ),
    state("attributes")(
      Rule.skip("""\s+"""),
      Rule.groups("""(=)(\s*)([^\s"'=<>`]+)""", Some(Operator), None, Some(String)),
      Rule.token("=", Operator),
      Rule.token("\"", String).push("doubleValue"),
      Rule.token("'", String).push("singleValue"),
      Rule.token("""[^\s/>"'=]+""", Attribute)
    ),
    state("tag")(
      Include("attributes"),
      Rule.token("""/?>""", Punctuation).pop
    ),
    // The content of `<script>` is a program and `<style>` a stylesheet, and the tag says which.
    // Both end at their closing tag and nowhere else -- which is also what HTML's tokenizer does,
    // so `"</script>"` inside a string ends the script here exactly as it would in a browser.
    state("scriptTag")(
      Include("attributes"),
      Rule.token("/>", Punctuation).pop,
      Rule.token(">", Punctuation).pop.embed(javascript, "</[sS][cC][rR][iI][pP][tT]")
    ),
    state("styleTag")(
      Include("attributes"),
      Rule.token("/>", Punctuation).pop,
      Rule.token(">", Punctuation).pop.embed(css, "</[sS][tT][yY][lL][eE]")
    ),
    state("doubleValue")(
      Rule.token("\"", String).pop,
      Include("entity"),
      Rule.token("""[^"&]+""", String),
      Rule.token("&", String)
    ),
    state("singleValue")(
      Rule.token("'", String).pop,
      Include("entity"),
      Rule.token("""[^'&]+""", String),
      Rule.token("&", String)
    )
  )

  // ---------------------------------------------------------------------------------------
  // Shell
  // ---------------------------------------------------------------------------------------

  private val shellKeywords = Seq(
    "if",
    "then",
    "else",
    "elif",
    "fi",
    "for",
    "while",
    "until",
    "do",
    "done",
    "case",
    "esac",
    "in",
    "function",
    "select",
    "time",
    "return",
    "break",
    "continue"
  )

  private val shellBuiltins = Seq(
    "alias",
    "cd",
    "declare",
    "echo",
    "eval",
    "exec",
    "exit",
    "export",
    "local",
    "printf",
    "pwd",
    "read",
    "readonly",
    "set",
    "shift",
    "source",
    "test",
    "trap",
    "typeset",
    "unset"
  )

  /** `#` starts a comment only at the start of a word: `$#` and `a#b` are not comments. */
  private val shellCommentStart: Rule.Guard = (line, at) =>
    at == 0 || {
      val previous = line.charAt(at - 1)
      previous.isWhitespace || previous == ';'
    }

  private val afterSpace: Rule.Guard = (line, at) => at == 0 || line.charAt(at - 1).isWhitespace

  lazy val shell: Grammar = Grammar("shell")(
    state("root")(
      Rule.skip("""\s+"""),
      Rule.token("#.*", Comment).when(shellCommentStart),
      Rule.token("\"", String).push("double"),
      Rule.token("'", String).push("single"),
      Include("expansions"),
      Rule.token("`", String).push("backtick"),
      Rule
        .groups("""([A-Za-z_][\w-]*)(\s*\(\s*\))""", Some(Function), Some(Punctuation))
        .when(Rule.atLineStart),
      Rule
        .groups("""(function)(\s+)([A-Za-z_][\w-]*)""", Some(Keyword), None, Some(Function))
        .when(Rule.atWordStart),
      Rule.words(Keyword, """[A-Za-z_][\w-]*""", shellKeywords*).when(Rule.atWordStart),
      Rule.words(Function, """[A-Za-z_][\w-]*""", shellBuiltins*).when(Rule.atWordStart),
      Rule
        .groups("""([A-Za-z_]\w*)(\+?=)""", Some(Variable), Some(Operator))
        .when(Rule.atWordStart),
      Rule.token("""--?[A-Za-z0-9][\w-]*""", Attribute).when(afterSpace),
      Rule.token("""\d*[<>]+&?\d*-?|\|\||&&|;;|[|&;!]""", Operator),
      Rule.token("""\d+(?![\w.])""", Number).when(Rule.atWordStart),
      Rule.token("""[{}()\[\]]""", Punctuation),
      Rule.skip("""[^\s"'`$#|&;<>(){}\[\]]+""")
    ),
    state("expansions")(
      Rule.token("""\$\{""", Variable).push("parameter"),
      Rule.token("""\$\(\(""", Punctuation).push("arithmetic"),
      Rule.token("""\$\(""", Punctuation).push("substitution"),
      Rule.token("""\$(?:[A-Za-z_]\w*|[0-9#?@*$!-])""", Variable)
    ),
    state("parameter")(
      Rule.token("""\}""", Variable).pop,
      Rule.token("""[^}]+""", Variable)
    ),
    state("arithmetic")(
      Rule.token("""\)\)""", Punctuation).pop,
      Include("root")
    ),
    state("substitution")(
      Rule.token("""\)""", Punctuation).pop,
      Rule.token("""\(""", Punctuation).push("substitution"),
      Include("root")
    ),
    state("backtick")(
      Rule.token("`", String).pop,
      Include("root")
    ),
    // Shell strings span lines, so neither of these is line-bound.
    state("double")(
      Rule.token("""\\.""", Escape),
      Rule.token("\"", String).pop,
      Include("expansions"),
      Rule.token("""[^"\\$]+""", String),
      Rule.token("""[\\$]""", String)
    ),
    state("single")(
      Rule.token("'", String).pop,
      Rule.token("[^']+", String)
    )
  )

  // ---------------------------------------------------------------------------------------
  // Markdown
  // ---------------------------------------------------------------------------------------

  /** Position zero of the line: block syntax in Markdown is decided by where a line begins. */
  private val lineBegin: Rule.Guard = (_, at) => at == 0

  /** A reading of Markdown for colour, line by line.
    *
    * Deliberately not `ember-markdown`. That parser is exact, and exactness is what a colour does
    * not need: whether `*a**b*` is emphasis under CommonMark's delimiter rules changes nothing an
    * author can see here, while re-parsing a whole block on every keystroke would. What this reads
    * is what a reader of the source sees -- headings, fences, markers, emphasis, code and links.
    */
  lazy val markdown: Grammar = markdownWith(() => HighlightLanguages.standard)

  /** The opener of a fence: indentation, the fence itself, and the first word of the info string.
    */
  private val fenceOpener =
    java.util.regex.Pattern.compile("""\s{0,3}(`{3,}|~{3,})\s*([^\s`{]*)""")

  /** Markdown whose fenced code is coloured in the language its info string names.
    *
    * The languages are a thunk: the standard registry contains this grammar itself, and a fence in
    * a Markdown block may well be Markdown again. A fence closes with the same character and at
    * least as many of them as opened it (CommonMark §4.5), so the end is chosen per opener. An
    * unknown language keeps the fence's content in one colour, as before.
    */
  def markdownWith(languages: () => HighlightLanguages): Grammar = Grammar("markdown")(
    state("root")(
      Rule
        .groups("""(\s{0,3})(`{3,}|~{3,})(.*)""", None, Some(Punctuation), Some(Meta))
        .push("fence")
        .embedMatched { text =>
          val opener = fenceOpener.matcher(text)
          if !opener.lookingAt() || opener.group(2).isEmpty then None
          else
            val fence  = opener.group(1)
            val marker = if fence.head == '`' then "`" else "~"
            languages()
              .grammarFor(opener.group(2))
              .map(_ -> ("""\s{0,3}""" + marker + "{" + fence.length + """,}\s*$"""))
        }
        .when(lineBegin),
      Rule.token("""\s{0,3}#{1,6}(?:\s.*)?$""", Heading).when(lineBegin),
      Rule
        .token("""\s{0,3}(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$""", Punctuation)
        .when(lineBegin),
      Rule.token("""\s{0,3}(?:>\s?)+""", Punctuation).when(lineBegin),
      Rule.token("""\s*(?:[-*+]|\d{1,9}[.)])(?=\s|$)""", Operator).when(lineBegin),
      Include("inline")
    ),
    state("inline")(
      Rule.token("""\\[!-/:-@\[-`{-~]""", Escape),
      Rule.token("""(`+)[^`]+?\1""", Code),
      Rule.token("""!?\[[^\]]*\]\([^)\s]*(?:\s+"[^"]*")?\)""", Link),
      Rule.token("""!?\[[^\]]*\]\[[^\]]*\]""", Link),
      Rule.token("""<(?:https?|mailto|ftp):[^>\s]+>""", Link),
      Rule.token("""</?[A-Za-z][^>]*>""", Tag),
      Rule.token("""\*\*(?=\S)[^*]*[^*\s]\*\*|__(?=\S)[^_]*[^_\s]__""", Strong),
      Rule.token("""\*(?=[^\s*])[^*]*[^*\s]\*""", Emphasis),
      Rule.token("""_(?=[^\s_])[^_]*[^_\s]_(?![A-Za-z0-9])""", Emphasis).when(Rule.atWordStart),
      Rule.skip("""[^\\`*_\[!<]+""")
    ),
    state("fence")(
      Rule.token("""\s{0,3}(?:`{3,}|~{3,})\s*$""", Punctuation).pop.when(lineBegin),
      Rule.token(""".+""", Code)
    )
  )

/** Language names and the grammars behind them.
  *
  * Looked up case-insensitively: a fence says `Scala` as often as `scala`, and neither author means
  * a different language. The name comes from [[ember.editor.code.CodeInfo]], so what is found here
  * is exactly what Markdown wrote and what `class="language-…"` says.
  */
final class HighlightLanguages private (byName: Map[String, Grammar]):

  def grammarFor(language: String): Option[Grammar] = byName.get(language.toLowerCase)

  def names: Set[String] = byName.keySet

  /** Adds or replaces a language under all of its names. */
  def withLanguage(grammar: Grammar, names: String*): HighlightLanguages =
    new HighlightLanguages(byName ++ names.map(_.toLowerCase -> grammar))

object HighlightLanguages:

  val empty: HighlightLanguages = new HighlightLanguages(Map.empty)

  /** The first set: Scala, JavaScript/TypeScript, JSON, HTML/XML, CSS, shell and Markdown. */
  lazy val standard: HighlightLanguages =
    empty
      .withLanguage(Grammars.scala, "scala", "sc", "sbt")
      .withLanguage(Grammars.javascript, "javascript", "js", "mjs", "cjs", "jsx")
      .withLanguage(Grammars.typescript, "typescript", "ts", "mts", "cts", "tsx")
      .withLanguage(Grammars.json, "json", "jsonc", "json5")
      .withLanguage(Grammars.html, "html", "htm", "xhtml", "xml", "svg")
      .withLanguage(Grammars.css, "css")
      .withLanguage(Grammars.shell, "shell", "sh", "bash", "zsh", "shellscript")
      .withLanguage(Grammars.markdown, "markdown", "md")
