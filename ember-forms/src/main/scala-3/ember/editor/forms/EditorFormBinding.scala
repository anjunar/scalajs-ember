package ember.editor.forms

import ember.editor.core.*

import scala.collection.mutable

/** Which of the two views owns the value right now. */
enum FieldMode:

  /** The document is authoritative. The textarea is hidden but '''not disabled''' -- §16 asks
    * for exactly one successful named field, and a disabled control submits nothing.
    */
  case Rich

  /** The textarea is authoritative. The rich view goes readonly (§16). */
  case Source

/** The one named form field of an editor, and everything §16 says about owning its value.
  *
  * ==What this class is for==
  *
  * A form has exactly one value for a field, and an editor has two places a value could come
  * from: the document and the textarea. §16 resolves that by making ownership explicit and
  * switchable, and this is where the switch lives.
  *
  * {{{
  * val field   = MarkdownField("body", …)
  * val binding = new EditorFormBinding(session, field)
  *
  * binding.submitValue                 // immer eindeutig
  * binding.enterSource()               // Textarea uebernimmt
  * binding.editDraft("# Titel")        // unbestaetigte Eingabe
  * binding.applyDraft()                // atomar gegen die Baseline
  * }}}
  *
  * ==Headless on purpose==
  *
  * Nothing here touches a DOM or a component. §16's structure -- preview, textarea, status -- is
  * a '''structure illustration''', and the rules it states are about ownership, staleness and
  * atomicity. Those are testable without a browser, and the browser gate then checks the parts
  * that are not: that the textarea is named, focusable and submits without JavaScript.
  *
  * ==What it does not do==
  *
  * No HTTP. §16: "Form action/method, CSRF, Validation, Persistenz und Fehlerrueckgabe liefert
  * die Anwendung." This class produces a string and says whose it is.
  */
