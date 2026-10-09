package ember.editor.forms

import ember.editor.core.*
import ember.editor.json.*
import ember.editor.markdown.*

/** The two field formats §16 offers, and the reason there are exactly two.
  *
  * §16: "Nicht jeder Custom Node ist verlustfrei in Markdown darstellbar. Die Anwendung waehlt
  * daher ausdruecklich `MarkdownField(profile)` oder `JsonDocumentField(schema)`."
  *
  * The difference is not convenience, it is what a server gets:
  *
  *   - a '''Markdown''' field sends something a human wrote and can read, and refuses documents it
  *     cannot express;
  *   - a '''JSON''' field sends the document, losslessly, and is legible to a program rather than
  *     to a person.
  *
  * §16 is candid about the second one: "JSON-Felder koennen als universellen Non-JS-Fallback eine
  * beschriftete JSON-Textarea anbieten; anwendungsspezifische serverseitige Formulare sind
  * nutzerfreundlichere Alternativen." A JSON textarea is a fallback, not a feature.
  */
object EditorFields:

  /** A Markdown field.
    *
    * @param name
    *   the form field's `name` attribute.
    * @param rules
    *   the Markdown rules; from `ember-standard` in practice.
    * @param schema
    *   for decoding a submitted string back into a document.
    * @param loss
    *   §16: "Ein Markdown-Feld akzeptiert nur vollstaendig unterstuetzte Dokumente bzw. eine
    *   explizite verlustbehaftete Konvertierung mit Diagnose." `Strict` is the default, and that is
    *   the strict-CommonMark field §16 uses as its example: a `ToggleUnderline` in it cannot
    *   commit.
    */
  def markdown(
      name: String,
      rules: MarkdownSupport,
      generator: NodeIdGenerator,
      profile: MarkdownProfile = MarkdownProfile.commonMarkSafe,
      loss: LossPolicy = LossPolicy.Strict
  ): EditorField =
    new EditorField(name, new MarkdownFieldCodec(rules, generator, profile, loss))

  /** A JSON field: the whole document, losslessly.
    *
    * No loss policy, because there is no loss to allow. A document that this codec cannot write is
    * a document with a node type nobody registered a codec for, and that is a configuration error
    * rather than a format boundary.
    */
  def json(name: String, support: JsonSupport, pretty: Boolean = false): EditorField =
    new EditorField(name, new JsonFieldCodec(support, pretty))
