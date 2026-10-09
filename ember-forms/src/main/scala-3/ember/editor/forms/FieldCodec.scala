package ember.editor.forms

import ember.editor.core.*

/** The format a form field speaks.
  *
  * ==Why the application has to choose one==
  *
  * §16: "Nicht jeder Custom Node ist verlustfrei in Markdown darstellbar. Die Anwendung waehlt
  * daher ausdruecklich `MarkdownField(profile)` oder `JsonDocumentField(schema)`."
  *
  * There is no default and no automatic fallback. A field that quietly switched formats when a
  * document outgrew the first one would produce a form value whose meaning depends on the content
  * -- and a server has no way to tell which it got.
  *
  * ==Why encoding can fail==
  *
  * Because the format boundary is real. A Markdown field cannot carry an underlined run, and §16
  * asks for that to be decided '''before''' the commit rather than reported afterwards: "Ein
  * `ToggleUnderline` in einem Strict-CommonMark-Feld kann daher keinen kanonischen Zustand
  * erzeugen, dessen Formwert veraltet bleibt."
  *
  * [[EditorField]] is what makes that happen -- it is a [[StateField]] whose reducer runs this
  * codec against the finished candidate and rejects the transaction when it refuses.
  */
trait FieldCodec:

  /** Shown in diagnostics. */
  def name: String

  /** The submit value for a document, or why this document has none.
    *
    * Pure and synchronous, because a [[StateField]] reducer is: the core runs it for candidates
    * that are about to be discarded, and it must not touch anything outside.
    */
  def encode(document: Document): Either[EditorError, String]

  /** A document from a submitted string.
    *
    * Takes the schema and the root id rather than capturing them, and that is not a detail: a
    * document built against a *different* `Schema` instance is a foreign document, and
    * `Transaction.restore` refuses it. The only schema that can be right is the session's, and only
    * the caller has it.
    */
  def decode(source: String, schema: Schema, rootId: NodeId): Either[EditorError, Document]

  /** What an empty field holds. Not `""` for every format: an empty JSON field is not an empty
    * string, it is an empty document's payload -- and a server that receives `""` cannot tell an
    * empty document from a missing field.
    */
  def emptyValue(schema: Schema, rootId: NodeId): String
