package ember.editor.core

/** Der private Entwurf einer laufenden Aenderung.
  *
  * ==Lebensdauer==
  *
  * Ein Handle gilt genau fuer die Dauer seiner `update`-Closure und wird danach ungueltig (§10). Es
  * in einem `Future` aufzuheben und spaeter zu benutzen ist ein Programmierfehler, kein Datenfehler
  * -- deshalb wirft jeder Zugriff danach eine [[EditorContractViolation]] und liefert kein `Left`.
  * Asynchrone Arbeit findet ausserhalb statt und beginnt bei ihrem Ergebnis eine '''neue'''
  * Transaktion.
  *
  * ==Warum die Fehler einrasten==
  *
  * Die Closure liefert `Unit`, ihr Rueckgabewert kann also nichts melden. Ein Aufrufer, der das
  * `Either` einer Operation ignoriert -- was der uebliche Stil ist --, wuerde sonst auf einem
  * kaputten Entwurf weiterarbeiten. Stattdessen merkt sich die Transaktion den ersten Fehlschlag,
  * weist alles Weitere ab und laesst `update` mit genau diesem Fehler scheitern. Es gibt keinen
  * halb angewandten Zustand: der Entwurf ist eine Kette unveraenderlicher Dokumente, und der
  * Sitzungszustand wurde nie angefasst.
  *
  * ==Selection wandert mit==
  *
  * Jede Operation bildet die aktuelle Auswahl mit ab (§10, Schritt 3). Eine ausdruecklich gesetzte
  * Auswahl wird gegen den Entwurf geprueft, in dem sie gesetzt wurde, und danach von den folgenden
  * Operationen weitergefuehrt -- genau wie eine vorgefundene.
  */
