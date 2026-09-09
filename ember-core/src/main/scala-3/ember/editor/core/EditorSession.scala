package ember.editor.core

import scala.collection.mutable

/** Ein Listener hat beim Verarbeiten eines Commits geworfen. */
final case class ListenerFailed(listener: String, cause: String) extends EditorError:
  def message: String = s"Listener `$listener` ist gescheitert: $cause"

/** Was eine Sitzung mitbekommt.
  *
  * P05 baut diese Konfiguration aus Extensions zusammen; bis dahin wird sie direkt uebergeben.
  *
  * @param mappingRetention
  *   wie viele Commit-Abbildungen aufgehoben werden. Begrenzt, weil §11 die Retention ausdruecklich
  *   beschraenkt: ein Bookmark, dessen Abbildungen herausgefallen sind, laeuft ab -- und das ist
  *   richtig so, denn die Alternative waere eine unbegrenzt wachsende Kette oder eine geratene
  *   Position.
  * @param errorSink
  *   nimmt Fehler entgegen, die niemand zurueckgeben kann -- vor allem geworfene Listener.
  *   Voreinstellung ist ein Nichtstuer; eine Anwendung sollte hier protokollieren.
  */
final case class SessionConfig(
    fields: Vector[StateField[?]] = Vector.empty,
    preCommitRules: Vector[PreCommitRule] = Vector.empty,
    selectionSupport: SelectionSupport = SelectionSupport.core,
    mappingRetention: Int = 64,
    errorSink: EditorError => Unit = _ => ()
)

/** Besitzt den aktuellen Zustand und die einzige Commit-Grenze.
  *
  * ==Die Reihenfolge eines Commits==
  *
  * Nach §10, Schritt fuer Schritt:
  *
  *   1. Metadaten erfassen, Operationen im privaten Entwurf sammeln.
  *   1. Jede Operation fuehrt Dokument, Elternindex, ChangeSet und Abbildung gemeinsam nach.
  *   1. Auswahl entlang der Operationen mitfuehren.
  *   1. ''Transforms -- folgen in P05.''
  *   1. [[DocumentChangePolicy]] anwenden, dann [[PreCommitRule]]n, dann Feld-Reducer. Jede
  *      Ablehnung verwirft die '''ganze''' Transaktion.
  *   1. Unveraenderlichen Zustand atomar veroeffentlichen. Ein No-op veroeffentlicht nichts.
  *   1. Konsumenten benachrichtigen, danach die Warteschlange abarbeiten.
  *
  * ==Warum keine Reentranz==
  *
  * Ein `update` innerhalb eines `update` waere eine verdeckte Schachtelung: der innere Commit
  * wuerde auf einem Zustand aufsetzen, den der aeussere gleich ueberschreibt. §10 schliesst das
  * aus. Damit der Aenderungsbedarf trotzdem nicht verloren geht, gibt es [[enqueueUpdate]] -- die
  * Anforderung landet in einer FIFO-Warteschlange und laeuft, wenn die aktuelle
  * Benachrichtigungsphase durch ist.
  *
  * ==Commit ist nicht gerendert==
  *
  * Der Kern veroeffentlicht einen Zustand; ob eine View ihn schon zeigt, ist eine andere Frage und
  * ein anderer Zeitpunkt (§5). P09 haengt die Projektion in dieselbe Phase, in der hier die
  * Warteschlange abgearbeitet wird.
  *
  * Nicht threadsicher, und das ist Absicht: Scala.js ist einspurig, und eine Sperre wuerde
  * Sicherheit vortaeuschen, die es hier nicht braucht.
  */
