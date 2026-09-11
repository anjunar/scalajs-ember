package ember.editor.forms

import ember.editor.browser.*
import ember.editor.core.*
import ember.editor.html.{HtmlSupport, RenderProfile}
import ember.editor.jfx.{DocumentView, ViewSupport}
import jfx.core.component.{AbstractComponent, HydrationBoundary}
import jfx.core.render.HostElement
import org.scalajs.dom
import jfx.core.dsl.AttributeDsl.setAttribute
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextArea
import jfx.core.layout.TextArea.textArea
import jfx.core.render.Cursor

/** The editable field as a component: a preview and exactly one named textarea.
  *
  * ==The structure, and why it is this one==
  *
  * §16 shows it as HTML and then says what the HTML has to be: the textarea is "ohne JavaScript
  * sichtbar, benannt, fokussierbar und normal submitbar", and after activation there remains
  * "'''genau ein''' erfolgreiches benanntes Formularfeld". Both halves constrain this component:
  *
  *   - The '''same''' textarea serves both modes. It is hidden in the rich mode, never
  *     `disabled` -- a disabled control submits nothing, and then the form would have no value
  *     at all.
  *   - The preview carries no form name. §16: "Der Editor-Host hat keinen konkurrierenden
  *     Formularnamen."
  *
  * ==Without JavaScript==
  *
  * Rendered server-side, this is a labelled textarea holding the source, inside a form the
  * application built. It submits, the server answers, and §16 leaves preview and validation to
  * the normal POST/Redirect/GET. Nothing here invents an endpoint.
  *
  * ==Cost, stated rather than hidden==
  *
  * §16: "Formatadapter koennen unveraenderte Blockausgaben cachen; das Materialisieren/Zuweisen
  * des vollstaendigen Formularstrings kostet dennoch mindestens dessen Laenge. Diese Kosten
  * werden getrennt von Core-/Projection-Lokalitaet gemessen und nicht als O(1) dargestellt."
  *
  * So: a keystroke touches a handful of components in the projection (P09 measures that), and
  * '''also''' re-encodes the whole document into a string. The second cost is linear in the
  * document, every commit. That is not a bug to be optimised away here -- it is what having a
  * single form value means, and pretending otherwise would misrepresent the measurement.
  */