final class Transaction private[core] (
    initialDocument: Document,
    initialSelection: Option[Selection],
    initialFields: StateFields,
    val meta: TransactionMeta,
    private val selectionSupport: SelectionSupport,
    private val commands: CommandRegistry = CommandRegistry.empty,
    private val strictCommands: Boolean = true
):

  private var currentDocument  = initialDocument
  private var currentSelection = initialSelection
  private var currentFields    = initialFields
  private var changes          = ChangeSet.empty
  private var mapping          = PositionMapping.identity
  private var assigned         = Set.empty[StateField[?]]
  private var latched          = Option.empty[UpdateError]
  private var alive            = true

  /** Der Entwurf, wie er nach den bisherigen Operationen aussieht. */
  def document: Document =
    requireAlive()
    currentDocument

  /** Die Auswahl, wie sie nach den bisherigen Operationen dasteht. */
  def selection: Option[Selection] =
    requireAlive()
    currentSelection

  def field[A](stateField: StateField[A]): A =
    requireAlive()
    currentFields(stateField)

  /** Setzt ein Feld.
    *
    * Eine ausdrueckliche Zuweisung schlaegt [[DocumentChangePolicy.Reset]]: sonst koennte eine
    * Transaktion, die das Dokument aendert, den zugehoerigen Folgewert nie setzen -- und genau das
    * ist der haeufigste Fall. Der Reducer des Feldes laeuft trotzdem noch.
    */
  def setField[A](stateField: StateField[A], value: A): Unit =
    requireAlive()
    if latched.isEmpty then
      currentFields = currentFields.updated(stateField, value)
      assigned = assigned + stateField

  /** Ob bereits ein Fehler eingerastet ist. */
  def hasFailed: Boolean =
    requireAlive()
    latched.isDefined

  // -----------------------------------------------------------------------------------------
  // Primitive
  // -----------------------------------------------------------------------------------------

  def apply(operation: Operation): Either[UpdateError, Unit] =
    requireAlive()
    latched match
      case Some(previous) => Left(previous)
      case None           =>
        currentDocument.applyOperation(operation) match
          case Left(error)   => fail(UpdateError.OperationFailed(error))
          case Right(result) =>
            currentDocument = result.document
            changes = changes andThen result.changes
            mapping = mapping andThen result.mapping
            currentSelection =
              currentSelection.flatMap(selectionSupport.map(_, result.mapping, result.document))
            Right(())

  def insert(
      parent: NodeId,
      index: Int,
      node: EditorNode,
      descendants: Vector[EditorNode] = Vector.empty
  ): Either[UpdateError, Unit] =
    apply(Operation.Insert(parent, index, node, descendants))

  def remove(nodeId: NodeId): Either[UpdateError, Unit] = apply(Operation.Remove(nodeId))

  def move(nodeId: NodeId, newParent: NodeId, index: Int): Either[UpdateError, Unit] =
    apply(Operation.Move(nodeId, newParent, index))

  def replace(nodeId: NodeId, replacement: EditorNode): Either[UpdateError, Unit] =
    apply(Operation.Replace(nodeId, replacement))

  def spliceText(
      nodeId: NodeId,
      start: Int,
      deleteCount: Int,
      inserted: String
  ): Either[UpdateError, Unit] =
    apply(Operation.SpliceText(nodeId, start, deleteCount, inserted))

  def splitText(nodeId: NodeId, at: Int, newId: NodeId): Either[UpdateError, Unit] =
    apply(Operation.SplitText(nodeId, at, newId))

  def mergeText(left: NodeId, right: NodeId): Either[UpdateError, Unit] =
    apply(Operation.MergeText(left, right))

  /** Setzt Dokument und Auswahl auf einen frueheren Stand.
    *
    * ==Warum das kein Primitiv unter den anderen ist==
    *
    * Es ist keine Operation. [[Operation]] beschreibt, was jemand '''tut'''; dies beschreibt,
    * wohin ein Stand zurueckgesetzt wird. Der Unterschied ist nicht akademisch: eine Operation
    * kennt ihre eigene Wirkung und liefert ChangeSet und Abbildung selbst, hier dagegen gibt es
    * nur zwei Staende, und der Unterschied muss ausgerechnet werden ([[DocumentDiff]]).
    *
    * ==Wofuer es da ist, und wofuer nicht==
    *
    * Fuer Undo und Redo (§14: "strukturell geteilte Document-Snapshots") und fuer alles, was
    * einen ganzen Stand ersetzt statt ihn zu bearbeiten -- ein verworfener Entwurf, ein
    * neu geladenes Dokument. '''Nicht''' als bequemer Ersatz fuer eine Bearbeitung: wer den
    * naechsten Stand aus dem aktuellen ausrechnet und ihn hier hineinreicht, verliert genau die
    * Information, um die es §10 geht, und laesst die Projektion einen Diff nachholen, den die
    * Operation frei mitgeliefert haette.
    *
    * ==Was geprueft wird==
    *
    * Dass das Dokument zum Schema der Sitzung gehoert. Mehr braucht es nicht: ein [[Document]]
    * hat die Invarianten aus §8.2 bereits erfuellt -- wer einen Wert dieses Typs in der Hand
    * hat, haelt ein gueltiges Dokument (§8.2). Die Auswahl wird wie bei [[setSelection]] gegen
    * den '''wiederhergestellten''' Stand geprueft, nicht gegen den, aus dem sie stammt.
    */
  def restore(document: Document, selection: Option[Selection]): Either[UpdateError, Unit] =
    requireAlive()
    latched match
      case Some(previous) => Left(previous)
      case None if !(document.schema eq currentDocument.schema) =>
        fail(UpdateError.ForeignSchema)
      case None =>
        val (restored, positions) = DocumentDiff.between(currentDocument, document)
        currentDocument = document
        changes = changes andThen restored
        mapping = mapping andThen positions
        // Erst danach: die Auswahl gehoert zum wiederhergestellten Stand und wird gegen ihn
        // geprueft.
        setSelection(selection)

  /** Setzt die Auswahl, geprueft gegen den aktuellen Entwurf. */
  def setSelection(selection: Option[Selection]): Either[UpdateError, Unit] =
    requireAlive()
    latched match
      case Some(previous) => Left(previous)
      case None           =>
        selection match
          case None =>
            currentSelection = None
            Right(())
          case Some(chosen) =>
            val violations = selectionSupport.validate(chosen, currentDocument)
            if violations.nonEmpty then fail(UpdateError.InvalidSelection(violations))
            else
              currentSelection = Some(chosen)
              Right(())

  def select(selection: Selection): Either[UpdateError, Unit] = setSelection(Some(selection))

  // -----------------------------------------------------------------------------------------
  // Commands
  // -----------------------------------------------------------------------------------------

  /** Fuehrt eine Absicht im '''laufenden''' Entwurf aus (§12).
    *
    * Handler laufen von Critical bis Fallback, bei gleicher Prioritaet in
    * Registrierungsreihenfolge. Das erste `Handled` beendet die Kette.
    *
    * Ist bereits ein Fehler eingerastet, laeuft kein Handler mehr -- ein Handler auf einem kaputten
    * Entwurf koennte nur weiteren Schaden anrichten.
    */
  def dispatch[A](command: EditorCommand[A], payload: A): CommandResult =
    requireAlive()
    if latched.isDefined then CommandResult.Pass
    else
      val scope    = new TransformScope(this)
      val handlers = commands.handlersFor(command)
      var result   = CommandResult.Pass
      var index    = 0

      while result == CommandResult.Pass && index < handlers.length && latched.isEmpty do
        val before = fingerprint
        result = handlers(index)(scope, payload)
        if result == CommandResult.Pass then checkPassIsClean(command, before)
        index += 1

      result

  def dispatch(command: EditorCommand[Unit]): CommandResult = dispatch(command, ())

  /** §12: `Pass` muss nebenwirkungsfrei sein.
    *
    * Ein Handler, der den Entwurf aendert und dann `Pass` meldet, hinterlaesst einen Zustand, mit
    * dem der naechste Handler nicht rechnet -- und der Fehler zeigt sich erst weit entfernt. Das
    * ist eine Vertragsverletzung des Handlers, also nach der Fehlerkonvention eine Exception und
    * kein `Left`. Wer die Pruefung in Produktion nicht will, schaltet
    * `SessionConfig.strictCommands` ab; dann gilt der Vertrag unveraendert, wird aber nicht mehr
    * ueberwacht.
    */
  private def checkPassIsClean(command: EditorCommand[?], before: Fingerprint): Unit =
    if strictCommands && before != fingerprint then
      throw EditorContractViolation(
        s"Der Handler fuer `${command.name}` hat den Entwurf veraendert und trotzdem Pass gemeldet."
      )

  private type Fingerprint = (Document, Option[Selection], StateFields)

  private def fingerprint: Fingerprint = (currentDocument, currentSelection, currentFields)

  // -----------------------------------------------------------------------------------------
  // Intern
  // -----------------------------------------------------------------------------------------

  private def fail(error: UpdateError): Either[UpdateError, Unit] =
    latched = Some(error)
    Left(error)

  private def requireAlive(): Unit =
    if !alive then
      throw EditorContractViolation(
        "Diese Transaktion ist abgeschlossen. Ein Tx-Handle gilt nur innerhalb seiner update-Closure; " +
          "asynchrone Arbeit beginnt bei ihrem Ergebnis eine neue Transaktion."
      )

  private[core] def close(): Unit = alive = false

  private[core] def failure: Option[UpdateError] = latched

  /** Felder, die diese Transaktion ausdruecklich gesetzt hat. */
  private[core] def assignedFields: Set[StateField[?]] = assigned

  /** Der bisher aufgelaufene ChangeSet. Die Transform-Schleife liest daraus ihre Dirty-Menge. */
  private[core] def currentChanges: ChangeSet = changes

  private[core] def candidate(previous: EditorState): CommitCandidate =
    CommitCandidate(
      previous = previous,
      document = currentDocument,
      selection = currentSelection,
      changes = changes,
      mapping = mapping,
      fields = currentFields,
      meta = meta
    )
