package ember.editor.core

/** Eine Benutzer- oder Anwendungsabsicht.
  *
  * ==Identitaet ist das Objekt, nicht sein Name==
  *
  * Ein Command wird ueber Referenzgleichheit nachgeschlagen, nie ueber einen String (§12). Damit
  * gibt es keine Namenskollisionen zwischen Modulen, keine Tippfehler, die erst zur Laufzeit
  * auffallen, und der Compiler prueft den Payload-Typ. [[name]] steht ausschliesslich in
  * Diagnosemeldungen -- er ist ausdruecklich '''keine''' Dispatch-API (§23).
  *
  * ==Nicht jedes Primitiv wird ein Command==
  *
  * §12: Commands sind Absichten, keine Operationen. `Bold`, `Undo`, `InsertParagraph` sind
  * Absichten; `SpliceText` ist es nicht. Wer alles zu einem Command macht, bekommt eine Registry
  * statt einer API.
  *
  * @tparam A
  *   der Payload-Typ. `Unit` fuer Commands ohne Argument.
  */
trait EditorCommand[A]:

  /** Nur fuer Diagnose. */
  def name: String

object EditorCommand:

  /** Ein Command ohne Payload. */
  def unit(commandName: String): EditorCommand[Unit] =
    new EditorCommand[Unit]:
      val name: String = commandName

  /** Ein Command mit Payload. */
  def of[A](commandName: String): EditorCommand[A] =
    new EditorCommand[A]:
      val name: String = commandName

/** Ob ein Handler die Absicht uebernommen hat.
  *
  * Ausdruecklich nicht dasselbe wie `preventDefault`/`stopPropagation` (§12): das sind
  * Browserereignis-Operationen, ueber die allein der Browseradapter entscheidet -- und zwar danach,
  * ob er die native Aktion tatsaechlich ersetzt oder bewusst abgelehnt hat.
  */
enum CommandResult:

  /** Nicht zustaendig. Der naechste Handler kommt dran.
    *
    * '''Muss nebenwirkungsfrei sein.''' Ein Handler, der den Entwurf aendert und dann `Pass`
    * meldet, hinterlaesst einen Zustand, mit dem der naechste Handler nicht rechnet. Bei
    * `SessionConfig.strictCommands` wird das erkannt und gemeldet.
    */
  case Pass

  /** Uebernommen. Die Kette endet hier. */
  case Handled

/** Benannte Prioritaetsstufen.
  *
  * Fuenf Stufen statt freier Zahlen: eine Zahl laedt dazu ein, sich mit `priority + 1` vor einen
  * anderen zu draengen, und dann entscheidet nicht mehr die Absicht, sondern wer zuletzt
  * geschrieben hat. Innerhalb einer Stufe gilt stabile Registrierungsreihenfolge.
  */
enum CommandPriority:
  case Critical, High, Normal, Low, Fallback

/** Ein Command samt Prioritaet und Handler, wie eine Extension ihn beitraegt. */
final case class CommandRegistration[A](
    command: EditorCommand[A],
    priority: CommandPriority,
    handler: (TransformScope, A) => CommandResult
)

object CommandRegistration:

  /** Bequemer Konstruktor mit Normalprioritaet. */
  def apply[A](command: EditorCommand[A])(
      handler: (TransformScope, A) => CommandResult
  ): CommandRegistration[A] =
    CommandRegistration(command, CommandPriority.Normal, handler)

/** Was ein Dispatch ausserhalb einer Transaktion ergeben hat.
  *
  * Beides wird gebraucht und ist nicht dasselbe: ein Handler kann uebernehmen, ohne etwas zu
  * aendern (`Handled` mit `commit.isNoOp`), und ein Dispatch kann etwas aendern, ohne dass
  * irgendjemand uebernommen hat -- dann naemlich, wenn gar kein Handler registriert war und die
  * Transaktion leer blieb.
  */
final case class DispatchOutcome(result: CommandResult, commit: Commit):
  def wasHandled: Boolean = result == CommandResult.Handled