final class EditorFieldView(
    binding: EditorFormBinding,
    session: EditorSession,
    views: ViewSupport,
    label: String,
    semantics: Option[HtmlSupport] = None
) extends AbstractComponent:

  val tagName = "section"

  private var source: TextArea      = null
  private var preview: DocumentView = null
  private var activated             = false

  /** What was on the page before the first claim (§17.2). `None` outside hydration. */
  private var snapshot: Option[HydrationSnapshot] = None

  /** Whether the local claim came through. A rebuild sets it to `false` (§17.3). */
  private var claimed = true

  private var sourceImported = false
  private var lastFailure: Option[String] = None
  private var boundary: HydrationBoundary[HydrationSnapshot] = null

  /** The textarea, once composed. For a caller that drives focus or selection. */
  def sourceControl: TextArea = source

  override def compose(cursor: Cursor): Unit =
    setAttribute("data-editor-field", binding.fieldName)

    DslLayer.render(this, cursor) {
      // Die Textarea zuerst und ausserhalb der Boundary. §17.3: "Der Fallback liegt
      // ausserhalb der austauschbaren Rich-View-Boundary und bleibt bei deren Fehler erhalten."
      // Laege sie darin, naehme ein fehlgeschlagener Claim sie mit -- samt dem, was der
      // Benutzer hineingetippt hat.
      source = textArea(binding.submitValue) {}
      source.setAttribute("name", binding.fieldName)
      source.setAttribute("aria-label", label)
      source.setDefaultValue(binding.submitValue)

      // Die Vorschau steckt in einer HydrationBoundary. Sie ist die austauschbare Haelfte:
      // scheitert der Claim, baut jfx-core NUR SIE neu auf und nimmt dabei die schon
      // registrierten Cursor und die noch offenen Hydration-Callbacks dieses Versuchs mit
      // (§17, letzter Absatz).
      val wrapper = new HydrationBoundary[HydrationSnapshot](
        "div",
        capture = _ => captureFallback(),
        preflight = (element, _) => runPreflight(element),
        onRecovery = error =>
          claimed = false
          lastFailure = Some(error.getMessage)
      )((inner: AbstractComponent) ?=>
        (isolated: Cursor) ?=>
          // Ein zweiter Versuch laeuft durch denselben Block. Die Runtime hat die Komponenten
          // des ersten schon abgeraeumt -- die Ansicht darum herum haelt aber noch ihr
          // Commit-Abonnement, und das zeigte danach auf einen Baum, den es nicht mehr gibt.
          if preview != null then preview.dispose()

          inner.setAttribute("data-editor-preview", "")
          // Die Vorschau traegt keinen Formularnamen und ist im Source-Modus readonly (§16).
          inner.setAttribute("role", "document")

          // Der isolierte Cursor der Boundary, nicht `Runtime.contentCursor(inner)`:
          // `withHydrationBoundary` uebergibt ihm den gesamten verbleibenden Bereich und laesst
          // den eigenen Cursor der Komponente leer zurueck. Ueber den zu hydrieren hiesse, an
          // einer Stelle weiterzulesen, die die Boundary gerade abgegeben hat.
          preview = DocumentView.mount(session, isolated, views, parent = Some(inner))
      )

      jfx.core.component.Runtime.mount(wrapper, summon[jfx.core.render.Cursor], Some(this)): Unit
      boundary = wrapper
    }

    // Nach jedem Commit synchron nachziehen (§16). Eine verzoegerte Vorschau waere davon
    // unabhaengig -- der '''Wert''' darf nie hinterherhinken.
    preview.onProjected(_ => syncFromDocument()): Unit
    applyMode()

  // -----------------------------------------------------------------------------------------
  // Hydration (§17)
  // -----------------------------------------------------------------------------------------

  /** Reads the fallback before anything claims it.
    *
    * §17.2 puts this '''before the first claim''': the live `value`, the selection and the
    * focus, not the attribute. A user who typed before the script ran changed `value`; the
    * attribute still holds what the server sent, and reading it would discard their input.
    *
    * It reads the '''textarea''', although the boundary hands over the preview host. That is
    * deliberate, and it is why the fallback sits outside: the thing worth rescuing must not be
    * inside the thing that might fail.
    */
  private def captureFallback(): HydrationSnapshot =
    val captured = Option(source)
      .flatMap(area => jfx.core.render.DomNodes.option(area.host))
      .collect { case element: dom.HTMLTextAreaElement => HydrationSnapshot.of(element) }

    snapshot = captured
    captured.getOrElse(
      HydrationSnapshot("", 0, 0, SelectionDirection.Collapsed, focused = false, composing = false)
    )

  /** Checks the served markup against the document, before binding hides it (§17.5). */
  private def runPreflight(element: HostElement): Unit =
    semantics.foreach { support =>
      jfx.core.render.DomNodes.option(element).foreach {
        case host: dom.Element =>
          // Der Editor-Check, den JFX-Strict allein nicht leistet: IDs, Text und die Attribute,
          // die die Semantik nennt.
          //
          // `preflightContent`, nicht `preflight`: die Boundary reicht ihren eigenen Host
          // herueber -- den Vorschau-Container --, und der Wurzelknoten des Dokuments ist das
          // erste Element darin.
          EditorHydration.preflightContent(host, session.document, support, RenderProfile.Editor)
        case _ => ()
      }
    }

  /** What was captured before the first claim, if anything. */
  def captured: Option[HydrationSnapshot] = snapshot

  /** Whether the local claim came through. */
  def claimSucceeded: Boolean = claimed

  /** The activation decision of §17 for the current state.
    *
    * Recomputed rather than stored: every input is something this object already knows, and a
    * cached answer would be a second opinion. Repeated enhancement is then idempotent by
    * construction, which §17's deviation table asks for.
    */
  def activation(pageHydrated: Boolean): ActivationState =
    EditorActivation.decide(claimed, pageHydrated, snapshot, binding.submitValue, sourceImported)

  /** Takes over a source the user changed before the script ran (§17.4).
    *
    * "Das anfaengliche Preview wird gegen den Server-Snapshot geclaimt; anschliessend wird der
    * erfolgreich geparste Source-Draft als neuer Zustand projiziert." So: claim first, import
    * second -- and a parse error leaves the draft editable and blocks the enhancement.
    */
  def importCapturedSource(): Either[EditorError, Unit] =
    snapshot match
      case None =>
        sourceImported = true
        Right(())
      case Some(value) if !value.differsFrom(binding.submitValue) =>
        sourceImported = true
        Right(())
      case Some(value) =>
        binding.enterSource() match
          case Left(error) => Left(error)
          case Right(_) =>
            setDraft(value.sourceValue)
            binding.applyDraft() match
              case Right(_) =>
                sourceImported = true
                applyMode()
                Right(())
              // §17.4: "Parsingfehler lassen den Draft editierbar und verhindern Enhancement."
              case Left(error) => Left(error)

  /** Why the local claim failed, if it did. */
  def failure: Option[String] = lastFailure

  /** Hides the textarea, because the rich view has taken over.
    *
    * §16 puts this after activation, and the order is the whole no-JavaScript contract:
    * "Die Textarea ist ohne JavaScript sichtbar, benannt, fokussierbar und normal submitbar",
    * and only "nach erfolgreicher Aktivierung ... wird sie fuer die Rich-Ansicht verborgen".
    *
    * So a server-rendered field shows its textarea. Hiding it at render time would mean a page
    * without JavaScript had a form nobody could fill in -- which a browser test found, and a
    * server-side render never would have.
    */
  def activate(): Unit =
    activated = true
    applyMode()

  /** Writes the document's value into the textarea -- unless a draft owns it.
    *
    * §16: "Document→Form-Projektion darf diesen Draft nicht ueberschreiben." That check is the
    * entire reason this is a method and not a binding.
    */
  private def syncFromDocument(): Unit =
    if binding.mode == FieldMode.Rich && source != null then
      val value = binding.submitValue
      if source.value != value then source.setValue(value)

  /** Sets the draft programmatically, textarea included.
    *
    * The difference to [[EditorFormBinding.editDraft]] matters: that one changes the model, and
    * the textarea would keep the old string. The next [[captureDraft]] would then read the old
    * string back and quietly undo the change -- which is exactly what a browser test found.
    */
  def setDraft(text: String, selection: Option[SourceSelection] = None): Unit =
    binding.editDraft(text, selection)
    if source != null then source.setValue(text)

  /** Reads what the user typed into the draft. Call from an `input` handler. */
  def captureDraft(selection: Option[SourceSelection] = None): Unit =
    if binding.mode == FieldMode.Source && source != null then
      binding.editDraft(source.readNativeValue(), selection)

  /** Hands the value to the textarea and shows it. */
  def enterSource(): Either[EditorError, Unit] =
    binding.enterSource().map { draft =>
      if source != null then source.setValue(draft.text)
      applyMode()
    }

  /** Imports the draft and hides the textarea again. */
  def applyDraft(): Either[EditorError, Unit] =
    captureDraft()
    binding.applyDraft().map { _ =>
      syncFromDocument()
      applyMode()
    }

  def discardDraft(): Unit =
    binding.discardDraft()
    syncFromDocument()
    applyMode()

  /** Shows and hides, and nothing else.
    *
    * `hidden` and not `disabled`: §16 asks for exactly one '''successful''' named field, and a
    * disabled control is not successful -- the form would submit nothing for this name.
    */
  private def applyMode(): Unit =
    if source == null then ()
    else
      // Vor der Aktivierung ist die Textarea sichtbar -- das ist der Non-JS-Fall (§16).
      val editing = binding.mode == FieldMode.Source || !activated
      // `removeAttribute` und nicht `setAttribute(name, null)`: ein `null` kommt im SSR-Host
      // beim Escapen an und fliegt dort, statt das Attribut wegzulassen.
      if editing then
        source.removeAttribute("hidden")
        source.removeAttribute("aria-hidden")
      else
        source.setAttribute("hidden", "")
        source.setAttribute("aria-hidden", "true")
      setAttribute(
        "data-editor-mode",
        if binding.mode == FieldMode.Source then "source" else if activated then "rich" else "nojs"
      )

  override def dispose(): Unit =
    if preview != null then preview.dispose()
    super.dispose()
