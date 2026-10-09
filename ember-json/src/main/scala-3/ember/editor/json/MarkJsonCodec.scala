package ember.editor.json

import ember.editor.core.*

/** Wie eine Markierungsart als JSON aussieht.
  *
  * Marks sind offen (§8.2) und der Kern kennt keine einzige -- Strong, Emphasis und die uebrigen
  * kommen aus `ember-rich-text` (P12). Damit ein Textlauf trotzdem heute schon verlustfrei
  * persistierbar ist, gibt es die SPI jetzt und nicht erst mit ihren ersten Nutzern.
  */
trait MarkJsonCodec[M <: TextMark]:

  def markId: MarkId

  /** Der Typzeuge, wie [[NodeType.project]]. */
  def project(mark: TextMark): Option[M]

  /** Die eigenen Felder der Markierung. Leer, wenn sie keine Nutzdaten traegt. */
  def encode(mark: M): Vector[(String, JsonValue)]

  def decode(payload: JsonValue.Obj, at: DiagnosticPath): Either[DecodeError, M]