final class EditorSession private (initial: EditorState, config: SessionConfig):

  private var currentState = initial
  private var inUpdate     = false
  private var disposedFlag = false
  private var nextHandle   = 0L

  private var listeners  = Vector.empty[(Long, Commit => Unit)]
  private var errorSinks = Vector.empty[(Long, EditorError => Unit)]
  private var mappings   = Vector.empty[RevisionMapping]

  private val pending = mutable.Queue.empty[Transaction => Unit]

  /** Der aktuelle Zustand. Ausserhalb jeder Closure lesbar und danach unveraenderlich. */
  def state: EditorState = currentState

  def document: Document = currentState.document

  def selection: Option[Selection] = currentState.selection

  def isDisposed: Boolean = disposedFlag

  // -----------------------------------------------------------------------------------------
  // Aendern
  // -----------------------------------------------------------------------------------------

  def update(body: Transaction => Unit): Either[UpdateError, Commit] =
    update(TransactionMeta.user)(body)

  def update(meta: TransactionMeta)(body: Transaction => Unit): Either[UpdateError, Commit] =
    if disposedFlag then Left(UpdateError.SessionDisposed)
    else if inUpdate then Left(UpdateError.NestedUpdate)
    else
      val outcome = runOnce(meta, body)
      drain()
      outcome

  /** Fordert eine Aenderung fuer einen '''folgenden''' Commit an.
    *
    * Der Weg aus einem Listener heraus. Ausserhalb einer laufenden Transaktion laeuft die
    * Anforderung sofort -- alles andere waere eine Ueberraschung.
    */
  def enqueueUpdate(body: Transaction => Unit): Unit =
    if disposedFlag then report(UpdateError.SessionDisposed)
    else
      pending.enqueue(body)
      if !inUpdate then drain()

  // -----------------------------------------------------------------------------------------
  // Beobachten
  // -----------------------------------------------------------------------------------------

  /** Wird nach jedem veroeffentlichten Commit gerufen, No-ops ausgenommen. */
  def onCommit(listener: Commit => Unit): Subscription =
    if disposedFlag then Subscription.cancelled
    else
      val handle = takeHandle()
      listeners = listeners :+ (handle, listener)
      Subscription(() => listeners = listeners.filterNot(_._1 == handle))

  /** Nimmt Fehler entgegen, die kein Aufrufer zurueckbekommen kann. */
  def onError(sink: EditorError => Unit): Subscription =
    if disposedFlag then Subscription.cancelled
    else
      val handle = takeHandle()
      errorSinks = errorSinks :+ (handle, sink)
      Subscription(() => errorSinks = errorSinks.filterNot(_._1 == handle))

  /** Die Abbildung von `revision` bis zum aktuellen Stand, fuer [[Bookmark.resolve]].
    *
    * Laeuft ab, sobald die noetigen Abbildungen aus der Retention gefallen sind -- ein
    * ausdruecklicher Fehler statt einer falschen Position (§11).
    */
  def mappingSince(revision: Revision): Either[ExpiredBookmark, RevisionMapping] =
    if revision == currentState.revision then Right(RevisionMapping.identity(revision))
    else
      mappings.indexWhere(_.from == revision) match
        case -1 =>
          Left(
            ExpiredBookmark(
              s"Keine Abbildung ab Revision ${revision.value} mehr vorhanden " +
                s"(Retention: ${config.mappingRetention}).",
              DiagnosticPath.Root
            )
          )
        case index => RevisionMapping.composeAll(mappings.drop(index))

  /** Beendet die Sitzung. Idempotent.
    *
    * Verwirft Listener und die Warteschlange. Der zuletzt veroeffentlichte Zustand bleibt lesbar --
    * er ist ein unveraenderlicher Wert und wird oft noch zum Aufraeumen gebraucht.
    */
  def dispose(): Unit =
    if !disposedFlag then
      disposedFlag = true
      listeners = Vector.empty
      errorSinks = Vector.empty
      mappings = Vector.empty
      pending.clear()

  // -----------------------------------------------------------------------------------------
  // Commit
  // -----------------------------------------------------------------------------------------

  private def runOnce(
      meta: TransactionMeta,
      body: Transaction => Unit
  ): Either[UpdateError, Commit] =
    inUpdate = true
    val transaction = new Transaction(
      currentState.document,
      currentState.selection,
      currentState.fields,
      meta,
      config.selectionSupport
    )

    try
      // Ein geworfener Body ist ein Programmierfehler und wird nicht in ein `Left` verwandelt.
      // Der Sitzungszustand bleibt trotzdem unberuehrt: veroeffentlicht wird erst ganz unten.
      try body(transaction)
      finally transaction.close()

      transaction.failure match
        case Some(error) => Left(error)
        case None        => finish(transaction.candidate(currentState), transaction.assignedFields)
    finally inUpdate = false

  private def finish(
      raw: CommitCandidate,
      assigned: Set[StateField[?]]
  ): Either[UpdateError, Commit] =
    val candidate = raw.copy(fields = applyPolicies(raw, assigned))

    checkRules(candidate) match
      case Some(error) => Left(error)
      case None        =>
        reduceFields(candidate) match
          case Left(error)   => Left(error)
          case Right(fields) => Right(publish(candidate, fields))

  /** §9: Felder erklaeren ausdruecklich, was bei einer Dokumentaenderung mit ihnen geschieht.
    *
    * Ausdruecklich zugewiesene Felder sind ausgenommen. Sonst koennte eine Transaktion, die das
    * Dokument aendert, den zugehoerigen Folgewert nie setzen -- und das ist der haeufigste Fall
    * ueberhaupt. Zurueckgesetzt wird also nur, was diese Transaktion nicht selbst bestimmt hat.
    */
  private def applyPolicies(
      candidate: CommitCandidate,
      assigned: Set[StateField[?]]
  ): StateFields =
    if !candidate.documentChanged then candidate.fields
    else
      config.fields.foldLeft(candidate.fields) { (fields, field) =>
        field.onDocumentChange match
          case DocumentChangePolicy.Keep                     => fields
          case DocumentChangePolicy.Reset if assigned(field) => fields
          case DocumentChangePolicy.Reset                    => resetTo(fields, field)
      }

  private def resetTo[A](fields: StateFields, field: StateField[A]): StateFields =
    fields.updated(field, field.initial)

  private def checkRules(candidate: CommitCandidate): Option[UpdateError] =
    config.preCommitRules.iterator
      .map(rule => rule.check(candidate).map(UpdateError.RuleRejected(rule.name, _)))
      .collectFirst { case Some(error) => error }

  private def reduceFields(candidate: CommitCandidate): Either[UpdateError, StateFields] =
    config.fields.foldLeft[Either[UpdateError, StateFields]](Right(candidate.fields)) {
      (accumulated, field) => accumulated.flatMap(reduceOne(_, field, candidate))
    }

  private def reduceOne[A](
      fields: StateFields,
      field: StateField[A],
      candidate: CommitCandidate
  ): Either[UpdateError, StateFields] =
    field.reduce(fields(field), candidate) match
      case Left(error)  => Left(UpdateError.FieldRejected(field.name, error))
      case Right(value) => Right(fields.updated(field, value))

  private def publish(candidate: CommitCandidate, fields: StateFields): Commit =
    val previous        = candidate.previous
    val documentChanged = candidate.documentChanged
    val anythingChanged =
      documentChanged || candidate.selection != previous.selection || fields != previous.fields

    // §10, Schritt 6: Ein No-op erzeugt keinen Dokumentcommit -- und keine Benachrichtigung.
    // Der Aufrufer bekommt trotzdem ein Ergebnis und kann es an `isNoOp` erkennen.
    if !anythingChanged then
      Commit(previous, previous, ChangeSet.empty, PositionMapping.identity, candidate.meta)
    else
      val next = EditorState(
        document = candidate.document,
        selection = candidate.selection,
        revision = previous.revision.next,
        // Nur eine echte Dokumentaenderung bewegt diese Zahl. Wer speichert, vergleicht sie --
        // ein bewegter Cursor loest dann keinen Schreibvorgang aus (§9).
        documentRevision =
          if documentChanged then previous.documentRevision.next else previous.documentRevision,
        fields = fields
      )

      currentState = next
      val commit = Commit(previous, next, candidate.changes, candidate.mapping, candidate.meta)
      remember(commit.revisionMapping)
      notifyListeners(commit)
      commit

  private def remember(mapping: RevisionMapping): Unit =
    mappings = (mappings :+ mapping).takeRight(config.mappingRetention)

  /** Ein gescheiterter Listener darf die uebrigen nicht mitreissen (§10, Schritt 7).
    *
    * Die Liste ist ein Schnappschuss: wer sich waehrend der Benachrichtigung an- oder abmeldet,
    * aendert erst den naechsten Durchlauf. Ein `dispose` aus einem Listener heraus bricht die Runde
    * dagegen ab -- weitere Konsumenten einer entsorgten Sitzung zu bedienen waere schlechter als
    * sie zu uebergehen.
    */
  private def notifyListeners(commit: Commit): Unit =
    listeners.foreach { (_, listener) =>
      if !disposedFlag then
        try listener(commit)
        catch
          case error: Throwable =>
            report(
              ListenerFailed(listener.getClass.getSimpleName, String.valueOf(error.getMessage))
            )
    }

  /** Arbeitet die Warteschlange ab. Waehrend des Abarbeitens Angefordertes landet hinten. */
  private def drain(): Unit =
    while pending.nonEmpty && !disposedFlag do
      val body = pending.dequeue()
      runOnce(TransactionMeta.user, body).left.foreach(report)

  private def report(error: EditorError): Unit =
    config.errorSink(error)
    errorSinks.foreach((_, sink) => sink(error))

  private def takeHandle(): Long =
    nextHandle += 1
    nextHandle

object EditorSession:

  /** Startet eine Sitzung auf einem bereits gueltigen Dokument. */
  def create(document: Document, config: SessionConfig = SessionConfig()): EditorSession =
    new EditorSession(EditorState.initial(document, config.fields), config)

  /** Startet eine Sitzung mit vorgegebener Anfangsauswahl.
    *
    * Wirft, wenn die Auswahl im Dokument nicht darstellbar ist -- ein im Code angegebener
    * Anfangswert ist entweder gueltig oder ein Programmierfehler.
    */
  def create(
      document: Document,
      selection: Selection,
      config: SessionConfig
  ): EditorSession =
    val violations = config.selectionSupport.validate(selection, document)
    if violations.nonEmpty then
      throw EditorContractViolation(
        violations.map(_.render).mkString("Ungueltige Anfangsauswahl:\n", "\n", "")
      )
    new EditorSession(
      EditorState.initial(document, config.fields).copy(selection = Some(selection)),
      config
    )
