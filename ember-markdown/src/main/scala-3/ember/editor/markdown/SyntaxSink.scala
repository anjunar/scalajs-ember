package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** Where an encoding rule puts what it cannot express.
  *
  * It hands out no ids: the syntax tree a rule builds is thrown away after it is written, so its
  * [[SyntaxId]]s and [[SourceSpan]]s mean nothing. `fresh` and `nowhere` exist so a rule can fill
  * the fields without pretending they carry information.
  */
final class SyntaxSink private[markdown] ():

  private var counter = 0
  private val notes   = mutable.ArrayBuffer.empty[MarkdownDiagnostic]

  def fresh(): SyntaxId =
    counter += 1
    SyntaxId(counter)

  /** The span for a syntax node that is on its way out. Empty, and deliberately so. */
  val nowhere: SourceSpan = SourceSpan(0, 0)

  def note(message: String, loss: Boolean = false): Unit =
    notes += MarkdownDiagnostic(message, None, loss)

  /** Records that something the document holds has no Markdown spelling.
    *
    * The distinction between this and [[note]] is the whole of `Strict` versus `AllowLossy`
    * (§18.2): a loss means the export is not reversible, and an application has to say in advance
    * that it accepts that.
    */
  def lost(what: String): Unit = note(what, loss = true)

  private[markdown] def diagnostics: Vector[MarkdownDiagnostic] = notes.toVector