final class EditorFormBinding(
    session: EditorSession,
    val field: EditorField,
    val intents: IntentPolicy = IntentPolicy.Defer,
    val submits: SubmitPolicy = SubmitPolicy.ImportDraft
):

  private var currentMode: FieldMode          = FieldMode.Rich
  private var draft: Option[SourceDraft]      = None
  private val pending                         = mutable.Queue.empty[DeferredIntent[?]]
  private var lastGood: Option[EncodedFieldValue] = None

  /** A queued change. A thunk, not a value: §16 asks for waiting intents to be '''re-validated'''
    * after the draft lands, and a value computed against the old document cannot be.
    */
  private final class DeferredIntent[A](val name: String, val run: () => A)

  def mode: FieldMode = currentMode

  def pendingDraft: Option[SourceDraft] = draft

  def deferred: Vector[String] = pending.map(_.name).toVector

  // -----------------------------------------------------------------------------------------
  // Der Formwert
  // -----------------------------------------------------------------------------------------

  /** The value a submit would send. Always defined, and always from exactly one owner.
    *
    * In [[FieldMode.Rich]] it is the document's encoded value for the '''current''' revision --
    * §16: "Im Rich-Modus wird der Submit-Wert nach jedem Dokumentcommit synchron aktualisiert",
    * which is what avoids a stale payload on Enter or `requestSubmit`.
    *
    * In [[FieldMode.Source]] it is the draft, unconfirmed and unparsed. That is the point: the
    * user sees the string they typed, and a submit sends the string they see.
    */
  def submitValue: String = currentMode match
    case FieldMode.Source => draft.map(_.text).getOrElse(encodedNow)
    case FieldMode.Rich   => encodedNow

  /** The last value the codec produced, or the last good one if the current state has none.
    *
    * A state without a representable value should not be reachable -- [[EditorField.reduce]]
    * rejects the transaction that would create one. The fallback exists for the one case it
    * cannot cover: a session created with a document that was already unrepresentable, where no
    * transaction ever ran.
    */
  private def encodedNow: String =
    field.valueFor(session.state) match
      case Right(value) =>
        lastGood = Some(value)
        value.value
      case Left(_) => lastGood.map(_.value).getOrElse(emptyValue)

  /** What this field sends for an empty document. */
  def emptyValue: String =
    field.codec.emptyValue(session.document.schema, session.document.rootId)

  /** Why the current document has no form value, if it has none. */
  def encodingProblem: Option[EditorError] = field.valueFor(session.state).left.toOption

  // -----------------------------------------------------------------------------------------
  // Moduswechsel
  // -----------------------------------------------------------------------------------------

  /** Hands ownership to the textarea, opening a draft from the current document.
    *
    * §16: "Ein Wechsel zu Source darf unbekannte Nodes niemals still entfernen." Here that means
    * the switch '''fails''' when the document has no representation rather than opening a draft
    * that silently lacks something -- because such a draft, applied back, would delete it.
    */
  def enterSource(selection: Option[SourceSelection] = None): Either[EditorError, SourceDraft] =
    if currentMode == FieldMode.Source then
      Right(draft.getOrElse(SourceDraft(encodedNow, session.state.documentRevision, selection)))
    else
      field.valueFor(session.state).map { encoded =>
        val opened = SourceDraft(encoded.value, session.state.documentRevision, selection)
        currentMode = FieldMode.Source
        draft = Some(opened)
        opened
      }

  /** Replaces the draft text. Unconfirmed: nothing is parsed and nothing is committed. */
  def editDraft(text: String, selection: Option[SourceSelection] = None): Unit =
    draft = Some(
      draft
        .map(_.copy(text = text, selection = selection.orElse(draft.flatMap(_.selection))))
        .getOrElse(SourceDraft(text, session.state.documentRevision, selection))
    )

  /** Imports the draft against its baseline, atomically, and returns to the rich mode.
    *
    * Three ways this refuses, and all three keep the visible string (§16: "Decode-/Konfliktfehler
    * erhalten den sichtbaren String"):
    *
    *   - the text does not parse,
    *   - the document moved since the draft was opened,
    *   - the resulting document has no representation in this field's format.
    *
    * Only on success does the mode change and the deferred queue run -- §16: "erst danach werden
    * wartende Intents neu validiert."
    */
  def applyDraft(): Either[EditorError, Unit] = draft match
    case None =>
      currentMode = FieldMode.Rich
      Right(())
    case Some(open) =>
      if open.isStaleAt(session.state) then
        Left(FieldError.StaleDraft(open.baseline.value, session.state.documentRevision.value))
      else
        // Schema und Wurzel kommen aus der Sitzung: ein Dokument gegen ein anderes
        // Schema-Objekt ist ein fremdes, und `restore` weist es zu Recht ab.
        field.codec.decode(open.text, session.document.schema, session.document.rootId) match
          case Left(error) => Left(error)
          case Right(document) =>
            // Eine Transaktion, nicht zwei: der Import ist atomar, und ein abgelehnter laesst
            // Dokument, Formwert und History unberuehrt (§16).
            session.update(transaction => transaction.restore(document, None): Unit) match
              case Left(error) => Left(error)
              case Right(_) =>
                draft = None
                currentMode = FieldMode.Rich
                releaseDeferred()
                Right(())

  /** Throws the draft away and returns to the rich mode.
    *
    * §16: "Ein Reset/Verwerfen ist eine ausdrueckliche Formaktion." Never implicit, never a side
    * effect of a document change -- which is the whole reason the draft is protected.
    */
  def discardDraft(): Unit =
    draft = None
    currentMode = FieldMode.Rich
    releaseDeferred()

  // -----------------------------------------------------------------------------------------
  // Fremde Aenderungen
  // -----------------------------------------------------------------------------------------

  /** Runs a change, or does not, depending on whether a draft is open.
    *
    * §16 names upload completion as the case that made this necessary: a file finishes uploading
    * while the user is editing the source, and applying it would overwrite what they typed. The
    * two allowed answers are [[IntentPolicy.Defer]] and [[IntentPolicy.Reject]].
    *
    * The rich mode runs everything immediately -- there is nothing to protect.
    */
  def request[A](what: String)(change: => A): IntentOutcome[A] =
    if currentMode == FieldMode.Rich then IntentOutcome.Applied(change)
    else
      intents match
        case IntentPolicy.Reject => IntentOutcome.Refused(FieldError.SourceBusy(what))
        case IntentPolicy.Defer =>
          pending.enqueue(new DeferredIntent(what, () => change))
          IntentOutcome.Deferred

  /** Runs what was waiting, against the document as it stands '''now'''.
    *
    * Not a replay: the thunks were queued unevaluated precisely so that they see the imported
    * document rather than the one they were written against.
    */
  private def releaseDeferred(): Unit =
    while pending.nonEmpty do pending.dequeue().run(): Unit

  // -----------------------------------------------------------------------------------------
  // Submit
  // -----------------------------------------------------------------------------------------

  /** The value to send, or why there is nothing to send.
    *
    * Under [[SubmitPolicy.ImportDraft]] an open draft is imported first, so that the server
    * receives a document and not an unconfirmed string. Under
    * [[SubmitPolicy.RefuseWhileDirty]] an open draft blocks the submit outright.
    */
  def valueForSubmit(): Either[EditorError, String] =
    (currentMode, draft, submits) match
      case (FieldMode.Rich, _, _) => Right(submitValue)

      case (FieldMode.Source, Some(_), SubmitPolicy.RefuseWhileDirty) =>
        Left(FieldError.SourceBusy("submit"))

      case (FieldMode.Source, Some(open), SubmitPolicy.ImportDraft) =>
        // Erst importieren, dann senden. Schlaegt der Import fehl, bleibt der Text stehen und
        // der Submit unterbleibt -- ein halb uebernommener Stand waere schlimmer als keiner.
        applyDraft().map(_ => submitValue)

      case (FieldMode.Source, None, _) => Right(submitValue)

  /** The name a server sees. §16: exactly one successful named field per editor. */
  def fieldName: String = field.fieldName
