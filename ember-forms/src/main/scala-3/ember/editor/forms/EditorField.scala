package ember.editor.forms

import ember.editor.core.*

/** The derived state field that carries a document's form value.
  *
  * ==This is §16's format boundary, and it sits before the commit==
  *
  * §16: "Diese Formatgrenze wird '''vor Commit''' geprueft, auch fuer programmgesteuerte Commands:
  * Ein Form-Adapter installiert eine synchrone Darstellbarkeitsregel und ein abgeleitetes
  * `EncodedFieldValue`-StateField. Dessen Reducer berechnet gegen den fertig normalisierten
  * Kandidaten den Submit-String oder lehnt die Tx ab."
  *
  * The core already had the shape for this: [[StateField.reduce]] runs against the finished
  * candidate in §10's step 5, and a `Left` rejects the transaction. So the "Darstellbarkeitsregel"
  * and the "abgeleitetes StateField" are '''one''' thing here rather than two -- a separate
  * [[PreCommitRule]] that asked the same question would either duplicate the encode or disagree
  * with it.
  *
  * The consequence is the one §16 names: a `ToggleUnderline` in a strict CommonMark field does not
  * produce a document whose form value is stale. It produces no document at all -- the transaction
  * is rejected, and the session, the form value and the history are untouched.
  *
  * ==Why it is not persisted and not restored==
  *
  * §16: "Das abgeleitete Feld wird nicht persistiert und bei Undo neu berechnet; History speichert
  * keine veralteten Codec-Ergebnisse." So [[onHistoryRestore]] stays at `Recompute`, which is the
  * default -- and the default is right here for a reason worth stating: a captured encode result is
  * a cached answer to a question the document can answer again, and a cache in a history snapshot
  * is a cache nobody invalidates.
  *
  * @param fieldName
  *   the `name` attribute of the form control. §16 asks for '''exactly one''' successful named
  *   field, so this is also the identity of the field as far as a server is concerned.
  * @param codec
  *   the format; see [[FieldCodec]].
  */
final class EditorField(val fieldName: String, val codec: FieldCodec)
    extends StateField[EncodedFieldValue]:

  val name: String = s"ember.forms.field/$fieldName"

  val initial: EncodedFieldValue = EncodedFieldValue(None, "")

  /** Recomputed for every candidate, not carried forward.
    *
    * [[DocumentChangePolicy]] would only run on a document change, and that is not enough: the
    * value has to exist for the '''first''' state too, before anything changed. [[reduce]] runs on
    * every commit and covers both.
    */
  override def onDocumentChange: DocumentChangePolicy = DocumentChangePolicy.Keep

  override def reduce(
      current: EncodedFieldValue,
      candidate: CommitCandidate
  ): Either[EditorError, EncodedFieldValue] =
    // Gegen den fertigen Kandidaten, nicht gegen den Stand davor: §10 laesst Transforms bis
    // hierher laufen, und ein Wert, der vor der Normalisierung berechnet wurde, gehoerte zu
    // einem Dokument, das es nie gab.
    codec
      .encode(candidate.document)
      .map(value => EncodedFieldValue(Some(candidate.previous.revision.next), value))

  /** The value for a state, computed on demand.
    *
    * For a session that has just been created and has not committed anything: its field holds
    * [[initial]], which is the empty value rather than the document's. A form that read it would
    * send an empty payload for a non-empty document.
    */
  def valueFor(state: EditorState): Either[EditorError, EncodedFieldValue] =
    val stored = state.fields(this)
    if stored.isFor(state) then Right(stored)
    else codec.encode(state.document).map(value => EncodedFieldValue(Some(state.revision), value))
