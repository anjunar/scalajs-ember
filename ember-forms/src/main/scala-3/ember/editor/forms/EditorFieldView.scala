package ember.editor.forms

import ember.editor.core.*
import ember.editor.html.RenderProfile
import ember.editor.jfx.{DocumentView, ViewSupport}
import jfx.core.component.AbstractComponent
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
    label: String
) extends AbstractComponent:

  val tagName = "section"

  private var source: TextArea      = null
  private var preview: DocumentView = null
  private var activated             = false

  /** The textarea, once composed. For a caller that drives focus or selection. */
  def sourceControl: TextArea = source

  override def compose(cursor: Cursor): Unit =
    setAttribute("data-editor-field", binding.fieldName)

    DslLayer.render(this, cursor) {
      val host = div {}
      // Auf der Komponente und nicht ueber den DSL-Import: innerhalb einer AbstractComponent
      // verdeckt deren eigenes `setAttribute` die Erweiterung, und die Attribute landeten
      // stillschweigend auf der Section statt auf dem Div.
      host.setAttribute("data-editor-preview", "")
      // Die Vorschau traegt keinen Formularnamen und ist im Source-Modus readonly (§16).
      host.setAttribute("role", "document")

      preview = DocumentView.mount(session, jfx.core.component.Runtime.contentCursor(host), views,
        parent = Some(host))

      source = textArea(binding.submitValue) {}
      source.setAttribute("name", binding.fieldName)
      source.setAttribute("aria-label", label)
      source.setDefaultValue(binding.submitValue)
    }

    // Nach jedem Commit synchron nachziehen (§16). Eine verzoegerte Vorschau waere davon
    // unabhaengig -- der '''Wert''' darf nie hinterherhinken.
    preview.onProjected(_ => syncFromDocument()): Unit
    applyMode()

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
