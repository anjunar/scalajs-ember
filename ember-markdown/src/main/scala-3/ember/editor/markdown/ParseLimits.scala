package ember.editor.markdown

import ember.editor.core.*

/** Upper bounds for parsing foreign Markdown.
  *
  * §18.2: "Groesse, Tiefe, Tokenzahl und Arbeitsschritte sind begrenzt." P17's acceptance repeats
  * it as "keine unbeschraenkte Rekursion oder katastrophale Regex-Laufzeit".
  *
  * ==Why limits at all==
  *
  * The same reason `DecodeLimits` in `ember-json` exists: a source arrives from the network, the
  * clipboard or a form field, and without bounds the sender decides how much memory and time the
  * receiver spends. Markdown is worse than JSON here, because its pathological inputs are short.
  * `"> " * 50000` is 100 kB and nests fifty thousand block quotes; a recursive tree walk over that
  * overflows the stack, and the tree itself is far larger than the input.
  *
  * ==Why a step budget and not just a depth==
  *
  * Because the expensive inputs are not always deep. A line of ten thousand backticks makes the
  * fence scan quadratic if written naively; so does a list marker followed by ten thousand spaces.
  * Depth and size do not see either. The step budget is charged per line and per block start
  * attempt, so anything that turns linear input into superlinear work runs out of budget instead of
  * running out of time.
  *
  * They are a value, not a constant: an application that needs deeper nesting raises them and then
  * knows that it did.
  *
  * @param maxSourceChars
  *   Length of the source, in UTF-16 units. Checked '''before''' parsing -- afterwards the memory
  *   is already spent.
  * @param maxLines
  *   Lines of input. A separate bound because a source of many empty lines is cheap per character
  *   and not per line.
  * @param maxDepth
  *   Nesting depth of containers. Bounds the tree walk that builds the immutable result, which is
  *   the only recursion in this module.
  * @param maxBlocks
  *   Blocks in the result. The token count of §18.2 -- a block is this parser's token.
  * @param maxSteps
  *   Work steps. Charged for every line and every attempted block start; see above.
  */
final case class ParseLimits(
    maxSourceChars: Int = 4 * 1024 * 1024,
    maxLines: Int = 200_000,
    maxDepth: Int = 100,
    maxBlocks: Int = 100_000,
    maxSteps: Int = 20_000_000
)

object ParseLimits:

  /** The default. For a document a human wrote, never reachable. */
  val default: ParseLimits = ParseLimits()

  /** Tight bounds for an untrusted paste: a page of text, not a book. */
  val paste: ParseLimits = ParseLimits(
    maxSourceChars = 256 * 1024,
    maxLines = 10_000,
    maxDepth = 24,
    maxBlocks = 5_000,
    maxSteps = 1_000_000
  )

/** A failure that produced no syntax tree.
  *
  * Closed: the ways parsing can fail are a property of this module, not an extension point. A
  * '''syntax''' error is not among them, and that is not an oversight -- CommonMark has no invalid
  * input. Every string is a valid document, so everything that can go wrong here is a resource
  * bound.
  */
sealed trait ParseError extends EditorError

object ParseError:

  final case class LimitExceeded(limit: String, allowed: Int, found: Int) extends ParseError:
    def message: String =
      s"Die Grenze `$limit` erlaubt $allowed, gefunden wurden $found. " +
        "Grenzen sind ein Wert (ParseLimits) -- eine Anwendung, die mehr braucht, setzt sie hoch."

  /** The step budget ran out. Deliberately says '''where''', because the answer is usually "one
    * line", and that is the difference between a big document and an attack.
    */
  final case class BudgetExhausted(allowed: Int, atLine: Int) extends ParseError:
    def message: String =
      s"Der Arbeitsschritt-Vorrat von $allowed war in Zeile $atLine aufgebraucht. " +
        "Das ist keine Groessengrenze -- eine kurze Eingabe kann ihn ebenso erschoepfen."

/** Something worth saying about a parse that nonetheless succeeded.
  *
  * Separate from [[ParseError]] for the reason `DecodeDiagnostic` in `ember-json` gives: an error
  * means no tree exists, a diagnostic means one does and something about it is worth knowing.
  */
final case class ParseDiagnostic(message: String, span: SourceSpan):
  def render: String = s"${span.render}: $message"
