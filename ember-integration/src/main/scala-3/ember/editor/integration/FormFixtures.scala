package ember.editor.integration

import ember.editor.core.*
import ember.editor.browser.*
import ember.editor.forms.*
import ember.editor.image.MediaUrlPolicy
import ember.editor.link.LinkUrlPolicy
import ember.editor.markdown.*
import ember.editor.richtext.*
import ember.editor.standard.{MarkdownSupports, ParagraphSupport, RichTextSupport}
import ui.core.component.Runtime
import ui.core.render.{DomCursor, HydratingCursor, SsrCursor}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** The form field, driven from a test (P19b).
  *
  * ==Two jobs, and they run in different processes==
  *
  *   - [[renderForNoScript]] runs in '''Node''', inside the harness server. It renders the field
  *     the way a server would and hands back HTML. §15.2 makes that possible -- importing this
  *     module must not touch `window` -- and the no-JavaScript test then works on exactly what a
  *     server would have sent.
  *   - [[mount]] and the rest run in the '''browser''', so that `source-form.spec.mjs` can drive
  *     the draft, the queue and the submit against a live session.
  *
  * Both go through the same [[EditorFieldView]]. A fixture that rendered its own HTML would be
  * testing itself.
  */
@JSExportTopLevel("formFixtures")
object FormFixtures:

  private val links = LinkUrlPolicy.default
  private val media = MediaUrlPolicy.default

  private def resolved(field: EditorField): ResolvedExtensions =
    ExtensionResolver
      .resolve(Vector(RichText(NodeIdGenerator.sequential("n")), FormFieldExtension(field)))
      .getOrElse(throw new IllegalStateException("Extensions nicht aufloesbar"))

  private def newField(): EditorField =
    EditorFields.markdown(
      "body",
      MarkdownSupports.richText,
      NodeIdGenerator.sequential("m")
    )

  private def newSession(field: EditorField, source: String): EditorSession =
    val extensions = resolved(field)
    val schema     = extensions.schema

    val document = MarkdownCodec
      .decode(source, schema, MarkdownSupports.richText, NodeIdGenerator.sequential("d"), NodeId("root"))
      .map(_.document)
      .getOrElse(throw new IllegalStateException(s"Fixture nicht dekodierbar: $source"))

    EditorSession
      .create(document, extensions, extensions.sessionConfig())
      .getOrElse(throw new IllegalStateException("Sitzung nicht erzeugbar"))

  private val views = RichTextSupport.views

  /** The same description the SSR came from -- the preflight compares against it (§17.5). */
  private val semantics = RichTextSupport.semantics

  // -----------------------------------------------------------------------------------------
  // Der Serverprozess
  // -----------------------------------------------------------------------------------------

  /** Renders the field the way a server would, without touching a DOM.
    *
    * Called from `server.mjs` in Node. This is the whole point of §15.2's "Importieren eines
    * Moduls im Serverprozess darf nicht bereits `window` oder `document` lesen" -- without it
    * there would be no server-rendered field to submit, and the no-JavaScript test would have
    * to test a hand-written HTML string instead of the real one.
    */
  @JSExport
  def renderForNoScript(source: String): String =
    val field   = newField()
    val session = newSession(field, source)
    val binding = new EditorFormBinding(session, field)
    val cursor  = new SsrCursor()
    val view    = new EditorFieldView(binding, session, views, "Inhalt")

    Runtime.mount(view, cursor)
    val html = cursor.collectHtml()
    Runtime.unmount(view)
    session.dispose()
    html

  /** The value a server should receive for a source, computed without any DOM. */
  @JSExport
  def expectedValue(source: String): String =
    val field   = newField()
    val session = newSession(field, source)
    val value   = new EditorFormBinding(session, field).submitValue
    session.dispose()
    value

  // -----------------------------------------------------------------------------------------
  // Der Browser
  // -----------------------------------------------------------------------------------------

  private var field: EditorField           = null
  private var session: EditorSession       = null
  private var binding: EditorFormBinding   = null
  private var view: EditorFieldView        = null

  @JSExport
  def mount(container: dom.Element, source: String, reject: Boolean): Unit =
    dispose()
    field = newField()
    session = newSession(field, source)
    binding = new EditorFormBinding(
      session,
      field,
      if reject then IntentPolicy.Reject else IntentPolicy.Defer
    )
    view = new EditorFieldView(binding, session, views, "Inhalt", Some(semantics))
    Runtime.mount(view, DomCursor.root(container)): Unit
    // Der Browserpfad ist die '''Aktivierung''' aus §16 -- erst danach verschwindet die Textarea.
    view.activate()

  @JSExport
  def dispose(): Unit =
    if view != null then Runtime.unmount(view)
    if session != null then session.dispose()
    view = null
    session = null
    binding = null
    field = null

  @JSExport def mode: String       = if binding.mode == FieldMode.Source then "source" else "rich"
  @JSExport def submitValue: String = binding.submitValue
  @JSExport def deferred: js.Array[String] = js.Array(binding.deferred*)

  @JSExport
  def enterSource(): String = view.enterSource() match
    case Right(_)    => ""
    case Left(error) => error.message

  @JSExport
  def editDraft(text: String): Unit = view.setDraft(text)

  @JSExport
  def applyDraft(): String = view.applyDraft() match
    case Right(_)    => ""
    case Left(error) => error.message

  @JSExport
  def discardDraft(): Unit = view.discardDraft()

  /** An independent document change, of the kind an upload completion would be. */
  @JSExport
  def outsideChange(text: String): String =
    binding.request("outside") {
      session.update { transaction =>
        session.document.inDocumentOrder.collectFirst { case run: TextNode => run }.foreach { run =>
          transaction.replace(run.id, run.copy(text = text)): Unit
        }
      }
    } match
      case ember.editor.forms.IntentOutcome.Applied(_)     => "applied"
      case ember.editor.forms.IntentOutcome.Deferred       => "deferred"
      case ember.editor.forms.IntentOutcome.Refused(error) => s"refused:${error.message}"

  /** A change that did 'not' go through the binding.
    *
    * The baseline exists for exactly this: something moved the document without asking, and the
    * draft was written against a revision that is gone. §16 calls it a conflict, and the draft
    * survives it.
    */
  @JSExport
  def outsideChangeUnmediated(text: String): Unit =
    session.update { transaction =>
      session.document.inDocumentOrder.collectFirst { case run: TextNode => run }.foreach { run =>
        transaction.replace(run.id, run.copy(text = text)): Unit
      }
    }: Unit

  /** The document's first text run -- what a deferred change should have reached. */
  @JSExport
  def firstRun: String =
    session.document.inDocumentOrder.collectFirst { case run: TextNode => run.text }.getOrElse("")


  // -----------------------------------------------------------------------------------------
  // Hydration (P20)
  // -----------------------------------------------------------------------------------------

  private var hydrated = false
  private var activations = 0

  /** Hydrates over markup the server already sent.
    *
    * `container` holds the server's HTML; nothing here writes it. The cursor claims it, the
    * boundary captures the fallback first (§17.2) and the preflight checks it before binding
    * hides it (§17.5).
    *
    * @param source
    *   the document the server rendered. A '''different''' one here is how the test produces a
    *   mismatch without hand-editing the DOM.
    */
  @JSExport
  def hydrate(container: dom.Element, source: String): Unit =
    dispose()
    hydrated = false
    activations = 0
    field = newField()
    session = newSession(field, source)
    binding = new EditorFormBinding(session, field)
    view = new EditorFieldView(binding, session, views, "Inhalt", Some(semantics))

    val cursor = HydratingCursor.root(container)
    Runtime.mount(view, cursor): Unit
    cursor.completeHydration()
    hydrated = true

  /** The activation decision right now (§17.6). */
  @JSExport
  def activationState: String = view.activation(hydrated) match
    case ActivationState.Active            => "active"
    case ActivationState.Pending           => "pending"
    case ActivationState.Deferred(reason)  => s"deferred:${reason.toString}"
    case ActivationState.Failed(problem)   => s"failed:$problem"

  /** Runs the activation. Counted, so that a test can show it is idempotent. */
  @JSExport
  def activate(): String =
    val state = view.activation(hydrated)
    if state == ActivationState.Active then
      activations += 1
      view.activate()
    activationState

  @JSExport def activationCount: Int = activations

  @JSExport def claimSucceeded: Boolean = view.claimSucceeded

  @JSExport def capturedSource: String = view.captured.map(_.sourceValue).getOrElse("")

  @JSExport def capturedFocus: Boolean = view.captured.exists(_.focused)

  @JSExport
  def capturedSelection: String = view.captured match
    case Some(value) => s"${value.selectionStart}:${value.selectionEnd}:${value.selectionDirection}"
    case None        => "none"

  @JSExport def mayRestoreSelection: Boolean = EditorActivation.mayRestoreSelection(view.captured)

  @JSExport
  def importCapturedSource(): String = view.importCapturedSource() match
    case Right(_)    => ""
    case Left(error) => error.message

  @JSExport def hydrationFailure: String = view.failure.getOrElse("")

  @JSExport
  def valueForSubmit(): String = binding.valueForSubmit() match
    case Right(value) => value
    case Left(error)  => s"refused:${error.message}"
