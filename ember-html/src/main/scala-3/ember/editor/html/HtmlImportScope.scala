package ember.editor.html

import ember.editor.core.*

import scala.collection.mutable

/** What a rule may ask for while it decides.
  *
  * Deliberately small: ids, the schema, the policy, a way to report a loss, and URL handling. A
  * rule that could reach further would be a rule that could make the import depend on something the
  * caller did not pass in.
  */
final class HtmlImportScope private[html] (
    val schema: Schema,
    val policy: HtmlImportPolicy,
    private val generator: NodeIdGenerator,
    private val used: mutable.Set[NodeId],
    private val sink: mutable.ArrayBuffer[HtmlDiagnostic],
    private val open: mutable.ArrayBuffer[String]
):

  /** The tags a rule is currently inside, outermost first.
    *
    * Needed because meaning in HTML is not always local. A `<code>` is an inline mark, except
    * inside a `<pre>`, where it is the conventional wrapper a highlighter puts there and means
    * nothing of its own. Without the context, either every code block gains a spurious mark or
    * inline code loses its real one.
    */
  def ancestors: Vector[String] = open.toVector

  def insideTag(tag: String): Boolean = open.contains(tag)

  private[html] def enter(tag: String): Unit = open += tag

  private[html] def leave(): Unit = if open.nonEmpty then open.remove(open.length - 1)

  /** A fresh id, distinct from everything handed out so far. */
  def nextId(): NodeId =
    val id = generator.next(used.contains)
    used += id
    id

  def note(kind: HtmlLoss, detail: String): Unit = sink += HtmlDiagnostic(kind, detail)

  /** A URL the format is willing to carry, normalised (§19.1).
    *
    * `None` means the baseline refused it -- `javascript:` and friends -- and the loss has already
    * been reported. A rule still has to ask its own link or media policy afterwards: this is the
    * floor, not the decision.
    */
  def url(raw: String): Option[String] =
    val normalised = policy.normaliseUrl(raw)
    if policy.refusesUrl(normalised) then
      note(HtmlLoss.RefusedUrl, normalised.take(60))
      None
    else Some(normalised)
